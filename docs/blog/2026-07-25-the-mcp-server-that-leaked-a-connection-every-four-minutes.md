# The MCP Server That Leaked a Connection Every Four Minutes

*How one underrated Actuator metric explained a recurring `OutOfMemoryError` in a Spring AI MCP server — and why the
default keep-alive setting is the wrong default.*

---

I run a small public MCP server that exposes the jOOQ manual to AI coding agents. Spring Boot 4.1, Spring AI 2.0, a 1 GB
machine on Fly.io in Frankfurt. It does one thing: take a query, embed it, search Pinecone, hand back documentation
snippets.

It also fell over with an `OutOfMemoryError` every few days.

Nothing about the workload explains that. There is no state. There is no database connection pool. There is no cache.
Every request is embed → query → format string → return. The heap should look like a flat line.

It didn't.

## Don't guess. Measure.

The tempting move here is to start reading code and looking for the leak. I've done that before and it mostly produces
plausible theories that are wrong. A leak that takes days to manifest is a leak you cannot reason your way to — you need
to see the growth.

The app already had Actuator on:

```properties
management.endpoints.web.exposure.include=health,metrics,info
```

So before touching anything, I asked the live instance what it looked like. Standard JVM metrics first:

```
jvm.memory.max   (heap)  = 245 MB
jvm.memory.used  (heap)  = 100 MB
jvm.gc.live.data.size    =  59 MB
jvm.memory.usage.after.gc (old gen) = 0.54
process.uptime           = 91 752 s  (25.5 hours)
```

Two things stood out.

**The heap is tiny.** 245 MB. The Dockerfile is:

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

No `-Xmx`. So the JVM applies its default: `MaxRAMPercentage=25`, i.e. 256 MB of the 1 GB machine, of which ~234 MiB is
actually usable heap (the reported maximum excludes one survivor space). **Three quarters of the machine's memory was
sitting unused** while the JVM strangled itself in the remaining quarter.

**The live set is not flat.** 59 MB of data surviving a full GC, with the old generation already 54 % full — on a server
that, conceptually, holds nothing between requests. Something was accumulating.

But *what*? The JVM metrics tell you the heap is filling up. They don't tell you with what.

## The metric that cracked it

`jvm.buffer.memory.used` came back at 141 KB, which ruled out a direct-memory/Netty problem. Thread count was 23, peak
27 — no thread leak. Class count 14 246, stable — no classloader leak.

Then this:

```
http.server.requests.active
  ACTIVE_TASKS = 399
  MAX          = 91 728 s
```

**399 HTTP requests were in flight simultaneously. The oldest had been running for 91 728 seconds — 24 seconds short of
the process uptime.** It had been open since the server started and had never finished.

Broken down by method, the picture got sharper:

| Method | Active  |
|--------|---------|
| GET    | **399** |
| POST   | 0       |
| DELETE | **0**   |

Average age across all 399: 15.3 hours. They accumulate and they never end. Roughly **16 new ones every hour** — one
leaked connection every four minutes — and not one of them ever released.

`http.server.requests.active` is the metric nobody looks at. Everyone watches `http.server.requests` for latency and
error rates. The *active* gauge is the one that tells you a request went in and never came back out. On any server doing
streaming, SSE, or long-polling, it's the first thing you should chart.

Each of those 399 requests pins a Tomcat connection, its socket read/write buffers, an `Http11Processor`, the response
object, the SSE writer, and an MCP session. Multiply by 399 and grow it forever, against a 234 MiB ceiling, and you get
an OOM every few days. That's the whole bug.

## Why the streams never close

Those GETs are MCP streamable-HTTP listening streams. In the MCP protocol, a client opens `GET /mcp` and holds it as a
Server-Sent Events channel so the server can push messages. Long-lived by design. What is *not* by design is that they
outlive their clients.

The transport is `WebMvcStreamableServerTransportProvider`. Here is the GET handler, condensed:

```java
McpStreamableServerSession.McpStreamableServerSessionStream listeningStream =
        session.listeningStream(sessionTransport);

sseBuilder.

onComplete(() ->{
        logger.

debug("SSE connection completed for session: "+sessionId);
    listeningStream.

close();
});
```

The cleanup hook is right there — it just never fires. Four facts compound:

