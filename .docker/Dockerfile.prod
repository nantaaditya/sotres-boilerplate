FROM bellsoft/liberica-runtime-container:jdk-all-25-musl AS jre-build

WORKDIR /build

# Copy dependencies first for jdeps analysis
COPY target/dependency/*.jar lib/
COPY target/*.jar app.jar

# Dynamically extract necessary modules instead of using ALL-MODULE-PATH
RUN MODULES=$(jdeps --ignore-missing-deps -q --recursive --multi-release 25 --print-module-deps --class-path 'lib/*' app.jar) && \
    echo "${MODULES},jdk.crypto.ec,jdk.charsets,java.naming,jdk.unsupported" > jdeps.info

# Build a truly minimized custom runtime
RUN jlink \
    --add-modules $(cat jdeps.info | tr -d '\n') \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

RUN strip -p --strip-unneeded /javaruntime/lib/server/libjvm.so && \
    find /javaruntime -name '*.so' | xargs -i strip -p --strip-unneeded {}

# ---------------------------------------------------------
# FINAL RUNTIME IMAGE
# ---------------------------------------------------------
FROM bellsoft/alpaquita-linux-base:latest

MAINTAINER NantaAditya
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

ENV TZ=Asia/Jakarta

# Let Kubernetes dictate the physical limits, let Java take 85% of it
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=60.0 -XX:MaxRAMPercentage=85.0 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=60 -XX:InitiatingHeapOccupancyPercent=40 -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+UseContainerSupport -XX:+UseG1GC -XX:+OptimizeStringConcat"

LABEL org.opencontainers.image.authors="nantaaditya"

RUN apk update && apk add --no-cache procps tzdata && \
    mkdir -p app/logs app/lib app/patch app/resources app/resources-patch

WORKDIR /app

# Ordered copying for optimal layer caching
COPY target/dependency/*.jar lib/
COPY src/main/resources resources/
COPY target/*.jar lib/
COPY pom.xml pom.xml

# Using the exec form to ensure SIGTERM reaches the JVM
ENTRYPOINT ["java", "-cp", ".:resources:patch/*:lib/*", "com.nantaaditya.sotres.SoTResApplication"]