# ---- Build stage: compile and package with Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first for faster rebuilds.
COPY pom.xml .
RUN mvn -B -e dependency:go-offline || true
# Then build the app.
COPY src ./src
RUN mvn -B -e clean package -DskipTests

# ---- Run stage: slim JRE 21 image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Respect the platform PORT if provided (Render sets it); default to 8080.
ENV SERVER_PORT=8080
ENTRYPOINT ["sh","-c","java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