1. **The stream has no timeout.** Correct for SSE, but it means nothing expires on its own.
2. **The server never writes to an idle stream.** This server pushes nothing spontaneously; it only answers POSTs.
3. **TCP doesn't tell you the peer is gone.** A laptop that sleeps, a NAT that expires, a client process that gets
   killed — none of that reaches the server. The socket looks fine until you try to write to it. And nobody was writing.
4. **Clients don't send `DELETE /mcp`.** The spec allows a client to end its session explicitly. In 25 hours of real
   traffic from real MCP clients, I received exactly zero DELETEs.

So the connection sits there, half of a TCP conversation whose other half stopped existing hours ago, and Spring MVC has
no reason to think otherwise.

It gets slightly worse. In this transport, `sessions.remove(sessionId)` appears in exactly one place — the DELETE
handler:

```java
try{
        session.delete()...

block();
    this.sessions.

remove(sessionId);
    return ServerResponse.

ok().

build();
}
```

Neither `close()` nor the error path in `sendMessage()` removes the session from the map. (The sibling
`HttpServletStreamableServerTransportProvider` in mcp-core has the same line in its `close()` method, but commented out:

```java
// HttpServletStreamableServerTransportProvider.this.sessions.remove(this.sessionId);
this.asyncContext.complete();
```

which reads like a known rough edge rather than an oversight.)

## The fix is one line

Spring AI exposes exactly the setting this needs:

```properties
spring.ai.mcp.server.streamable-http.keep-alive-interval=30s
```

**Its default is `null` — keep-alive off.**

With an interval set, `KeepAliveScheduler` pings every session on a timer:

```java
Flux.interval(initialDelay, interval, scheduler)
    .

doOnNext(tick ->mcpSessions.

get()
        .

flatMap(session ->session.

sendRequest(METHOD_PING, null,OBJECT_TYPE_REF)
            .

doOnError(e ->logger.

warn("Failed to send keep-alive ping ..."))
        .

onErrorComplete())
        .

subscribe())
```

That single write is the entire mechanism. Writing to a dead socket eventually throws, the transport calls
`sseBuilder.error(e)`, the async response completes, `onComplete` fires, `listeningStream.close()` runs — and Tomcat
reclaims the connection, the buffers, the processor, the writer. The chain that was already written finally gets a
reason to execute.

The ping doubles as a liveness probe in the other direction too: `sendRequest` carries a 20-second timeout, so a client
that holds the socket open but has stopped responding also gets cleaned up.

I picked 30 seconds because it's short enough to bound the leak (worst case ~16 dead connections held for half a minute
instead of forever) and long enough to be invisible: a JSON-RPC ping on an idle stream is a few dozen bytes.

## What keep-alive does *not* fix

Being precise about this matters, because "I set a property and the graph got better" is how you end up with a smaller
version of the same bug next quarter.

Keep-alive reclaims the **expensive** part — the HTTP connection and everything Tomcat hangs off it. Verified by reading
`McpStreamableServerSessionStream.close()`: it swaps the session's `listeningStreamRef` back to a placeholder and drops
the stream, so the SSE builder and the entire servlet response graph become unreachable.

What stays is the bare `McpStreamableServerSession` object in the transport's `ConcurrentHashMap`, because only DELETE
removes it and clients don't send DELETE. That object is small — an ID, client info, capabilities, references to shared
handler maps, and some now-empty maps. Message history would make it much bigger, but the SDK still has
`// TODO: store message in history` where that would go, so today it's on the order of a kilobyte.

At ~16 sessions/hour that's roughly **0.4 MB per day** of genuinely unreclaimable heap. Against a properly sized heap
and a service that redeploys every couple of weeks, that's noise. It is still a leak, it is still unbounded, and it's
worth reporting upstream. But it's not what was killing the process.

The honest summary: the fix removed the leak that mattered by three orders of magnitude and left a slow one that a
restart handles.

## The other four things

While I was in there.

**Size the heap explicitly.** Letting a container JVM keep 25 % of the machine is a habit worth breaking:

```dockerfile
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=70.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp", \
    "-jar", "app.jar"]
```

More heap doesn't fix a leak, it just moves the deadline — that's not why it's here. It's here because 234 MiB on a 1 GB
machine was never a deliberate decision, it was a default nobody looked at.

The heap dump flags are the part I actually care about. `/tmp` is a mounted volume, so a dump survives the machine
restart. Next time there's an OOM I get evidence instead of a hypothesis. I had to reconstruct this entire investigation
from live metrics because the previous crashes left nothing behind. Two flags, and that never happens again.

