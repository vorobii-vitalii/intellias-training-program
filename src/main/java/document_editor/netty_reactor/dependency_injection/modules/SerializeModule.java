package document_editor.netty_reactor.dependency_injection.modules;

import java.io.ByteArrayOutputStream;

import javax.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import dagger.Module;
import dagger.Provides;
import serialization.Serializer;

@Module(includes = JacksonModule.class)
public class SerializeModule {

	@Provides
	@Singleton
	Serializer serializer(ObjectMapper objectMapper) {
		return obj -> {
			var arrayOutputStream = new ByteArrayOutputStream();
//			objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
			objectMapper.writeValue(arrayOutputStream, obj);
			return arrayOutputStream.toByteArray();
		};
	}

}
