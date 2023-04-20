package document_editor.neo4j;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.treedoc.TreeDOCImpl;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
	private static final Integer USER_1 = 1;
	private static final Integer USER_2 = 2;
	private static final Integer USER_3 = 3;

	static Driver driver = GraphDatabase.driver("bolt://localhost:7689", AuthTokens.basic("document_editor/neo4j", "password"), Config.builder()
					.withMaxConnectionLifetime(30, TimeUnit.MINUTES)
					.withMaxConnectionPoolSize(50)
					.withConnectionAcquisitionTimeout(2, TimeUnit.MINUTES)
					.build());

	static TreeDOCImpl<Character, Integer> treeDOC = new TreeDOCImpl<>(new Neo4jDocumentsAtomBuffer(driver, 123));

	public static void main(String[] args) {
//		try (var session = driver.session()) {
//			var greeting = session.executeWrite(tx -> {
//				var query = new Query("CREATE (a:Greeting) SET a.message = $message RETURN a.message + ', from node ' + id(a)",
//								parameters("message", "Hi"));
//				var result = tx.run(query);
//				return result.single().get(0).asString();
//			});
//			System.out.println(greeting);
//		}

		// User 1 inserted first character
		var aPath = treeDOC.insertBetween(null, null, 'A', USER_1);
		expectContent(List.of('A'));

		// User 2 inserted second character before A
		var bPath = treeDOC.insertBetween(null, aPath, 'B', USER_2);
		expectContent(List.of('B', 'A'));

		// Users 1 and 3 concurrently inserted characters between B and A
		var cPath = treeDOC.insertBetween(bPath, aPath, 'C', USER_3);
		var dPath = treeDOC.insertBetween(bPath, aPath, 'D', USER_1);
		expectContent(List.of('B', 'D', 'C', 'A'));

		// Users 1 and 3 concurrently inserted character between D and C, operations was executed in different order
		var ePath = treeDOC.insertBetween(dPath, cPath, 'E', USER_1);
		var fPath = treeDOC.insertBetween(dPath, cPath, 'F', USER_3);

		// Since disambiguator of User 1 is lower than disambiguator of User 2 character E is inserted before F
		expectContent(List.of('B', 'D', 'E', 'F', 'C', 'A'));

		// User 2 deletes character F while User 3 inserts new character between F and C
		treeDOC.delete(fPath);
		var gPath = treeDOC.insertBetween(fPath, cPath, 'G', USER_2);

		expectContent(List.of('B', 'D', 'E', 'G', 'C', 'A'));
	}

	private static void expectContent(List<Character> expectedContent) {
		System.out.println("Content = " + treeDOC.getEntries());

	}


}
