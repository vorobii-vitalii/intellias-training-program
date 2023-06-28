package sip;

public record CommandSequence(int sequenceNumber, String commandName) {

	public static CommandSequence parse(CharSequence charSequence) {
		var arr = charSequence.toString().trim().split("\\s+");
		return new CommandSequence(Integer.parseInt(arr[0]), arr[1]);
	}

}