**Stop asking Pinecone for data you throw away.**

```java
QueryResponseWithUnsignedIndices response = pineconeIndex.queryByVector(
        limit,
        queryEmbedding,
        false, // include values - we only read metadata, so skip the 1536 floats per hit
        true   // include metadata
);
```

`includeValues` was `true`. Every hit came back with its full 1536-dimension embedding, deserialized and boxed into a
`List<Float>` at roughly 24 KB each. Hybrid search runs two queries at 15 hits each, so every single search allocated
about 700 KB of vectors that the result mapper never reads. Not the OOM — the GC handled it — but it was pure waste on
the hot path.

**Turn down the logging.**

```properties
logging.level.org.springframework.ai=INFO   # was DEBUG
```

DEBUG on a framework package, in production, permanently. Separately, the `@ControllerAdvice` was logging a full stack
trace at ERROR for every request to `/sse` — an endpoint this server no longer has, and which older MCP clients still
hammer. Each 404 produced ~40 lines of Tomcat internals and got reported as a 500:

```java

@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<String> handleNoResourceFound(NoResourceFoundException e) {
    logger.debug("No handler for request: {}", e.getResourcePath());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Not found. The MCP endpoint of this server is /mcp.");
}
```

An unknown path is a routine event on a public server, not an error.

**Close the metrics endpoint.** Every number in this post I read from the open internet, unauthenticated, with `curl`.
That was extremely convenient for me and it is exactly as convenient for everyone else:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

`health` stays public because external uptime monitoring needs it, and it still returns 503 when the app is unhealthy,
so alerting is unaffected. `show-details=always` had been publishing internal error messages — including OpenAI quota
failures — to anyone who asked.

The diagnostic access I needed is one command away without a redeploy:

```
fly secrets set MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics
```

## Guard the fix

The keep-alive fix has a nasty property: if the key is misspelled, Spring binds nothing, no warning appears, and the
leak comes back silently. A one-line config fix with no test is a one-line config fix waiting to be deleted by someone
tidying up properties.

So it gets a test that reads the real `application.properties` and binds it:

```java
new ApplicationContextRunner()
        .

withUserConfiguration(StreamableHttpPropertiesConfiguration .class)
        .

withPropertyValues(KEEP_ALIVE_PROPERTY +"="+value)
        .

run(context ->{
McpServerStreamableHttpProperties bound =
        context.getBean(McpServerStreamableHttpProperties.class);

assertThat(bound.getKeepAliveInterval())
        .

isNotNull()
                    .

isPositive()
                    .

isLessThanOrEqualTo(Duration.ofMinutes(1));
        });
```

I deleted the property and re-ran it to confirm it actually fails. A guard you haven't watched fail is not a guard.

## Takeaways

**`http.server.requests.active` is the metric you're not watching.** Request *count* and *latency* dashboards are
everywhere. The active-requests gauge is what catches requests that go in and never come out — and if you run SSE,
streamable HTTP, WebSockets, or long-polling, that is your most likely leak. Compare its `MAX` against `process.uptime`:
if they match, something has been open since boot and will be forever.

**A dead TCP connection is invisible until you write to it.** This is the actual lesson under the MCP specifics. Any
protocol where the server holds a connection open and stays quiet needs an application-level heartbeat, because the
transport layer will not tell you. If your framework offers a keep-alive interval, "unset" is not a safe value.

**Defaults you never chose are still decisions.** `MaxRAMPercentage=25`, `keep-alive-interval=null`,
`show-details=always`, `logging.level=DEBUG` — every one of these was a default that shipped to production because
nobody looked at it. Three of the five fixes here were reverting a default, not writing code.

**Instrument before you theorise.** I had three plausible explanations before I ran a single query — the 6 MB
documentation HTML, the gRPC/Netty direct buffers, an unbounded cache. All three were wrong, and I'd have happily spent
a day on any of them. One `curl` against Actuator settled it in about a minute. The metrics endpoint being carelessly
wide open is what made the diagnosis fast; that is not an argument for leaving it open, it's an argument for having a
deliberate way in.

---

*The server is [jooq-mcp](https://github.com/martinellich/jooq-mcp). If you're running an MCP server on Spring AI's
streamable-HTTP transport, go check `spring.ai.mcp.server.streamable-http.keep-alive-interval` right now — and then go
look at your active request count.*
