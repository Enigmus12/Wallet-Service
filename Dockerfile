# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache deps
COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline

# Build
COPY src ./src
RUN mvn -B -q -DskipTests package


FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Copy the fat jar
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
