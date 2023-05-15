package com.example.dao.impl;

import com.example.dao.DocumentsDAO;
import com.example.document.storage.*;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.*;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MongoDocumentsDAO implements DocumentsDAO {
    public static final String DOCUMENT_ID = "documentId";
    public static final String PATH = "path";
    public static final String VALUE = "value";
    public static final int BATCH_SIZE = 500;
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
                        .projection(new Document().append(PATH, 1).append(VALUE, 1)))
                .buffer(BATCH_SIZE)
                .map(documents ->
                        DocumentElements.newBuilder()
                                .addAllDocumentElements(documents.stream()
                                        .map(document ->
                                                DocumentElement.newBuilder()
                                                        .setPath(extractPath(document))
                                                        .setCharacter(document.getInteger(VALUE))
                                                        .build())
                                        .collect(Collectors.toList()))
                                .build());
    }

    private TreePath extractPath(Document document) {
        return fromString(Base64.getDecoder().decode(document.getString(PATH)));
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
                                            .setPath(extractPath(fullDocument));
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
                        Filters.eq(PATH, toString(delete.getPath()))))
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
        var documentsToInsert = inserts.stream()
                .map(c -> {
                    return new Document()
                            .append(DOCUMENT_ID, c.getDocumentId())
                            .append(PATH, toString(c.getPath()))
                            .append(VALUE, c.getCharacter());

                })
                .collect(Collectors.toList());
        collection.insertMany(documentsToInsert)
                .subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(InsertManyResult insertManyResult) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.error("Error " + t);
                    }

                    @Override
                    public void onComplete() {
                        LOGGER.debug("Insert complete");
                    }
                });
    }

    private <T> void applyIfNotEmpty(List<T> list, Consumer<List<T>> consumer) {
        if (list.isEmpty()) {
            return;
        }
        consumer.accept(list);
    }

    private TreePath fromString(byte[] bytes) {
        try {
            return TreePath.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private String toString(TreePath path) {
        return Base64.getEncoder().encodeToString(path.toByteArray());
    }
}
