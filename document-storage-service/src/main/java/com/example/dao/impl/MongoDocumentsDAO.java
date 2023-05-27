package com.example.dao.impl;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.reactivestreams.client.MongoCollection;

import reactor.core.publisher.Flux;

public class MongoDocumentsDAO implements DocumentsDAO {
    public static final String DOCUMENT_ID = "documentId";
    public static final String DIRECTIONS = "directions";
    private static final String DISAMBIGUATORS = "disambiguators";
    public static final String VALUE = "value";
    public static final int BATCH_SIZE = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDocumentsDAO.class);
    public static final String DELETING = "deleting";
    private final MongoCollection<Document> collection;

    public MongoDocumentsDAO(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public void applyChanges(ChangesRequest changesRequest) {
        var changesList = changesRequest.getChangesList();
        var changesGroups = changesList.stream().collect(Collectors.partitioningBy(Change::hasCharacter));
        applyIfNotEmpty(changesGroups.get(true), this::doInserts);
        applyIfNotEmpty(changesGroups.get(false), this::doDeletes);

    }

    @Override
    public Publisher<DocumentElements> fetchDocumentElements(int documentId) {
        return Flux.from(collection
                        .find(Filters.and(
                                Filters.eq(DOCUMENT_ID, documentId),
                                Filters.not(Filters.exists("deleting"))
                        ))
                        .projection(new Document().append(DIRECTIONS, 1).append(DISAMBIGUATORS, 1).append(VALUE, 1)))
                .buffer(BATCH_SIZE)
                .map(documents ->
                        DocumentElements.newBuilder()
                                .addAllDocumentElements(documents.stream()
                                        .map(document ->
                                                DocumentElement.newBuilder()
                                                        .setCharacter(document.getInteger(VALUE))
                                                        .addAllDirections(document.getList(DIRECTIONS, Boolean.class))
                                                        .addAllDisambiguators(document.getList(DISAMBIGUATORS, Integer.class))
                                                        .build())
                                        .collect(Collectors.toList()))
                                .build());
    }

    @Override
    public Publisher<DocumentChangedEvents> subscribeToDocumentsChanges(SubscribeForDocumentChangesRequest request) {
        return Flux.from(getChangeStreamDocumentPublisher(request))
                .bufferTimeout(request.getBatchSize(), Duration.ofMillis(request.getBatchTimeout()))
                .map(documents -> DocumentChangedEvents.newBuilder()
                        .addAllEvents(documents.stream()
                                .filter(doc -> doc.getFullDocument() != null)
                                .map(doc -> {
                                    var fullDocument = doc.getFullDocument();
                                    var changeBuilder = Change.newBuilder()
                                            .setDocumentId(fullDocument.getInteger(DOCUMENT_ID))
                                            .addAllDirections(fullDocument.getList(DIRECTIONS, Boolean.class))
                                            .addAllDisambiguators(fullDocument.getList(DISAMBIGUATORS, Integer.class));
                                    if (!fullDocument.containsKey(DELETING)) {
                                        changeBuilder.setCharacter(fullDocument.getInteger(VALUE));
                                    }
                                    return DocumentChangedEvent.newBuilder()
                                            .setResumeToken(doc.getResumeToken().toJson())
                                            .setChange(changeBuilder.build())
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

    private void doDeletes(List<Change> deletes) {
        var filters = deletes.stream()
                .map(delete -> Filters.and(
                        Filters.eq(DOCUMENT_ID, delete.getDocumentId()),
                        Filters.eq(DIRECTIONS, delete.getDirectionsList()),
                        Filters.eq(DISAMBIGUATORS, delete.getDisambiguatorsList())))
                .toList();
        var filter = Filters.or(filters);
        List<WriteModel<Document>> aggregationPipeline = List.of(
                new UpdateManyModel<>(filter, new Document().append("$set", new Document(DELETING, true))),
                new DeleteManyModel<>(filter));
        var bulkWriteOptions = new BulkWriteOptions().ordered(true);
        collection.bulkWrite(aggregationPipeline, bulkWriteOptions)
                .subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(BulkWriteResult bulkWriteResult) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.warn("Error during delete " + t);
                    }

                    @Override
                    public void onComplete() {
                        LOGGER.debug("Delete completed");
                    }
                });
    }

    private void doInserts(List<Change> inserts) {
        collection.bulkWrite(
                inserts.stream()
                        .map(c -> new UpdateOneModel<Document>(
                                new Document()
                                        .append(DOCUMENT_ID, c.getDocumentId())
                                        .append(DIRECTIONS, c.getDirectionsList())
                                        .append(DISAMBIGUATORS, c.getDisambiguatorsList()),
                                new Document().append("$set", new Document().append(VALUE, c.getCharacter())),
                                new UpdateOptions().upsert(true)
                        ))
                        .collect(Collectors.toList())
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

    private <T> void applyIfNotEmpty(List<T> list, Consumer<List<T>> consumer) {
        if (list.isEmpty()) {
            return;
        }
        consumer.accept(list);
    }
}
