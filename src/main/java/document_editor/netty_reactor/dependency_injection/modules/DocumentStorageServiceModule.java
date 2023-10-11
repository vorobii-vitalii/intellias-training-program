package document_editor.netty_reactor.dependency_injection.modules;

import javax.inject.Named;
import javax.inject.Singleton;

import com.example.document.storage.RxDocumentStorageServiceGrpc;

import dagger.Module;
import dagger.Provides;
import document_editor.netty_reactor.dependency_injection.constants.Constants;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;

@Module
public class DocumentStorageServiceModule {

	@Provides
	@Singleton
	RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService(@Named(Constants.DOCUMENT_STORAGE_SERVICE_URI) String uri) {
		return RxDocumentStorageServiceGrpc.newRxStub(Grpc.newChannelBuilder(uri, InsecureChannelCredentials.create()).build());
	}

}
