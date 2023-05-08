package document_editor.mongo;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import document_editor.DocumentStreamingWebSocketEndpoint;
import org.bson.conversions.Bson;
import org.msgpack.value.ArrayValue;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.bson.Document;
import org.treedoc.buffer.AtomBuffer;
import org.treedoc.path.MutableTreeDocPath;
import org.treedoc.path.MutableTreeDocPathImpl;
import org.treedoc.path.TreeDocPath;
import org.treedoc.utils.Pair;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import document_editor.PairDTO;

public class MongoReactiveAtomBuffer implements AtomBuffer<Character, Integer> {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoReactiveAtomBuffer.class);
	public static final Base64.Encoder ENCODER = Base64.getEncoder();

	private final MongoCollection<Document> mongoCollection;
	private final int documentId;
	private final ObjectMapper objectMapper;

	public MongoReactiveAtomBuffer(MongoCollection<Document> mongoCollection, int documentId, ObjectMapper objectMapper) {
		this.mongoCollection = mongoCollection;
		this.documentId = documentId;
		this.objectMapper = objectMapper;
	}

	public void streamDocument(Function<DocumentStreamingWebSocketEndpoint.Change, Boolean> function) {
		try (MongoCursor<Document> cursor = mongoCollection
				.find(Filters.and(Filters.eq("documentId", documentId), Filters.not(Filters.exists("deleting"))))
				.projection(new Document().append("path", 1).append("value", 1))
				.batchSize(5000)
				.cursor()
		) {
			while (cursor.hasNext()) {
				final Document document = cursor.next();
				final List<DocumentStreamingWebSocketEndpoint.TreePathEntry> path = fromString(document.getString("path"));
				LOGGER.info("Path {}", path);
				if (!function.apply(new DocumentStreamingWebSocketEndpoint.Change(path, document.getString("value").charAt(0)))) {
					return;
				}
			}
		}
	}

	public void applyChangesBulk(List<DocumentStreamingWebSocketEndpoint.Change> changes) {
		var map = changes.stream().collect(Collectors.partitioningBy(c -> c.b() != null));
		Optional.ofNullable(map.get(true)).ifPresent(c -> {
			if (c.isEmpty()) {
				return;
			}
			LOGGER.info("Inserting {}", c);
			mongoCollection.insertMany(c.stream()
					.map(a -> new Document()
							.append("documentId", documentId)
							.append("path", toString(a.a()))
							.append("value", a.b()))
					.collect(Collectors.toList()));
		});
		Optional.ofNullable(map.get(false)).ifPresent(c -> {
			if (c.isEmpty()) {
				return;
			}
			final List<String> paths =
					c.stream().map(DocumentStreamingWebSocketEndpoint.Change::a).map(this::toString).collect(Collectors.toList());
			LOGGER.info("Deleting {}", paths);
			final Bson filter = Filters.and(Filters.eq("documentId", documentId), Filters.in("path", paths));
			mongoCollection.bulkWrite(List.of(
					new UpdateManyModel<>(filter, new Document().append("$set", new Document("deleting", true))),
					new DeleteManyModel<>(filter)
			), new BulkWriteOptions().ordered(true));
		});


//		List<WriteModel<Document>> collect = changes.stream()
//				.flatMap(request -> {
//					if (request.b() != null) {
//						return Stream.of(new InsertOneModel<>(
//								new Document()
//										.append("documentId", documentId)
//										.append("path", toString(request.a()))
//										.append("value", request.b())
//						));
//					} else {
//						final Bson filter = Filters.and(Filters.eq("documentId", documentId), Filters.eq("path", toString(request.a())));
//						return Stream.of(
//								new UpdateOneModel<Document>(
//										filter,
//										new Document().append("$set", new Document("deleting", true))
//								),
//								new DeleteOneModel<Document>(
//										filter
//								)
//						);
//					}
//				})
//				.collect(Collectors.toList());
//		mongoCollection.bulkWrite(collect, new BulkWriteOptions().ordered(true));
	}

	@Override
	public List<Pair<TreeDocPath<Integer>, Character>> getEntries() {
		return null;
	}

	@Override
	public void insert(TreeDocPath<Integer> treeDocPath, Character character) {
		mongoCollection.insertOne(
				new Document()
						.append("documentId", documentId)
						.append("path", toString(treeDocPath))
						.append("value", character),
				new InsertOneOptions());
	}

	@Override
	public void delete(TreeDocPath<Integer> treeDocPath) {
		var filter = Filters.and(Filters.eq("documentId", documentId), Filters.eq("path", toString(treeDocPath)));
		mongoCollection.updateOne(filter, new Document().append("$set", new Document("deleting", true)));
		mongoCollection.deleteOne(filter);
	}

	private String toString(List<DocumentStreamingWebSocketEndpoint.TreePathEntry> path) {
//		try {
//			return ENCODER.encodeToString(objectMapper.writeValueAsBytes(path));
//		}
//		catch (JsonProcessingException e) {
//			throw new RuntimeException(e);
//		}

		return path.stream().map(treePathEntry -> {
					char c = treePathEntry.a() ? '1' : '0';
					return c + "," + treePathEntry.b();
				})
				.collect(Collectors.joining(" "));
	}

	private String toString(TreeDocPath<Integer> path) {
		return IntStream.range(0, path.length())
				.mapToObj(i -> {
					char c = path.isSet(i) ? '1' : '0';
					return c + "," + path.disambiguatorAt(i);
				})
				.collect(Collectors.joining(" "));
	}

	private List<DocumentStreamingWebSocketEndpoint.TreePathEntry> fromString(String str) {
//		try {
//			return objectMapper.readValue(Base64.getDecoder().decode(str),
//					new TypeReference<List<DocumentStreamingWebSocketEndpoint.TreePathEntry>>() {
//			});
//		}
//		catch (IOException e) {
//			throw new RuntimeException(e);
//		}

		String[] arr = str.split(" ");
		List<DocumentStreamingWebSocketEndpoint.TreePathEntry> list = new ArrayList<>(arr.length);
		for (String value : arr) {
			String[] s = value.split(",");
			boolean isSet = s[0].charAt(0) == '1';
			int d = Integer.parseInt(s[1]);
			list.add(new DocumentStreamingWebSocketEndpoint.TreePathEntry(isSet, d));
		}
		return list;
	}

