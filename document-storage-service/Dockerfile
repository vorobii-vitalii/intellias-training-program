FROM openjdk:19-alpine

ARG JAR_FILE

ADD target/${JAR_FILE} /usr/share/app.jar

ENTRYPOINT java -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Dcom.sun.management.jmxremote.local.only=false \
  -XX:+UseG1GC \
  -agentlib:jdwp=transport=dt_socket,address=*:8005,server=y,suspend=n -cp /usr/share/app.jar com.example.DocumentStorageServer

