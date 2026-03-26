FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -Dmaven.test.skip=true -Dasciidoctor.skip=true clean package && cp target/terra-api-*.jar /tmp/app.jar

FROM bellsoft/liberica-openjdk-debian:17

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /tmp/app.jar /app/app.jar

RUN mkdir -p /app/uploads/avatars && chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["/usr/lib/jvm/jdk/bin/java", "-jar", "/app/app.jar"]
