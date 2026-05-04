FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace
COPY . .
RUN chmod +x ./mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN groupadd --system scaffy && useradd --system --gid scaffy scaffy
COPY --from=build /workspace/target/scaffy-be-0.0.1-SNAPSHOT.jar /app/scaffy-be.jar
USER scaffy
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/scaffy-be.jar"]
