FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY . .
RUN chmod +x ./mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system scaffy \
    && useradd --system --gid scaffy scaffy
COPY --from=build /workspace/target/scaffy-be-0.0.1-SNAPSHOT.jar /app/scaffy-be.jar
USER scaffy
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/scaffy-be.jar"]
