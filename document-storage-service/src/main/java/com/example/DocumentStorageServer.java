package com.example;

import com.example.dao.impl.MongoDocumentsDAO;
import com.example.grpc.DocumentStorageServiceImpl;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.grpc.ServerBuilder;
import org.bson.Document;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;

public class DocumentStorageServer {

    public static final String DOCUMENTS_COLLECTION = "docCol";
    public static final int MIN_SIZE = 10;
    public static final int MAX_SIZE = 150;

    public static void main(String[] args) throws IOException, InterruptedException {
        var settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(getMongoConnectionURL()))
                .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                .applyToConnectionPoolSettings(builder -> builder.minSize(MIN_SIZE).maxSize(MAX_SIZE))
                .build();
        var mongoClient = MongoClients.create(settings);
        var mongoClientDatabase = mongoClient.getDatabase("test");
        var collection = mongoClientDatabase.getCollection(DOCUMENTS_COLLECTION);

        var mongoDocumentsDAO = new MongoDocumentsDAO(collection);

        var server = ServerBuilder.forPort(getServerPort())
                .addService(new DocumentStorageServiceImpl(mongoDocumentsDAO))
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
