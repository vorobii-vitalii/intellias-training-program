package sip;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SipParseUtils {

	private SipParseUtils() {
		// Utility classes
	}

	public static Map<String, String> parseParameters(String str, String delimiter) {
		if (str == null || str.isEmpty()) {
			return Collections.emptyMap();
		}
		var parametersArr = str.split(delimiter);
		var parameters = new LinkedHashMap<String, String>();
		for (var param : parametersArr) {
			if (param.isEmpty()) {
				continue;
			}
			var paramComponents = param.split("=", 2);
			var paramName = paramComponents[0].trim();
			if (paramName.isEmpty()) {
				continue;
			}
			var paramValue = paramComponents.length > 1 ? paramComponents[1].trim() : "";
			parameters.put(paramName, paramValue);
		}
		return parameters;
	}

}
