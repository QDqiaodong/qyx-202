FROM ${DOCKER_REGISTRY:-docker.io/library/}maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY settings.xml /etc/maven/settings.xml

COPY pom.xml .

RUN mvn dependency:go-offline -s /etc/maven/settings.xml

COPY src ./src

RUN mvn clean package -DskipTests -s /etc/maven/settings.xml

FROM ${DOCKER_REGISTRY:-docker.io/library/}eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/ship-room-1.0.0.jar app.jar

EXPOSE 8132

ENTRYPOINT ["java", "-jar", "app.jar"]