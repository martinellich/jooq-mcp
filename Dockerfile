FROM azul/zulu-openjdk-alpine:21.0.1

VOLUME /tmp

COPY target/*.jar app.jar

# The JVM default caps the heap at 25% of the machine, which is 245 MB of the 1 GB Fly
# machine while the rest sits unused. On an OOM, dump into the mounted /tmp volume so the
# heap survives the restart and the next incident can be diagnosed from evidence.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=70.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp", \
    "-jar", "app.jar"]
