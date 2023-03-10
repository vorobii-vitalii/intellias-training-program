package tcp;

import java.io.IOException;

public class RetryableTCPClient implements TCPClient {
	private final TCPClient tcpClient;
	private final int numAttempts;
	private final int waitBetweenAttempts;

	public RetryableTCPClient(TCPClient tcpClient, int numAttempts, int waitBetweenAttempts) {
		this.tcpClient = tcpClient;
		this.numAttempts = numAttempts;
		this.waitBetweenAttempts = waitBetweenAttempts;
	}

	@Override
	public TCPConnection createConnection() throws IOException {
		for (var i = 0; i < numAttempts; i++) {
			try {
				return tcpClient.createConnection();
			} catch (Exception error) {
				error.printStackTrace();
			}
			if (i != numAttempts - 1) {
				System.out.println("Connection creation failed, retrying...");
				try {
					Thread.sleep(waitBetweenAttempts);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		throw new IOException("Connection couldn't be established after " + numAttempts + " attempts");
	}

}
