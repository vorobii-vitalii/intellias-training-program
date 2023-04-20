package document_editor.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.log4j.BasicConfigurator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class Main {
	private static final Logger LOGGER = LogManager.getLogger(Main.class);

	static abstract class ObservableSubscriber<T> implements Subscriber<T> {
		private CountDownLatch countDownLatch = new CountDownLatch(1);
		private volatile boolean isError;

		@Override
		public void onComplete() {
			countDownLatch.countDown();
		}

		@Override
		public void onError(Throwable throwable) {
			isError = true;
			countDownLatch.countDown();
		}

		public void await() throws InterruptedException {
			countDownLatch.await();
		}
	}

	public static void main(String[] args) {
		BasicConfigurator.configure();

		ConnectionString connString = new ConnectionString("mongodb://localhost:27017");
		ServerApi serverApi = ServerApi.builder()
						.version(ServerApiVersion.V1)
						.build();
		MongoClientSettings settings = MongoClientSettings.builder()
						.applyConnectionString(connString)
						.serverApi(serverApi)
						.build();
		MongoClient mongoClient = MongoClients.create(settings);

		MongoDatabase mongoClientDatabase = mongoClient.getDatabase("test");

		MongoCollection<Document> collection = mongoClientDatabase.getCollection("docCol");
		Document document = new Document("name", "MongoDB")
						.append("type", "database")
						.append("count", 1)
						.append("versions", List.of("v3.2", "v3.0", "v2.6"))
						.append("info", new Document("x", 203).append("y", 102));

		LOGGER.info("Insertion res {}", collection.insertOne(document));

		CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
						fromProviders(PojoCodecProvider.builder().automatic(true).build()));

	}

}
