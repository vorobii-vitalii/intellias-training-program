package sip.reactor_netty.request_handling;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;
import sip.request_handling.invite.MediaConferenceService;

@ExtendWith(MockitoExtension.class)
class TestLeaveConferenceReactiveSipRequestHandler {

	@Mock
	MediaConferenceService mediaConferenceService;
	@Mock
	ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;
	@InjectMocks
	LeaveConferenceReactiveSipRequestHandler requestHandler;

	@Test
	void handleMessage() {

	}

	@Test
	void getHandledMessageType() {
	}
}