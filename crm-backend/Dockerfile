# ================================================================
#  Многоступенчатая сборка Spring Boot с layered JAR
#  Слои разделены для максимального кэширования Docker
# ================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Сначала копируем только pom.xml — кэшируем зависимости
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

# Копируем исходники и собираем
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# Разбираем layered JAR на слои
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /build/extracted

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Создаём непривилегированного пользователя
RUN addgroup -S crm && adduser -S crm -G crm

WORKDIR /app

# Копируем слои в правильном порядке (от редко меняемых к часто)
# Это максимизирует кэш слоёв Docker при пересборке
COPY --from=builder --chown=crm:crm /build/extracted/dependencies/ ./
COPY --from=builder --chown=crm:crm /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=crm:crm /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=crm:crm /build/extracted/application/ ./

USER crm

EXPOSE 8080

# Настройки JVM для контейнеров:
# -XX:+UseContainerSupport — учитывает лимиты cgroup (k8s resources.limits)
# -XX:MaxRAMPercentage=75 — использует 75% выделенной памяти под heap
# -XX:+ExitOnOutOfMemoryError — падаем чётко при OOM (k8s перезапустит)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/actuator/health | grep -q '"status":"UP"' || exit 1
