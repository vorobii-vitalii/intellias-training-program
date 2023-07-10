package rtcp;

public class SenderRTCPReportParser implements RTCPReportParser {
	@Override
	public RTCPReport parse(byte[] bytes, boolean isPadded, byte metadataBits) {
		return null;
	}

	@Override
	public byte getHandledReportType() {
		return Constants.SENDER_REPORT_TYPE;
	}

}
