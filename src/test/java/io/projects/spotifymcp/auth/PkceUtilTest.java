package io.projects.spotifymcp.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceUtilTest {

    @Test
    void generatesUrlSafeVerifierOfSufficientLength() {
        String verifier = PkceUtil.generateRandomToken();

        assertThat(verifier).doesNotContain("+", "/", "=");
        assertThat(verifier.length()).isGreaterThanOrEqualTo(43); // RFC 7636 minimum
    }

    @Test
    void generatesDifferentVerifiersEachTime() {
        assertThat(PkceUtil.generateRandomToken()).isNotEqualTo(PkceUtil.generateRandomToken());
    }

    @Test
    void codeChallengeIsDeterministicForSameVerifier() {
        String verifier = "fixed-test-verifier-value-1234567890";

        String challenge1 = PkceUtil.deriveCodeChallenge(verifier);
        String challenge2 = PkceUtil.deriveCodeChallenge(verifier);

        assertThat(challenge1).isEqualTo(challenge2);
        assertThat(challenge1).doesNotContain("+", "/", "=");
    }

    @Test
    void differentVerifiersProduceDifferentChallenges() {
        String challengeA = PkceUtil.deriveCodeChallenge("verifier-a");
        String challengeB = PkceUtil.deriveCodeChallenge("verifier-b");

        assertThat(challengeA).isNotEqualTo(challengeB);
    }
}
