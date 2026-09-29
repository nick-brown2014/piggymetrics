FROM eclipse-temurin:21-jre
LABEL maintainer="Alexander Lukyanchikov <sqshq@sqshq.com>"

ADD ./target/auth-service.jar /app/
CMD ["java", "-Xmx200m", "-jar", "/app/auth-service.jar"]

EXPOSE 5000
