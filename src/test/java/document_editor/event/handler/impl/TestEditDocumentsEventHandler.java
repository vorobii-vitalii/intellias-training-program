package document_editor.event.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.document.storage.ChangesResponse;
import com.example.document.storage.RxDocumentStorageServiceGrpc;

import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEventType;
import document_editor.event.EditDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import grpc.ServiceDecorator;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.reactivex.Single;
import serialization.Serializer;
import tcp.server.impl.MessageSerializerImpl;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

@ExtendWith(MockitoExtension.class)
class TestEditDocumentsEventHandler {
	private static final byte[] BYTES = new byte[] {2, 1, 3};
	private static final ByteBuffer BYTE_BUFFER = ByteBuffer.wrap(BYTES);

	public static final String CHANGE_ID = "236367423";
	@Mock
	RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageServiceStub;
	@Mock
	ServiceDecorator serviceDecorator;
	@Mock
	SocketConnection socketConnection;
	@Mock
	Context context;
	@Mock
	ClientConnectionsContext clientConnectionsContext;
	@Mock
	MessageSerializerImpl messageSerializer;
	@Mock
	Serializer serializer;
	EditEventHandler editEventHandler;

	@BeforeEach
	void setUp() {
		editEventHandler = new EditEventHandler(documentStorageServiceStub, TracerProvider.noop().get("name"), serviceDecorator,
				messageSerializer, serializer, () -> context);
	}

	@Test
	void getHandledEventType() {
		assertThat(editEventHandler.getHandledEventType()).isEqualTo(DocumentsEventType.EDIT);
	}

	@Test
	void handleHappyPath() throws IOException {
		var editEvent = new EditDocumentsEvent(
				List.of(
						new document_editor.dto.Change(
								"aeget3t3g", null, false, 22, 'a'
						)),
				CHANGE_ID,
				socketConnection
		);
		when(serializer.serialize(any())).thenReturn(BYTES);
		when(messageSerializer.serialize(any())).thenReturn(BYTE_BUFFER);
		when(serviceDecorator.decorateService(any())).thenAnswer(i -> i.getArgument(0));
		when(documentStorageServiceStub.applyChanges(any(Single.class))).thenReturn(
				Single.just(ChangesResponse.newBuilder().build()));
		editEventHandler.handle(editEvent, clientConnectionsContext);
		// Verify acknowledgement has been sent to client
		verify(socketConnection).appendResponse(BYTE_BUFFER);
		verify(socketConnection).changeOperation(OperationType.WRITE);
		verify(serializer).serialize(argThat(v -> {
			var response = (Response) v;
			return response.responseType() == ResponseType.ACK;
		}));
	}

	@Test
	void handleErrorCase() throws IOException {
		var editEvent = new EditDocumentsEvent(
				List.of(
						new document_editor.dto.Change(
								"aeget3t3g", null, false, 22, 'a'
						)),
				CHANGE_ID,
				socketConnection
		);
		when(serializer.serialize(any())).thenReturn(BYTES);
		when(messageSerializer.serialize(any())).thenReturn(BYTE_BUFFER);
		when(serviceDecorator.decorateService(any())).thenAnswer(i -> i.getArgument(0));
		when(documentStorageServiceStub.applyChanges(any(Single.class))).thenReturn(
				Single.error(new RuntimeException()));
		editEventHandler.handle(editEvent, clientConnectionsContext);
		// Verify acknowledgement has been sent to client
		verify(socketConnection).appendResponse(BYTE_BUFFER);
		verify(socketConnection).changeOperation(OperationType.WRITE);
		verify(serializer).serialize(argThat(v -> {
			var response = (Response) v;
			return response.responseType() == ResponseType.NACK;
		}));
	}

}
