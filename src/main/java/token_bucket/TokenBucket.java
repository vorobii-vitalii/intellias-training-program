package token_bucket;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class TokenBucket<Identifier> {
	private final AtomicInteger tokens;
	private final int maxTokens;
	private final Identifier identifier;

	public TokenBucket(int maxTokens, int initialTokens, Identifier identifier) {
		this.maxTokens = maxTokens;
		this.tokens = new AtomicInteger(initialTokens);
		this.identifier = identifier;
	}

	public void fill(int tokensToAdd) {
		tokens.updateAndGet(t -> Math.min(maxTokens, t + tokensToAdd));
	}

	public int getMaxTokens() {
		return maxTokens;
	}

	public boolean takeToken() {
		return tokens.getAndUpdate(t -> Math.max(0, t - 1)) > 0;
	}

	@Override
	public boolean equals(Object obj) {
		return Objects.equals(identifier, ((TokenBucket<?>) obj).identifier);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(identifier);
	}
}
