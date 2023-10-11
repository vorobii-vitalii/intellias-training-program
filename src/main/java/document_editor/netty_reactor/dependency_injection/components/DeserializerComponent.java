package document_editor.netty_reactor.dependency_injection.components;

import javax.inject.Singleton;

import dagger.Component;
import document_editor.netty_reactor.dependency_injection.modules.DeserializerModule;
import serialization.Deserializer;

@Component(modules = DeserializerModule.class)
@Singleton
public interface DeserializerComponent {
	Deserializer createDeserializer();
}
