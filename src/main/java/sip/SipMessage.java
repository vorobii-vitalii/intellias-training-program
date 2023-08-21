package sip;

import util.Serializable;

public sealed interface SipMessage extends Serializable permits SipRequest, SipResponse {
}
