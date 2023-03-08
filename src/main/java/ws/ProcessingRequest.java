package ws;

import util.Serializable;

public abstract class ProcessingRequest<Request, Response extends Serializable> {
	private final Request request;

	public ProcessingRequest(Request request) {
		this.request = request;
	}

	public Request getRequest() {
		return request;
	}

	public abstract void onResponse(Response response);

}
