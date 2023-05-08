FROM openjdk:19-alpine

ARG JAR_FILE

ADD target/$JAR_FILE ./app.jar
#ADD opentelemetry-javaagent.jar ./opentelemetry-javaagent.jar

ENTRYPOINT java -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9010 \
#  -javaagent:./opentelemetry-javaagent.jar \
#  -Dotel.service.name=document-streaming-app  \
#  -Dotel.traces.exporter=jaeger  \
#  -Dotel.exporter.jaeger.endpoint=${JAEGER_ENDPOINT}  \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Dcom.sun.management.jmxremote.local.only=false \
  -XX:+UseG1GC \
  -agentlib:jdwp=transport=dt_socket,address=*:8005,server=y,suspend=n -cp app.jar document_editor.HttpServer
