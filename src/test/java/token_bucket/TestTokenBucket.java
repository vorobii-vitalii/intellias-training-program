package token_bucket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestTokenBucket {
	private static final int MAX_TOKENS = 2;
	private static final int INITIAL_TOKENS = 2;
	private static final int ID = 1;

	TokenBucket<Integer> tokenBucket = new TokenBucket<>(MAX_TOKENS, INITIAL_TOKENS, ID);

	@Test
	void fillGivenDeltaPlusCurrentTokensIsHigherCapacity() {
		tokenBucket.takeToken();
		tokenBucket.fill(MAX_TOKENS);
		assertThat(tokenBucket.getTokens()).isEqualTo(MAX_TOKENS);
	}

	@Test
	void fillGivenDeltaPlusCurrentTokensIsLowerCapacity() {
		tokenBucket.takeToken();
		tokenBucket.takeToken();
		tokenBucket.fill(1);
		assertThat(tokenBucket.getTokens()).isEqualTo(1);
	}

	@Test
	void getMaxTokens() {
		assertThat(tokenBucket.getMaxTokens()).isEqualTo(MAX_TOKENS);
	}

	@Test
	void takeToken() {
		assertThat(tokenBucket.takeToken()).isTrue();
		assertThat(tokenBucket.takeToken()).isTrue();
		assertThat(tokenBucket.takeToken()).isFalse();
	}
}