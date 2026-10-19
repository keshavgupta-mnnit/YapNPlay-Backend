# YapNPlay backend image (Render and any Docker host).
# Stage 1 builds one runnable JAR with the Gradle wrapper; stage 2 runs it
# on a small Java 21 runtime. Secrets never go in here: the host passes
# them as environment variables (DB_URL, LIVEKIT_*, ADMIN_TOKEN, …).

# Gradle itself runs on Java 25 (gradle/gradle-daemon-jvm.properties); the
# code is compiled for Java 21, so the runtime stage below stays on 21.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
# Files committed from Windows may have CRLF line endings.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# The laptop's Java path in gradle.properties does not exist here: clear it.
RUN ./gradlew buildFatJar --no-daemon --console=plain -Porg.gradle.java.installations.paths=

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*-all.jar app.jar
# Free hosts give ~512 MB: keep the heap inside the container's memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
