# Compila con Gradle y ejecuta con un JRE liviano (igual que los microservicios).

# Compilacion
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
# Dependencias primero para aprovechar la cache.
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null 2>&1 || true
COPY src ./src
RUN gradle bootJar -x test --no-daemon -q \
 && find build/libs -name "*-plain.jar" -delete \
 && mv build/libs/*.jar app.jar

# Ejecucion
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S eter && adduser -S eter -G eter
COPY --from=build /app/app.jar app.jar
USER eter
ENV JAVA_TOOL_OPTIONS="-Xms96m -Xmx256m -Duser.timezone=America/Bogota"
EXPOSE 4000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
