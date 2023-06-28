package sip;

public record Address(String host, Integer port) {

	public static Address parse(String str) {
		var arr = str.trim().split(":");
		var host = arr[0].trim();
		return new Address(host, arr.length > 1 ? Integer.parseInt(arr[1]) : null);
	}

}
