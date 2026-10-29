FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/ /tmp/libs/
RUN cp "$(ls /tmp/libs/*.jar | grep -v -- '-plain.jar$')" /app/app.jar && rm -rf /tmp/libs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]