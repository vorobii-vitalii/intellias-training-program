package document_editor.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.treedoc.buffer.AtomBuffer;
import org.treedoc.path.MutableTreeDocPath;
import org.treedoc.path.MutableTreeDocPathImpl;
import org.treedoc.path.TreeDocPath;
import org.treedoc.path.TreeDocPathComparator;
import org.treedoc.utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MongoDBAtomBuffer implements AtomBuffer<Character, Integer> {
	private final MongoCollection<Document> mongoCollection;
	private final int documentId;

	public MongoDBAtomBuffer(MongoCollection<Document> mongoCollection, int documentId) {
		this.mongoCollection = mongoCollection;
		this.documentId = documentId;
	}

	@Override
	public List<Pair<TreeDocPath<Integer>, Character>> getEntries() {
		try (	MongoCursor<Document> iterator = mongoCollection.find(Filters.eq("documentId", documentId))
						.projection(new Document().append("path", 1).append("value", 1)).iterator()
		) {
			List<Pair<TreeDocPath<Integer>, Character>> list = new ArrayList<>();
			while (iterator.hasNext()) {
				Document document = iterator.next();
				list.add(new Pair<>(fromString(document.getString("path")), document.getString("value").charAt(0)));
			}
			list.sort(Comparator.comparing(Pair::first, new TreeDocPathComparator<>()));
			return list;
		}
	}

	@Override
	public void insert(TreeDocPath<Integer> treeDocPath, Character character) {
		mongoCollection.insertOne(new Document()
						.append("documentId", documentId)
						.append("path", toString(treeDocPath))
						.append("value", character)
		);
	}

	@Override
	public void delete(TreeDocPath<Integer> treeDocPath) {
		var filter = Filters.and(Filters.eq("documentId", documentId), Filters.eq("path", toString(treeDocPath)));
		mongoCollection.updateOne(filter, new Document().append("$set", new Document("deleting", true)));
		mongoCollection.deleteOne(filter);
	}

	private String toString(TreeDocPath<Integer> path) {
		return IntStream.range(0, path.length())
						.mapToObj(i -> {
							char c = path.isSet(i) ? '1' : '0';
							return c + "," + path.disambiguatorAt(i);
						})
						.collect(Collectors.joining(" "));
	}

	private TreeDocPath<Integer> fromString(String str) {
		String[] arr = str.split(" ");
		MutableTreeDocPath<Integer> path = new MutableTreeDocPathImpl<>(arr.length);
		for (int i = 0; i < arr.length; i++) {
			String[] s = arr[i].split(",");
			if (s[0].charAt(0) == '1') {
				path.set(i);
			}
			path.disambiguatorAt(i, Integer.parseInt(s[1]));
		}
		return path;
	}

}
