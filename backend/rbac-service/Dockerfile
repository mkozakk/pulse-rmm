FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/common backend/common
COPY backend/rbac-service backend/rbac-service

RUN mvn -f backend/pom.xml -pl rbac-service -am dependency:go-offline -q 2>&1 | grep -v "^\[" || true

COPY backend backend
RUN mvn -f backend/pom.xml -pl rbac-service -am -DskipTests=true package -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/rbac-service/target/*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
