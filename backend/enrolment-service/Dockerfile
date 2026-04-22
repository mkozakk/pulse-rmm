FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend backend
RUN mvn -f backend/pom.xml -pl enrolment-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/enrolment-service/target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
