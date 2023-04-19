package document_editor.neo4j;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.*;
import org.neo4j.driver.async.AsyncSession;
import org.treedoc.buffer.AtomBuffer;
import org.treedoc.path.MutableTreeDocPathImpl;
import org.treedoc.path.TreeDocPath;
import org.treedoc.path.TreeDocPathComparator;
import org.treedoc.utils.Pair;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.neo4j.driver.Values.parameters;

public class Neo4jDocumentsAtomBuffer implements AtomBuffer<Character, Integer> {

	private static final Logger LOGGER = LogManager.getLogger(Neo4jDocumentsAtomBuffer.class);

	public static final String BEFORE_LAST_NODE_NAME = "n";
	public static final String LAST_NODE_NAME = "m";
	public static final String EMPTY_NODE_ALIAS = "";
	public static final String DOCUMENT_NODE = "doc";
	private final Driver driver;
	private final Integer documentId;
	private final PooledObjectFactory<AsyncSession> objectFactory = new BasePooledObjectFactory<>() {

		@Override
		public AsyncSession create() {
			return driver.session(AsyncSession.class);
		}

		@Override
		public PooledObject<AsyncSession> wrap(AsyncSession session) {
			return new DefaultPooledObject<>(session);
		}
	};
	private final ObjectPool<AsyncSession> pool = new GenericObjectPool<>(objectFactory);

	public Neo4jDocumentsAtomBuffer(Driver driver, Integer documentId) {
		this.driver = driver;
		this.documentId = documentId;
//
//		try (var session = driver.session()) {
//			session.executeWriteWithoutResult(tx -> {
//				tx.run(new Query("MERGE (doc:DOCUMENT {documentId: %s})".formatted(documentId)));
//			});
	}

	public static void main(String[] args) {
		/*
		MATCH (doc: Document {documentId: 2}) -[:REF]-> (p:Path {dis: 1, v: 0})
MERGE (p)-[:REF]->(r:Path {dis: 2, v: 0})
		 */
		Driver driver = GraphDatabase.driver("bolt://localhost:7689", AuthTokens.basic("document_editor/neo4j", "password"));
		try (var session = driver.session()) {
			var greeting = session.executeWrite(tx -> {
				var query = new Query("CREATE (a:Greeting) SET a.message = $message RETURN a.message + ', from node ' + id(a)",
								parameters("message", "Hi"));
				var result = tx.run(query);
				return result.single().get(0).asString();
			});
			System.out.println(greeting);
		}
	}

	@Override
	public List<Pair<TreeDocPath<Integer>, Character>> getEntries() {
		try {
			return executeInSession(session -> session.executeReadAsync(tx -> {
				String query = "MATCH p = (" + DOCUMENT_NODE + ":DOCUMENT {documentId: %s}) -[*]->(n) WHERE n.s IS NOT NULL RETURN n.s, relationships(p)".formatted(documentId);
				System.out.println("getEntries() " + query);

				return tx.runAsync(new Query(query)).thenCompose(resultCursor -> resultCursor.listAsync()
								.thenApply(list -> list.stream()
												.map(record -> {
													var relationships = record.get(1).asList(Value::asRelationship);
													MutableTreeDocPathImpl<Integer> path = new MutableTreeDocPathImpl<>(relationships.size());
													for (int i = 0; i < relationships.size(); i++) {
														var relationship = relationships.get(i);
														path.disambiguatorAt(i, Integer.parseInt(relationship.get("d").asString()));
														if (relationship.get("v").asInt() == 1) {
															path.set(i);
														}
													}
													return new Pair<>((TreeDocPath<Integer>) path, record.get(0).asString().charAt(0));
												})
												.sorted(Comparator.comparing(Pair::first, new TreeDocPathComparator<>()))
												.collect(Collectors.toList())));
			})).toCompletableFuture().get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void insert(TreeDocPath<Integer> treeDocPath, Character character) {
		LOGGER.info("Start insert");
		executeInSession(session -> {
			session.executeWriteAsync(tx -> {
				String query = calculateCipherTreeUpdateQuery(treeDocPath, character);
				LOGGER.info(query);
				tx.runAsync(new Query(query));
				return null;
			});
			return null;
		});
		LOGGER.info("Insert done");
	}

	@Override
	public void delete(TreeDocPath<Integer> treeDocPath) {
		LOGGER.info("Start delete");
		executeInSession(session -> {
			session.executeWriteAsync(tx -> {
				String query = calculateCipherTreeUpdateQuery(treeDocPath, null);
				LOGGER.info(query);
				tx.runAsync(new Query(query));
				return null;
			});
			return null;
		});
		LOGGER.info("Delete done");
	}

	private <T> T executeInSession(Function<AsyncSession, T> function) {
		try {
			AsyncSession session = driver.session(AsyncSession.class);
			T res = function.apply(session);
//			pool.returnObject(session);
			return res;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String calculateCipherTreeUpdateQuery(TreeDocPath<Integer> treeDocPath, Character character) {
		var matchDocumentClause = "MERGE (" + DOCUMENT_NODE + ":DOCUMENT {documentId: %s}) ".formatted(documentId);
		String updatedValue = character == null ? "null" : "'" + StringEscapeUtils.escapeJava(character + "") + "'";

		return matchDocumentClause + IntStream.range(0, treeDocPath.length())
						.mapToObj(i -> {
							String prev = i == 0 ? DOCUMENT_NODE : "n" + (i - 1);
							String curr = "n" + i;
							return "MERGE (" + prev + ")" + getNodeReference(treeDocPath, i) + "(" + curr + ")";
						})
						.collect(Collectors.joining("\n"))
						+ " SET n" + (treeDocPath.length() - 1) + ".s = " + updatedValue;
	}

	private String getNodeReference(TreeDocPath<Integer> treeDocPath, int index) {
		var d = treeDocPath.disambiguatorAt(index);
		var s = d == null ? "null" : "'" + d + "'";
		return " -[:REF {d: %s, v: %d}]-> ".formatted(s, treeDocPath.isSet(index) ? 1 : 0);
	}

	private String getNodeAlias(TreeDocPath<Integer> treeDocPath, int i) {
		if (i == treeDocPath.length() - 1) {
			return LAST_NODE_NAME;
		}
		return i == treeDocPath.length() - 2 ? BEFORE_LAST_NODE_NAME : EMPTY_NODE_ALIAS;
	}

}
