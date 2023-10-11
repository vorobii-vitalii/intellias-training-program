package document_editor.netty_reactor.dependency_injection.modules;

import javax.inject.Singleton;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import dagger.Module;
import dagger.Provides;

@Module
public class JacksonModule {

	@Provides
	@Singleton
	ObjectMapper objectMapper() {
		return new ObjectMapper(new MessagePackFactory());
	}

}
