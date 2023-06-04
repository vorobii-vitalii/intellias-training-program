package com.example.dao;

import com.example.document.storage.*;
import com.mongodb.bulk.BulkWriteResult;
import org.reactivestreams.Publisher;

public interface DocumentsDAO {
	Publisher<BulkWriteResult> applyChanges(ChangesRequest changesRequest);
	Publisher<DocumentElements> fetchDocumentElements(int documentId);
	Publisher<DocumentChangedEvents> subscribeToDocumentsChanges(SubscribeForDocumentChangesRequest request);
}
