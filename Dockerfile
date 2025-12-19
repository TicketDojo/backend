FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080 8081

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]