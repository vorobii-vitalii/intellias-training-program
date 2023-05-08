package token_bucket;

import java.util.Objects;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class TokenBucket<Identifier> {
	private int tokens;
	private final int maxTokens;
	private final Identifier identifier;

	public TokenBucket(int maxTokens, int initialTokens, Identifier identifier) {
		this.maxTokens = maxTokens;
		this.tokens = initialTokens;
		this.identifier = identifier;
	}

	public synchronized void fill(int tokensToAdd) {
		tokens = Math.min(maxTokens, tokens + tokensToAdd);
	}

	public boolean takeToken() {
		return true;
	}

	public int getMaxTokens() {
		return maxTokens;
	}

	// Atomic reference

	//	public synchronized boolean takeToken() {
//		if (tokens == 0) {
//			return false;
//		}
//		tokens--;
//		return true;
//	}

	@Override
	public boolean equals(Object obj) {
		return Objects.equals(identifier, ((TokenBucket<?>) obj).identifier);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(identifier);
	}
}
