FROM eclipse-temurin:17-jre

WORKDIR /app
COPY app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul \
    JAVA_OPTS="-Xms256m -Xmx768m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
