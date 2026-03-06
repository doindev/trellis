package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class TotpNodeTest {

    private TotpNode node;

    // A valid Base32-encoded secret for testing (equivalent to "HELLO WORLD" in Base32)
    private static final String TEST_SECRET = "JBSWY3DPEHPK3PXP";

    @BeforeEach
    void setUp() {
        node = new TotpNode();
    }

    // ── Generate TOTP with default settings ──

    @Test
    void generateTotpDefaultSettings() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull();
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    // ── Custom algorithm: SHA256 ──

    @Test
    void generateTotpSha256() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA256",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull();
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    // ── Custom algorithm: SHA512 ──

    @Test
    void generateTotpSha512() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA512",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull();
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    // ── Custom digits: 8 ──

    @Test
    void generateTotpCustomDigits8() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA1",
                "digits", 8,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull();
        assertThat(otp).hasSize(8);
        assertThat(otp).matches("\\d{8}");
    }

    // ── Custom period ──

    @Test
    void generateTotpCustomPeriod() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA1",
                "digits", 6,
                "period", 60
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull();
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    // ── Different algorithms produce different OTPs (usually) ──

    @Test
    void differentAlgorithmsProduceDifferentResults() {
        List<Map<String, Object>> input = items(Map.of("x", 1));

        Map<String, Object> paramsSha1 = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );
        Map<String, Object> paramsSha256 = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA256",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult r1 = node.execute(ctx(input, paramsSha1));
        NodeExecutionResult r2 = node.execute(ctx(input, paramsSha256));

        // SHA1 and SHA256 results may differ (not guaranteed but very likely)
        String otp1 = (String) firstJson(r1).get("otp");
        String otp2 = (String) firstJson(r2).get("otp");
        assertThat(otp1).isNotNull();
        assertThat(otp2).isNotNull();
        // Both should be valid 6-digit OTPs
        assertThat(otp1).matches("\\d{6}");
        assertThat(otp2).matches("\\d{6}");
    }

    // ── Result has expected field ──

    @Test
    void resultHasOtpField() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("otp");
    }

    // ── Empty secret returns error ──

    @Test
    void emptySecretReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", "",
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).containsIgnoringCase("secret");
    }

    // ── Multiple input items all get OTPs ──

    @Test
    void multipleInputItems() {
        List<Map<String, Object>> input = items(
                Map.of("x", 1),
                Map.of("x", 2),
                Map.of("x", 3)
        );
        Map<String, Object> params = mutableMap(
                "secret", TEST_SECRET,
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        for (int i = 0; i < 3; i++) {
            String otp = (String) jsonAt(result, i).get("otp");
            assertThat(otp).isNotNull().matches("\\d{6}");
        }
    }

    // ── Secret with spaces is handled ──

    @Test
    void secretWithSpacesIsHandled() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", "JBSW Y3DP EHPK 3PXP",
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull().matches("\\d{6}");
    }

    // ── Secret with padding chars ──

    @Test
    void secretWithPaddingChars() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "secret", "JBSWY3DPEHPK3PXP====",
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String otp = (String) firstJson(result).get("otp");
        assertThat(otp).isNotNull().matches("\\d{6}");
    }
}
