FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache git curl

WORKDIR /app
COPY target/*.jar app.jar

# Config directory (mount ConfigMap or git-sync volume here)
RUN mkdir -p /opt/cwc/config
VOLUME /opt/cwc/config

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
