# BAS-25 Dockerfile 模板：多阶段构建（构建用 maven+JDK17，运行用 JRE17），非 root 运行。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 用户运行
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/{app}-server/target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
