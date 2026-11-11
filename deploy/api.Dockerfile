FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src/api
COPY api/ ./
RUN mvn -q -B -pl odin-bootstrap -am -DskipTests -Dcheckstyle.skip -Djacoco.skip=true package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 odin && mkdir -p /var/lib/odin/originals && chown odin /var/lib/odin/originals
COPY --from=build /src/api/odin-bootstrap/target/odin-bootstrap-*-exec.jar /app/odin.jar
USER odin
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/odin.jar"]
