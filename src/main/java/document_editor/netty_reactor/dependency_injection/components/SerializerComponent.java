package document_editor.netty_reactor.dependency_injection.components;

import javax.inject.Singleton;

import dagger.Component;
import document_editor.netty_reactor.dependency_injection.modules.SerializeModule;
import serialization.Serializer;

@Component(modules = SerializeModule.class)
@Singleton
public interface SerializerComponent {
	Serializer createSerializer();
}
