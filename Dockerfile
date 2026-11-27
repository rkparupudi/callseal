# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 callseal
COPY --from=build /src/target/callseal-*.jar app.jar
USER callseal
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
