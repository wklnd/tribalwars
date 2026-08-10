# Multi-stage build: compile with the Gradle wrapper, run the plain jar with `java -jar` (not `bootRun` -
# bootRun's dev JVM flags disable the C2 JIT and measurably slow the NPC tick, see CLAUDE.md "Performance").
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# The H2 file DB lives on a mounted volume (see docker-compose.yml) so it survives redeploys - never bake
# backend/data into the image (see .dockerignore), and a redeploy must reuse the same volume, not start fresh.
ENV SPRING_DATASOURCE_URL="jdbc:h2:file:/data/twlan2;AUTO_SERVER=TRUE"
# No server.address override here (unlike the local `twlan` script's --server.address=127.0.0.1): inside the
# container this must bind every interface so the frontend/nginx container can reach it over the Docker
# network. It is still never published to the host - only the frontend service is (see docker-compose.yml).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
