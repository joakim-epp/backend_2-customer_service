# 1. Build the React bundle
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar with the bundle baked into it
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

# 3. Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
