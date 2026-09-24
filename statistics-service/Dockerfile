FROM eclipse-temurin:21-jre
LABEL maintainer="Alexander Lukyanchikov <sqshq@sqshq.com>"

ADD ./target/statistics-service.jar /app/
CMD ["java", "-Xmx200m", "-jar", "/app/statistics-service.jar"]

EXPOSE 7000
