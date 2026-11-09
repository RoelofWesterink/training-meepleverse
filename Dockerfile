FROM eclipse-temurin:25-jre-alpine

COPY target/meepleverse.jar /

CMD java -jar /meepleverse.jar
