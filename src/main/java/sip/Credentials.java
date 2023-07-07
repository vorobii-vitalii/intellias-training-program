package sip;

public record Credentials(String username, String password) {
	public String asString() {
		return username == null
				? ""
				: (username + (password == null ? "" : ":" + password) + "@");
	}
}
