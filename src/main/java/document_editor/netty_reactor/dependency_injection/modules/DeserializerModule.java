package document_editor.netty_reactor.dependency_injection.modules;

import javax.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import dagger.Module;
import dagger.Provides;
import serialization.Deserializer;
import serialization.JacksonDeserializer;

@Module(includes = JacksonModule.class)
public class DeserializerModule {

	@Provides
	@Singleton
	Deserializer deserializer(ObjectMapper objectMapper) {
		return new JacksonDeserializer(objectMapper);
	}

}
