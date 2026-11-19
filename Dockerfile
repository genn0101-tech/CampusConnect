# Render cannot run Java directly, so the app runs inside this Docker image.

# ---- Stage 1: build the .jar with Java 17 + the Maven wrapper ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Windows may lose the "executable" flag, so set it again
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
# Tests already run on the developer's PC before pushing
RUN ./mvnw -q package -DskipTests

# ---- Stage 2: a smaller image that only runs the .jar (no build tools) ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/campus-connect-0.0.1-SNAPSHOT.jar app.jar
# Use production settings (PostgreSQL, no sample data, secrets required)
ENV SPRING_PROFILES_ACTIVE=prod
# Let Java use up to 75% of the container's memory (free plans have little memory)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
