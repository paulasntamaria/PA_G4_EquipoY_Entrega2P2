# Imagen del microservicio. Es igual en los 4 servicios: compila con Gradle
# dentro de Docker (no hace falta tener Java instalado) y ejecuta con un JRE liviano.

# --- Etapa 1: compilacion ---------------------------------------------------
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
# Primero solo los archivos de Gradle: si no cambian, Docker reutiliza la capa
# con las dependencias descargadas y el build siguiente es mucho mas rapido.
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null 2>&1 || true
COPY src ./src
RUN gradle bootJar -x test --no-daemon -q \
 && find build/libs -name "*-plain.jar" -delete \
 && mv build/libs/*.jar app.jar

# --- Etapa 2: ejecucion -----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S eter && adduser -S eter -G eter
COPY --from=build /app/app.jar app.jar
USER eter
# Hora de Colombia para que el vigia compare las fechas pactadas en hora local.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -Duser.timezone=America/Bogota"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
