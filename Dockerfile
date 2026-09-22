
# Build da aplicação com Maven e OpenJDK 21

FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copia pom.xml e pré-baixa dependências para cachear camadas
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copia código-fonte e compila para gerar o JAR executável
COPY src ./src
RUN mvn clean package -DskipTests -B


# ETAPA 2: Runtime enxuto com JRE 21

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Cria usuário não-root por segurança
RUN addgroup -S helpdesk && adduser -S helpdesk -G helpdesk

COPY --from=builder --chown=helpdesk:helpdesk /workspace/target/*.jar app.jar
USER helpdesk

# Parâmetros JVM otimizados para microsserviços em container
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]