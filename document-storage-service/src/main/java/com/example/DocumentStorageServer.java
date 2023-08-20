package com.example;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import com.example.dao.impl.MongoDocumentsDAO;
import com.example.grpc.DocumentStorageServiceImpl;
import com.example.interceptor.ContextInterceptor;
import com.example.tracing.ContextExtractor;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCompressor;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.reactivestreams.client.MongoClients;

import io.grpc.ServerBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public class DocumentStorageServer {

    public static final String DOCUMENTS_COLLECTION = "docCol";
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 150;

    public static void main(String[] args) throws IOException, InterruptedException {


        var settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(getMongoConnectionURL()))
                .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                .applyToConnectionPoolSettings(builder -> builder.minSize(MIN_SIZE).maxSize(MAX_SIZE))
                .compressorList(List.of(MongoCompressor.createZlibCompressor()))
                .build();
        var mongoClient = MongoClients.create(settings);
        var mongoClientDatabase = mongoClient.getDatabase("test");
        var collection = mongoClientDatabase.getCollection(DOCUMENTS_COLLECTION);

        var mongoDocumentsDAO = new MongoDocumentsDAO(collection);

        var server = ServerBuilder.forPort(getServerPort())
                .addService(new DocumentStorageServiceImpl(mongoDocumentsDAO))
//                .intercept(new ContextInterceptor(new ContextExtractor(openTelemetry)))
                .executor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()))
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        server.awaitTermination();
    }

    private static int getServerPort() {
        return Integer.parseInt(System.getenv("SERVER_PORT"));
    }

    private static String getMongoConnectionURL() {
        return Optional.ofNullable(System.getenv("MONGO_URL"))
                .orElse("mongodb://localhost:27017");
    }

}
