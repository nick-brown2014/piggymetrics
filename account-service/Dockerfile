FROM eclipse-temurin:21-jre
LABEL maintainer="Alexander Lukyanchikov <sqshq@sqshq.com>"

ADD ./target/account-service.jar /app/
CMD ["java", "-Xmx200m", "-jar", "/app/account-service.jar"]

EXPOSE 6000
