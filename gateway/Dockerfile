FROM eclipse-temurin:21-jre
LABEL maintainer="Alexander Lukyanchikov <sqshq@sqshq.com>"

ADD ./target/gateway.jar /app/
CMD ["java", "-Xmx200m", "-jar", "/app/gateway.jar"]

EXPOSE 4000
