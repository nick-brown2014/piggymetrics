FROM eclipse-temurin:21-jre
LABEL maintainer="Alexander Lukyanchikov <sqshq@sqshq.com>"

ADD ./target/monitoring.jar /app/
CMD ["java", "-Xmx200m", "-jar", "/app/monitoring.jar"]

EXPOSE 8080
