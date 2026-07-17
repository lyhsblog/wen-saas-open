# syntax=docker/dockerfile:1
# 后端镜像：容器内 Maven 编译 + JRE 运行，本机无需 JDK / Maven。
# 由同目录 docker-compose.yml 使用（全容器一键运行，构建上下文 = wen-saas-boot/，
# 上下文剔除项见同目录 backend.Dockerfile.dockerignore）。
# Maven 本地仓库走 BuildKit cache mount：依赖只在首次构建时下载，之后重建只重新编译。
# 依赖默认走阿里云镜像源（本编排主要受众在中国境内）；需要官方源时构建传参：
#   MAVEN_MIRROR=https://repo.maven.apache.org/maven2

FROM maven:3.9-eclipse-temurin-25 AS build
ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public
# settings 必须放在 /root/.m2 之外并用 -s 指定：编译时的 cache mount 会盖住 /root/.m2。
# mirror id 固定为 central：本地仓库按来源仓 id 记账，换源后已缓存依赖仍视为可用，不会全量重下
RUN printf '<settings><mirrors><mirror><id>central</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR" > /maven-settings.xml
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -s /maven-settings.xml clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/app/target/wen-saas-boot-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
