# ---------- build stage: compile and package the Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

# ---------- runtime stage: small JRE image, non-root user ----------
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER app
EXPOSE 8083
# Container-aware memory settings (Render free instances have 512 MB)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -Xss512k"
# On Render (RENDER=true) the "render" Spring profile is activated automatically.
ENTRYPOINT ["sh", "-c", "if [ \"$RENDER\" = \"true\" ] && [ -z \"$SPRING_PROFILES_ACTIVE\" ]; then export SPRING_PROFILES_ACTIVE=render; fi; exec java $JAVA_OPTS -jar /app/app.jar"]
