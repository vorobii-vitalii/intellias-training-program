package rtcp;

public record ReportBlock(
		int synchronizationSource,
		byte fractionLost,
		int cumulativeNumberOfPacketsLost,
		int highestSequenceNumberReceived,
		int interarrivalJitter,
		int lastSR,
		int delaySinceLastSR
) {
}
