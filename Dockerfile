FROM eclipse-temurin:11 AS builder
COPY . /tmp
WORKDIR /tmp
RUN chmod 700 ./mvnw
RUN ./mvnw clean package
RUN java -Djarmode=layertools -jar target/resource-group-controller.jar extract

FROM gcr.io/distroless/java:11
COPY --from=builder /tmp/dependencies/ /app
COPY --from=builder /tmp/snapshot-dependencies/ /app
COPY --from=builder /tmp/spring-boot-loader/ /app
COPY --from=builder /tmp/application/ /app
WORKDIR /app
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
