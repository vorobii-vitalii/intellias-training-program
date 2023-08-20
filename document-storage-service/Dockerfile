FROM openjdk:19-alpine

ARG JAR_FILE

ADD target/${JAR_FILE} /usr/share/app.jar
ADD opentelemetry-javaagent.jar /lib/opentelemetry-javaagent.jar

ENTRYPOINT java -Dcom.sun.management.jmxremote \
  -javaagent:/lib/opentelemetry-javaagent.jar \
  -Dotel.traces.exporter=jaeger \
  -Dotel.exporter.jaeger.endpoint=http://jaeger:14250 \
  -Dotel.javaagent.debug=true \
  -Dotel.service.name=document-storage-service \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Dcom.sun.management.jmxremote.local.only=false \
  -XX:+UseG1GC \
  -agentlib:jdwp=transport=dt_socket,address=*:8005,server=y,suspend=n -cp /usr/share/app.jar com.example.DocumentStorageServer

