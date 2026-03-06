package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class CryptoNodeTest {

    private CryptoNode node;

    @BeforeEach
    void setUp() {
        node = new CryptoNode();
    }

    // ── Hash: MD5 with hex encoding ──

    @Test
    void hashMd5Hex() {
        List<Map<String, Object>> input = items(Map.of("name", "alice"));
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "MD5",
                "value", "hello",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String hash = (String) firstJson(result).get("data");
        assertThat(hash).isNotNull().hasSize(32); // MD5 hex = 32 chars
        assertThat(hash).matches("[0-9a-f]{32}");
        // Known MD5 of "hello"
        assertThat(hash).isEqualTo("5d41402abc4b2a76b9719d911017c592");
        // Original fields preserved
        assertThat(firstJson(result)).containsEntry("name", "alice");
    }

    // ── Hash: SHA-256 with hex encoding ──

    @Test
    void hashSha256Hex() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "SHA-256",
                "value", "hello",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hash = (String) firstJson(result).get("data");
        assertThat(hash).isNotNull().hasSize(64); // SHA-256 hex = 64 chars
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    // ── Hash: SHA-512 with hex encoding ──

    @Test
    void hashSha512Hex() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "SHA-512",
                "value", "hello",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hash = (String) firstJson(result).get("data");
        assertThat(hash).isNotNull().hasSize(128); // SHA-512 hex = 128 chars
    }

    // ── Hash: SHA-256 with base64 encoding ──

    @Test
    void hashSha256Base64() throws Exception {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "SHA-256",
                "value", "hello",
                "encoding", "base64",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hash = (String) firstJson(result).get("data");
        assertThat(hash).isNotNull();
        // Verify it is valid base64
        byte[] decoded = Base64.getDecoder().decode(hash);
        assertThat(decoded).hasSize(32); // SHA-256 = 32 bytes

        // Compute the expected base64 value
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expected = digest.digest("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(hash).isEqualTo(Base64.getEncoder().encodeToString(expected));
    }

    // ── Hash: MD5 with base64 encoding ──

    @Test
    void hashMd5Base64() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "MD5",
                "value", "test",
                "encoding", "base64",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hash = (String) firstJson(result).get("data");
        assertThat(hash).isNotNull();
        byte[] decoded = Base64.getDecoder().decode(hash);
        assertThat(decoded).hasSize(16); // MD5 = 16 bytes
    }

    // ── Hash: custom property name ──

    @Test
    void hashCustomPropertyName() {
        List<Map<String, Object>> input = items(Map.of("name", "alice"));
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "SHA-256",
                "value", "hello",
                "encoding", "hex",
                "propertyName", "myHash"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("myHash");
        assertThat(firstJson(result)).doesNotContainKey("data");
    }

    // ── HMAC: SHA-256 with secret ──

    @Test
    void hmacSha256WithSecret() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "hmac",
                "hashType", "SHA-256",
                "value", "hello",
                "secret", "mysecret",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hmac = (String) firstJson(result).get("data");
        assertThat(hmac).isNotNull().hasSize(64); // HMAC-SHA256 hex = 64 chars
        assertThat(hmac).matches("[0-9a-f]{64}");
    }

    // ── HMAC: different secrets produce different results ──

    @Test
    void hmacDifferentSecretsProduceDifferentResults() {
        List<Map<String, Object>> input = items(Map.of("x", 1));

        Map<String, Object> params1 = mutableMap(
                "action", "hmac",
                "hashType", "SHA-256",
                "value", "hello",
                "secret", "secret1",
                "encoding", "hex",
                "propertyName", "data"
        );
        Map<String, Object> params2 = mutableMap(
                "action", "hmac",
                "hashType", "SHA-256",
                "value", "hello",
                "secret", "secret2",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result1 = node.execute(ctx(input, params1));
        NodeExecutionResult result2 = node.execute(ctx(input, params2));

        assertThat(firstJson(result1).get("data"))
                .isNotEqualTo(firstJson(result2).get("data"));
    }

    // ── HMAC: base64 encoding ──

    @Test
    void hmacBase64Encoding() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "hmac",
                "hashType", "SHA-256",
                "value", "hello",
                "secret", "key",
                "encoding", "base64",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hmac = (String) firstJson(result).get("data");
        assertThat(hmac).isNotNull();
        byte[] decoded = Base64.getDecoder().decode(hmac);
        assertThat(decoded).hasSize(32); // HMAC-SHA256 = 32 bytes
    }

    // ── Generate: UUID ──

    @Test
    void generateUuid() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "generate",
                "generateType", "uuid",
                "length", 32
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String uuid = (String) firstJson(result).get("data");
        assertThat(uuid).isNotNull();
        // UUID format: 8-4-4-4-12
        assertThat(uuid).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ── Generate: UUID is unique each time ──

    @Test
    void generateUuidIsUnique() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "generate",
                "generateType", "uuid",
                "length", 32
        );

        NodeExecutionResult r1 = node.execute(ctx(input, params));
        NodeExecutionResult r2 = node.execute(ctx(input, params));

        assertThat(firstJson(r1).get("data")).isNotEqualTo(firstJson(r2).get("data"));
    }

    // ── Generate: password ──

    @Test
    void generatePassword() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "generate",
                "generateType", "password",
                "length", 16
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String password = (String) firstJson(result).get("data");
        assertThat(password).isNotNull().hasSize(16);
    }

    // ── Generate: password with different lengths ──

    @Test
    void generatePasswordCustomLength() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "generate",
                "generateType", "password",
                "length", 64
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String password = (String) firstJson(result).get("data");
        assertThat(password).hasSize(64);
    }

    // ── Generate: hex ──

    @Test
    void generateHex() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "action", "generate",
                "generateType", "hex",
                "length", 16
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String hex = (String) firstJson(result).get("data");
        assertThat(hex).isNotNull().hasSize(32); // 16 bytes = 32 hex chars
        assertThat(hex).matches("[0-9a-f]{32}");
    }

    // ── Empty input: node still produces output ──

    @Test
    void emptyInputStillProducesOutput() {
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "SHA-256",
                "value", "hello",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        // CryptoNode defaults to a single empty-json item when input is empty
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("data");
    }

    // ── Multiple input items ──

    @Test
    void hashMultipleItems() {
        List<Map<String, Object>> input = items(
                Map.of("name", "alice"),
                Map.of("name", "bob")
        );
        Map<String, Object> params = mutableMap(
                "action", "hash",
                "hashType", "MD5",
                "value", "test",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        // Both items get the same hash (since value is static "test")
        assertThat(jsonAt(result, 0).get("data")).isEqualTo(jsonAt(result, 1).get("data"));
        // Original data preserved
        assertThat(jsonAt(result, 0)).containsEntry("name", "alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "bob");
    }

    // ── Default action is hash ──

    @Test
    void defaultActionIsHash() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "value", "hello",
                "encoding", "hex",
                "propertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Default action is "hash" with default hashType "SHA-256"
        String hash = (String) firstJson(result).get("data");
        assertThat(hash).isNotNull().hasSize(64);
    }
}
