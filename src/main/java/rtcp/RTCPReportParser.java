package rtcp;

/**
 * Should be stateless
 */
public interface RTCPReportParser {
	/**
	 * Parses report
	 * @param bytes - bytes, allocated for report
	 * @param isPadded - true whether padding enabled, false otherwise
	 * @param metadataBits - 5 "metadata" bits, interpretation depends on report type (can be subtype, SSRC etc.)
	 */
	RTCPReport parse(byte[] bytes, boolean isPadded, byte metadataBits);

	byte getHandledReportType();
}
