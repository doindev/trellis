package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractDocumentLoaderNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GitHub Document Loader — fetches files from a GitHub repository and loads them
 * as LangChain4j Documents. Supports loading individual files or all files in a
 * directory path.
 */
@Slf4j
@Node(
		type = "documentGithubLoader",
		displayName = "GitHub Document Loader",
		description = "Load files from a GitHub repository as AI documents",
		category = "AI / Document Loaders",
		icon = "github",
		credentials = {"githubApi"}
)
public class GitHubDocumentLoaderNode extends AbstractDocumentLoaderNode {

	private static final String GITHUB_API_BASE = "https://api.github.com";

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(30))
			.build();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@SuppressWarnings("unchecked")
	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String token = context.getCredentialString("apiKey", "");
		String owner = context.getParameter("owner", "");
		String repo = context.getParameter("repo", "");
		String branch = context.getParameter("branch", "main");
		String path = context.getParameter("path", "");

		if (owner.isBlank() || repo.isBlank()) {
			throw new IllegalArgumentException("Owner and repository are required");
		}

		String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/contents/"
				+ (path.startsWith("/") ? path.substring(1) : path);
		if (branch != null && !branch.isBlank()) {
			url += "?ref=" + branch;
		}

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.header("Accept", "application/vnd.github.v3+json")
				.timeout(Duration.ofSeconds(60));

		if (token != null && !token.isBlank()) {
			requestBuilder.header("Authorization", "Bearer " + token);
		}

		HttpResponse<String> response = httpClient.send(requestBuilder.build(),
				HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			throw new RuntimeException("GitHub API returned status " + response.statusCode()
					+ ": " + response.body());
		}

		List<Document> documents = new ArrayList<>();
		String body = response.body().trim();

		if (body.startsWith("[")) {
			// Directory listing — fetch each file
			List<Map<String, Object>> entries = objectMapper.readValue(body,
					new TypeReference<List<Map<String, Object>>>() {});
			for (Map<String, Object> entry : entries) {
				String type = (String) entry.get("type");
				if ("file".equals(type)) {
					String downloadUrl = (String) entry.get("download_url");
					String filePath = (String) entry.get("path");
					String fileName = (String) entry.get("name");
					if (downloadUrl != null) {
						try {
							String content = fetchFileContent(downloadUrl, token);
							if (content != null && !content.isBlank()) {
								Metadata metadata = new Metadata();
								metadata.put("source", "github");
								metadata.put("repository", owner + "/" + repo);
								metadata.put("branch", branch);
								metadata.put("path", filePath);
								metadata.put("fileName", fileName);
								documents.add(Document.from(content, metadata));
							}
						} catch (Exception e) {
							log.warn("Failed to fetch file {}: {}", filePath, e.getMessage());
						}
					}
				}
			}
		} else {
			// Single file
			Map<String, Object> fileData = objectMapper.readValue(body,
					new TypeReference<Map<String, Object>>() {});
			String encoding = (String) fileData.get("encoding");
			String content;
			if ("base64".equals(encoding)) {
				String encoded = (String) fileData.get("content");
				content = new String(Base64.getMimeDecoder().decode(encoded));
			} else {
				String downloadUrl = (String) fileData.get("download_url");
				content = downloadUrl != null ? fetchFileContent(downloadUrl, token) : "";
			}

			if (content != null && !content.isBlank()) {
				String filePath = (String) fileData.get("path");
				String fileName = (String) fileData.get("name");
				Metadata metadata = new Metadata();
				metadata.put("source", "github");
				metadata.put("repository", owner + "/" + repo);
				metadata.put("branch", branch);
				metadata.put("path", filePath != null ? filePath : path);
				metadata.put("fileName", fileName != null ? fileName : path);
				documents.add(Document.from(content, metadata));
			}
		}

		return documents;
	}

	private String fetchFileContent(String url, String token) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(60));

		if (token != null && !token.isBlank()) {
			builder.header("Authorization", "Bearer " + token);
		}

		HttpResponse<String> response = httpClient.send(builder.build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 200) {
			return response.body();
		}
		throw new RuntimeException("Failed to fetch file content: HTTP " + response.statusCode());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("owner").displayName("Repository Owner")
						.type(ParameterType.STRING)
						.defaultValue("")
						.required(true)
						.placeHolder("octocat")
						.description("GitHub repository owner or organization").build(),
				NodeParameter.builder()
						.name("repo").displayName("Repository")
						.type(ParameterType.STRING)
						.defaultValue("")
						.required(true)
						.placeHolder("hello-world")
						.description("GitHub repository name").build(),
				NodeParameter.builder()
						.name("branch").displayName("Branch")
						.type(ParameterType.STRING)
						.defaultValue("main")
						.description("Branch, tag, or commit SHA to fetch from").build(),
				NodeParameter.builder()
						.name("path").displayName("Path")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("docs/README.md")
						.description("File or directory path within the repository. "
								+ "If a directory is specified, all files in it will be loaded.").build()
		);
	}
}
