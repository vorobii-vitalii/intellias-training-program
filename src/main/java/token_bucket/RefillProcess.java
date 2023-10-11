package token_bucket;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefillProcess<T> implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(RefillProcess.class);

	private final Set<TokenBucket<T>> tokenBuckets;

	public RefillProcess(Set<TokenBucket<T>> tokenBuckets) {
		this.tokenBuckets = tokenBuckets;
	}

	@Override
	public void run() {
		tokenBuckets.forEach(tokenBucket -> tokenBucket.fill(tokenBucket.getMaxTokens()));
	}
}
