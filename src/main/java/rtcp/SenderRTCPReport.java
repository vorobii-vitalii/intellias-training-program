package rtcp;

import java.util.List;

public record SenderRTCPReport(SenderInfo senderInfo, List<ReportBlock> reportBlocks) implements RTCPReport {
	@Override
	public int getPayloadType() {
		return Constants.SENDER_REPORT_TYPE;
	}
}
