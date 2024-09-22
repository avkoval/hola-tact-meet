FROM openjdk:8-alpine

COPY target/uberjar/hola-tact-meet.jar /hola-tact-meet/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/hola-tact-meet/app.jar"]
