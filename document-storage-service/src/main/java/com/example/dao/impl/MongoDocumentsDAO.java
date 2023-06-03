package com.example.dao.impl;

import java.time.Duration;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.dao.DocumentsDAO;
import com.example.document.storage.Change;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.DocumentChangedEvent;
import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.DocumentElement;
import com.example.document.storage.DocumentElements;
import com.example.document.storage.SubscribeForDocumentChangesRequest;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;

import reactor.core.publisher.Flux;

public class MongoDocumentsDAO implements DocumentsDAO {
    public static final String DOCUMENT_ID = "documentId";
    public static final String CHAR_ID = "charId";
    public static final String PARENT_CHAR_ID = "parentCharId";
    public static final String IS_RIGHT = "isRight";
    public static final String DISAMBIGUATOR = "disambiguator";
    public static final String VALUE = "value";
    public static final int BATCH_SIZE = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDocumentsDAO.class);
    private final MongoCollection<Document> collection;

    public MongoDocumentsDAO(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    private static DocumentElement toDocumentElement(Document document) {
        var value = document.getInteger(VALUE);
        var builder = DocumentElement.newBuilder()
                .setCharId(document.getString(CHAR_ID))
                .setParentCharId(document.getString(PARENT_CHAR_ID))
                .setIsRight(document.getBoolean(IS_RIGHT))
                .setDisambiguator(document.getInteger(DISAMBIGUATOR));
        if (value != null) {
            builder.setCharacter(value);
        }
        return builder.build();
    }

    private static Change toChange(Document document) {
        var value = document.getInteger(VALUE);
        var builder = Change.newBuilder()
                .setCharId(document.getString(CHAR_ID))
                .setParentCharId(document.getString(PARENT_CHAR_ID))
                .setIsRight(document.getBoolean(IS_RIGHT))
                .setDocumentId(document.getInteger(DOCUMENT_ID))
                .setDisambiguator(document.getInteger(DISAMBIGUATOR));
        if (value != null) {
            builder.setCharacter(value);
        }
        return builder.build();
    }

    @Override
    public void applyChanges(ChangesRequest changesRequest) {
        var changesList = changesRequest.getChangesList();
        collection.bulkWrite(
                changesList.stream()
                        .map(c -> {
                            if (c.hasCharacter()) {
                                return new UpdateOneModel<Document>(
                                        new Document()
                                                .append(DOCUMENT_ID, c.getDocumentId())
                                                .append(CHAR_ID, c.getCharId())
                                                .append(PARENT_CHAR_ID, c.getParentCharId())
                                                .append(IS_RIGHT, c.getIsRight())
                                                .append(DISAMBIGUATOR, c.getDisambiguator()),
                                        new Document().append("$setOnInsert", new Document().append(VALUE, c.getCharacter())),
                                        new UpdateOptions().upsert(true)
                                );
                            }
                            else {
                                return new UpdateOneModel<Document>(
                                        Filters.and(
                                                Filters.eq(DOCUMENT_ID, c.getDocumentId()),
                                                Filters.eq(CHAR_ID, c.getCharId())
                                        ),
                                        new Document().append("$set", new Document(VALUE, null))
                                );
                            }
                        })
                        .collect(Collectors.toList()),
                new BulkWriteOptions().ordered(true)
        ).subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(BulkWriteResult bulkWriteResult) {

            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("Error", t);
            }

            @Override
            public void onComplete() {
                LOGGER.info("Inserts complete");
            }
        });

    }

    @Override
    public Publisher<DocumentElements> fetchDocumentElements(int documentId) {
        return Flux.from(collection
                        .find(Filters.and(
                                Filters.eq(DOCUMENT_ID, documentId)
                        ))
                        .projection(new Document()
                                .append(CHAR_ID, 1)
                                .append(PARENT_CHAR_ID, 1)
                                .append(IS_RIGHT, 1)
                                .append(DISAMBIGUATOR, 1)
                                .append(VALUE, 1)))
                .buffer(BATCH_SIZE)
                .map(documents ->
                        DocumentElements.newBuilder()
                                .addAllDocumentElements(documents.stream()
                                        .map(MongoDocumentsDAO::toDocumentElement)
                                        .collect(Collectors.toList()))
                                .build());
    }

    @Override
    public Publisher<DocumentChangedEvents> subscribeToDocumentsChanges(SubscribeForDocumentChangesRequest request) {
        LOGGER.info("Watching for documents changes {}", request);
        return Flux.from(getChangeStreamDocumentPublisher(request))
                .bufferTimeout(request.getBatchSize(), Duration.ofMillis(request.getBatchTimeout()))
                .map(documents -> DocumentChangedEvents.newBuilder()
                        .addAllEvents(documents.stream()
                                .filter(doc -> doc.getFullDocument() != null)
                                .map(doc -> {
                                    LOGGER.info("Document has changed");
                                    return DocumentChangedEvent.newBuilder()
                                            .setResumeToken(doc.getResumeToken().toJson())
                                            .setChange(toChange(doc.getFullDocument()))
                                            .build();
                                })
                                .collect(Collectors.toList()))
                        .build());
    }

    private Publisher<ChangeStreamDocument<Document>> getChangeStreamDocumentPublisher(
            SubscribeForDocumentChangesRequest request
    ) {
        var startAfter = request.hasResumeToken() ? BsonDocument.parse(request.getResumeToken()) : null;
        var publisher = collection.watch().fullDocument(FullDocument.UPDATE_LOOKUP);
        if (startAfter != null) {
            return publisher.startAfter(startAfter);
        }
        return publisher;
    }
}
