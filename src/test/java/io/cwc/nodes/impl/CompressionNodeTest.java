package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class CompressionNodeTest {

    private CompressionNode node;

    @BeforeEach
    void setUp() {
        node = new CompressionNode();
    }

    // -- Helper to build input items with binary data --

    private List<Map<String, Object>> binaryInput(String base64Data, String fileName, String mimeType) {
        Map<String, Object> binaryFile = new HashMap<>();
        binaryFile.put("data", base64Data);
        binaryFile.put("fileName", fileName);
        binaryFile.put("mimeType", mimeType);

        Map<String, Object> binary = new HashMap<>();
        binary.put("data", binaryFile);

        Map<String, Object> item = new HashMap<>();
        item.put("json", Map.of());
        item.put("binary", binary);

        return List.of(item);
    }

    private List<Map<String, Object>> binaryInputCustomField(String base64Data, String fileName,
                                                              String mimeType, String fieldName) {
        Map<String, Object> binaryFile = new HashMap<>();
        binaryFile.put("data", base64Data);
        binaryFile.put("fileName", fileName);
        binaryFile.put("mimeType", mimeType);

        Map<String, Object> binary = new HashMap<>();
        binary.put(fieldName, binaryFile);

        Map<String, Object> item = new HashMap<>();
        item.put("json", Map.of());
        item.put("binary", binary);

        return List.of(item);
    }

    // ── Compress with gzip produces gzip output ──

    @Test
    void compressWithGzipProducesGzipOutput() {
        String originalData = "hello world";
        String base64Data = Base64.getEncoder().encodeToString(originalData.getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "test.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "gzip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        assertThat(json).containsKey("binary");

        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        assertThat(binary).containsKey("data");

        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");
        assertThat(outFile.get("data")).isNotNull();
        assertThat((String) outFile.get("data")).isNotEmpty();
        assertThat(outFile.get("mimeType")).isEqualTo("application/gzip");
    }

    // ── Compress with zip produces zip output ──

    @Test
    void compressWithZipProducesZipOutput() {
        String originalData = "hello world";
        String base64Data = Base64.getEncoder().encodeToString(originalData.getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "test.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "zip",
                "fileName", "archive.zip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");

        assertThat(outFile.get("mimeType")).isEqualTo("application/zip");
        assertThat(outFile.get("fileName")).isEqualTo("archive.zip");
    }

    // ── Decompress gzip roundtrip ──

    @Test
    void decompressGzipRoundtrip() throws Exception {
        String originalData = "hello world roundtrip test";

        // First compress with gzip
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(originalData.getBytes());
        }
        String compressedBase64 = Base64.getEncoder().encodeToString(bos.toByteArray());

        List<Map<String, Object>> input = binaryInput(compressedBase64, "test.txt.gz", "application/gzip");
        Map<String, Object> params = mutableMap(
                "operation", "decompress",
                "binaryPropertyName", "data",
                "outputPrefix", "file_"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("file_0");

        assertThat(outFile).isNotNull();
        String decompressedBase64 = (String) outFile.get("data");
        byte[] decompressed = Base64.getDecoder().decode(decompressedBase64);
        assertThat(new String(decompressed)).isEqualTo(originalData);
    }

    // ── Decompress zip roundtrip ──

    @Test
    void decompressZipRoundtrip() throws Exception {
        String originalData = "zip roundtrip content";

        // Create a zip archive
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry entry = new ZipEntry("inner.txt");
            zos.putNextEntry(entry);
            zos.write(originalData.getBytes());
            zos.closeEntry();
        }
        String compressedBase64 = Base64.getEncoder().encodeToString(bos.toByteArray());

        List<Map<String, Object>> input = binaryInput(compressedBase64, "archive.zip", "application/zip");
        Map<String, Object> params = mutableMap(
                "operation", "decompress",
                "binaryPropertyName", "data",
                "outputPrefix", "file_"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("file_0");

        assertThat(outFile).isNotNull();
        String decompressedBase64 = (String) outFile.get("data");
        byte[] decompressed = Base64.getDecoder().decode(decompressedBase64);
        assertThat(new String(decompressed)).isEqualTo(originalData);
        assertThat(outFile.get("fileName")).isEqualTo("inner.txt");
    }

    // ── Output has correct mimeType for gzip ──

    @Test
    void gzipOutputHasCorrectMimeType() {
        String base64Data = Base64.getEncoder().encodeToString("test data".getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "test.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "gzip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");

        assertThat(outFile.get("mimeType")).isEqualTo("application/gzip");
    }

    // ── Output has correct mimeType for zip ──

    @Test
    void zipOutputHasCorrectMimeType() {
        String base64Data = Base64.getEncoder().encodeToString("test data".getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "test.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "zip",
                "fileName", "out.zip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");

        assertThat(outFile.get("mimeType")).isEqualTo("application/zip");
    }

    // ── Output has correct fileName for gzip ──

    @Test
    void gzipOutputHasCorrectFileName() {
        String base64Data = Base64.getEncoder().encodeToString("test".getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "myfile.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "gzip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");

        assertThat(outFile.get("fileName")).isEqualTo("myfile.txt.gz");
    }

    // ── Output has correct fileName for zip ──

    @Test
    void zipOutputHasCorrectFileName() {
        String base64Data = Base64.getEncoder().encodeToString("test".getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "myfile.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "zip",
                "fileName", "custom-archive.zip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");

        assertThat(outFile.get("fileName")).isEqualTo("custom-archive.zip");
    }

    // ── Full compress then decompress roundtrip via the node ──

    @Test
    void fullGzipCompressDecompressRoundtripViaNode() {
        String originalText = "full roundtrip through the compression node";
        String base64Data = Base64.getEncoder().encodeToString(originalText.getBytes());

        // Step 1: compress with gzip
        List<Map<String, Object>> compressInput = binaryInput(base64Data, "test.txt", "text/plain");
        Map<String, Object> compressParams = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "gzip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult compressResult = node.execute(ctx(compressInput, compressParams));

        @SuppressWarnings("unchecked")
        Map<String, Object> compressedJson = firstJson(compressResult);
        @SuppressWarnings("unchecked")
        Map<String, Object> compressedBinary = (Map<String, Object>) compressedJson.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> compressedFile = (Map<String, Object>) compressedBinary.get("data");
        String compressedBase64 = (String) compressedFile.get("data");

        // Step 2: decompress
        List<Map<String, Object>> decompressInput = binaryInput(
                compressedBase64, "test.txt.gz", "application/gzip");
        Map<String, Object> decompressParams = mutableMap(
                "operation", "decompress",
                "binaryPropertyName", "data",
                "outputPrefix", "file_"
        );

        NodeExecutionResult decompressResult = node.execute(ctx(decompressInput, decompressParams));

        @SuppressWarnings("unchecked")
        Map<String, Object> decompressedJson = firstJson(decompressResult);
        @SuppressWarnings("unchecked")
        Map<String, Object> decompressedBinary = (Map<String, Object>) decompressedJson.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> decompressedFile = (Map<String, Object>) decompressedBinary.get("file_0");

        String decompressedBase64 = (String) decompressedFile.get("data");
        byte[] decompressed = Base64.getDecoder().decode(decompressedBase64);
        assertThat(new String(decompressed)).isEqualTo(originalText);
    }

    @Test
    void fullZipCompressDecompressRoundtripViaNode() {
        String originalText = "zip roundtrip through node";
        String base64Data = Base64.getEncoder().encodeToString(originalText.getBytes());

        // Step 1: compress with zip
        List<Map<String, Object>> compressInput = binaryInput(base64Data, "test.txt", "text/plain");
        Map<String, Object> compressParams = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "zip",
                "fileName", "archive.zip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult compressResult = node.execute(ctx(compressInput, compressParams));

        @SuppressWarnings("unchecked")
        Map<String, Object> compressedJson = firstJson(compressResult);
        @SuppressWarnings("unchecked")
        Map<String, Object> compressedBinary = (Map<String, Object>) compressedJson.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> compressedFile = (Map<String, Object>) compressedBinary.get("data");
        String compressedBase64 = (String) compressedFile.get("data");

        // Step 2: decompress
        List<Map<String, Object>> decompressInput = binaryInput(
                compressedBase64, "archive.zip", "application/zip");
        Map<String, Object> decompressParams = mutableMap(
                "operation", "decompress",
                "binaryPropertyName", "data",
                "outputPrefix", "file_"
        );

        NodeExecutionResult decompressResult = node.execute(ctx(decompressInput, decompressParams));

        @SuppressWarnings("unchecked")
        Map<String, Object> decompressedJson = firstJson(decompressResult);
        @SuppressWarnings("unchecked")
        Map<String, Object> decompressedBinary = (Map<String, Object>) decompressedJson.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> decompressedFile = (Map<String, Object>) decompressedBinary.get("file_0");

        String decompressedBase64 = (String) decompressedFile.get("data");
        byte[] decompressed = Base64.getDecoder().decode(decompressedBase64);
        assertThat(new String(decompressed)).isEqualTo(originalText);
    }

    // ── Compressed output contains fileSize ──

    @Test
    void compressedOutputContainsFileSize() {
        String base64Data = Base64.getEncoder().encodeToString("size check".getBytes());
        List<Map<String, Object>> input = binaryInput(base64Data, "test.txt", "text/plain");

        Map<String, Object> params = mutableMap(
                "operation", "compress",
                "binaryPropertyName", "data",
                "outputFormat", "gzip",
                "outputBinaryPropertyName", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> json = firstJson(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> binary = (Map<String, Object>) json.get("binary");
        @SuppressWarnings("unchecked")
        Map<String, Object> outFile = (Map<String, Object>) binary.get("data");

        assertThat(outFile).containsKey("fileSize");
        assertThat((int) outFile.get("fileSize")).isGreaterThan(0);
    }

    // ── Node has parameters ──

    @Test
    void hasParameters() {
        assertThat(node.getParameters()).isNotEmpty();
        assertThat(node.getParameters()).extracting("name")
                .contains("operation", "binaryPropertyName", "outputFormat");
    }
}
