package document_editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.treedoc.path.TreeDocPath;
import org.treedoc.utils.Pair;

public class PathUtils {

	private PathUtils() {
		// Utility classes should not be instantiated
	}

	public static  <T extends Comparable<T>> List<PairDTO<Boolean, T>> toPairs(TreeDocPath<T> path) {
		var pairs = new ArrayList<PairDTO<Boolean, T>>(path.length());
		for (var i = 0; i < path.length(); i++) {
			pairs.add(new PairDTO<>(path.isSet(i), path.disambiguatorAt(i)));
		}
		return pairs;
	}


}