//	public void applyChangesBulk(ArrayValue arr) {
//		mongoCollection.bulkWrite(arr.stream()
//				.flatMap(request -> {
//					var map = request.asMapValue();
//					var path = getPath(map.get("a").asArrayValue());
//					var v = map.get("b").asRawValue().getString();
//					if (v != null) {
//						return Stream.of(new InsertOneModel<>(
//								new Document()
//										.append("documentId", documentId)
//										.append("path", path)
//										.append("value", v)
//						));
//					} else {
//						var filter = Filters.and(Filters.eq("documentId", documentId), Filters.eq("path", path));
//						return Stream.of(
//								new UpdateOneModel<Document>(
//										filter,
//										new Document().append("$set", new Document("deleting", true))),
//								new DeleteOneModel<Document>(filter));
//					}
//				})
//				.collect(Collectors.toList()), new BulkWriteOptions().ordered(true));
//	}
//
//	private String getPath(ArrayValue pathEntries) {
//		return pathEntries.stream()
//				.map(pathEntry -> {
//					char c = pathEntry.asRawValue().asBooleanValue().getBoolean() ? '1' : '0';
//					return c + "," + Integer.parseInt(pathEntry.asRawValue().getString());
//				})
//				.collect(Collectors.joining(" "));
//	}

	//	private List<DocumentStreamingWebSocketEndpoint.TreePathEntry> fromString(String str) {
//		String[] arr = str.split(" ");
//		MutableTreeDocPath<Integer> path = new MutableTreeDocPathImpl<>(arr.length);
//		for (int i = 0; i < arr.length; i++) {
//			String[] s = arr[i].split(",");
//			if (s[0].charAt(0) == '1') {
//				path.set(i);
//			}
//			path.disambiguatorAt(i, Integer.parseInt(s[1]));
//		}
//		return path;
//	}

}
