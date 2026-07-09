FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests -Dmaven.repo.local=/root/.m2/repository \
    -Dmaven.test.skip=true

FROM amazoncorretto:17-alpine-jdk

WORKDIR /app

COPY --from=builder /app/target/fire-training-1.0.0.jar .

EXPOSE 8136

ENTRYPOINT ["java", "-jar", "fire-training-1.0.0.jar"]