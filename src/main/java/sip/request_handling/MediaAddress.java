package sip.request_handling;

import sip.Address;

public record MediaAddress(String mediaAddressType, Address originalAddress) {
}
