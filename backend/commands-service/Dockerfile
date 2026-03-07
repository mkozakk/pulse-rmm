FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/common backend/common
COPY backend/commands-service backend/commands-service

RUN mvn -f backend/pom.xml -pl commands-service -am dependency:go-offline -q 2>&1 | grep -v "^\[" || true

COPY backend backend
RUN mvn -f backend/pom.xml -pl commands-service -am -DskipTests=true package -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/commands-service/target/*.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
