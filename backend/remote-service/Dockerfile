FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/common backend/common
COPY backend/remote-service backend/remote-service

RUN mvn -f backend/pom.xml -pl remote-service -am dependency:go-offline -q 2>&1 | grep -v "^\[" || true

COPY backend backend
RUN mvn -f backend/pom.xml -pl remote-service -am -DskipTests=true package -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/remote-service/target/*.jar /app/app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
