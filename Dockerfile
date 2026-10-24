# 1단계: JDK와 Gradle로 jar를 만든다. 테스트는 CI가 따로 돌린다.
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /src
# 의존성 층을 먼저 만들어 두면 소스만 바뀐 빌드는 다운로드를 건너뛴다(레이어 캐시)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon -q bootJar

# 2단계: JRE와 jar만. 빌드 도구와 소스는 들어가지 않는다.
FROM eclipse-temurin:21.0.12.1_1-jre-noble
# ghcr이 이 이미지를 저장소에 연결한다
LABEL org.opencontainers.image.source=https://github.com/Limsangkeun/saip
RUN useradd --system --uid 10001 app \
 && mkdir -p /data/images && chown app /data/images
COPY --from=build /src/build/libs/blog-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
