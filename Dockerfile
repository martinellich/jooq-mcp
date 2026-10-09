FROM azul/zulu-openjdk-alpine:21.0.1

VOLUME /tmp

COPY target/*.jar app.jar

# The JVM default caps the heap at 25% of the machine, which is 245 MB of the 1 GB Fly
# machine while the rest sits unused. On an OOM, dump into the mounted /tmp volume so the
# heap survives the restart and the next incident can be diagnosed from evidence.
#
# Heap is not the only memory: the Pinecone client runs gRPC on Netty, whose direct
# memory defaults to the size of the heap. Heap + direct + metaspace must stay below the
# 1 GB machine, otherwise the kernel kills the process and no heap dump is written.
# GC log and crash log also go to /tmp so they survive a restart, unlike Fly's logs.
# ExitOnOutOfMemoryError stops a half-dead JVM so Fly restarts it after the dump.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=60.0", \
    "-XX:MaxDirectMemorySize=128m", \
    "-XX:MaxMetaspaceSize=192m", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:ErrorFile=/tmp/hs_err_pid%p.log", \
    "-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=10m", \
    "-jar", "app.jar"]
