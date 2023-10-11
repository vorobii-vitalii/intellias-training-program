package sip.request_handling.register;

import sip.AddressOfRecord;

public record CreateBinding(AddressOfRecord bindingRecord, String callId, int commandSequence) {

}
