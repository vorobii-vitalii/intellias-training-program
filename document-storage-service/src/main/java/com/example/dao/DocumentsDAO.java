package com.example.dao;

import com.example.document.storage.*;
import org.reactivestreams.Publisher;

public interface DocumentsDAO {
	void applyChanges(ChangesRequest changesRequest);
	Publisher<DocumentElements> fetchDocumentElements(int documentId);
	Publisher<DocumentChangedEvents> subscribeToDocumentsChanges(SubscribeForDocumentChangesRequest request);
}
