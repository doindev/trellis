package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.util.*;

/**
 * AWS Transcribe — create, manage, and retrieve transcription jobs via Amazon Transcribe.
 */
@Node(
		type = "awsTranscribe",
		displayName = "AWS Transcribe",
		description = "Create and manage transcription jobs",
		category = "AWS Services",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsTranscribeNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String operation = context.getParameter("operation", "create");

		TranscribeClient client = TranscribeClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> handleCreate(context, client);
					case "delete" -> handleDelete(context, client);
					case "get" -> handleGet(context, client);
					case "getMany" -> handleGetMany(context, client);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		client.close();
		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleCreate(NodeExecutionContext context, TranscribeClient client) {
		String jobName = context.getParameter("transcriptionJobName", "");
		String mediaFileUri = context.getParameter("mediaFileUri", "");
		boolean identifyLanguage = toBoolean(context.getParameters().get("identifyLanguage"), false);
		String languageCode = context.getParameter("languageCode", "en-US");

		// Optional settings
		boolean channelIdentification = toBoolean(context.getParameters().get("channelIdentification"), false);
		boolean showSpeakerLabels = toBoolean(context.getParameters().get("showSpeakerLabels"), false);
		int maxSpeakerLabels = toInt(context.getParameters().get("maxSpeakerLabels"), 2);
		boolean showAlternatives = toBoolean(context.getParameters().get("showAlternatives"), false);
		int maxAlternatives = toInt(context.getParameters().get("maxAlternatives"), 2);
		String vocabularyName = context.getParameter("vocabularyName", "");
		String vocabularyFilterName = context.getParameter("vocabularyFilterName", "");
		String vocabularyFilterMethod = context.getParameter("vocabularyFilterMethod", "");

		StartTranscriptionJobRequest.Builder builder = StartTranscriptionJobRequest.builder()
				.transcriptionJobName(jobName)
				.media(Media.builder().mediaFileUri(mediaFileUri).build());

		if (identifyLanguage) {
			builder.identifyLanguage(true);
		} else {
			builder.languageCode(languageCode);
		}

		Settings.Builder settingsBuilder = Settings.builder();
		boolean hasSettings = false;

		if (channelIdentification) {
			settingsBuilder.channelIdentification(true);
			hasSettings = true;
		}
		if (showSpeakerLabels) {
			settingsBuilder.showSpeakerLabels(true);
			settingsBuilder.maxSpeakerLabels(maxSpeakerLabels);
			hasSettings = true;
		}
		if (showAlternatives) {
			settingsBuilder.showAlternatives(true);
			settingsBuilder.maxAlternatives(maxAlternatives);
			hasSettings = true;
		}
		if (!vocabularyName.isBlank()) {
			settingsBuilder.vocabularyName(vocabularyName);
			hasSettings = true;
		}
		if (!vocabularyFilterName.isBlank()) {
			settingsBuilder.vocabularyFilterName(vocabularyFilterName);
			hasSettings = true;
		}
		if (!vocabularyFilterMethod.isBlank()) {
			settingsBuilder.vocabularyFilterMethod(vocabularyFilterMethod);
			hasSettings = true;
		}

		if (hasSettings) {
			builder.settings(settingsBuilder.build());
		}

		StartTranscriptionJobResponse response = client.startTranscriptionJob(builder.build());
		TranscriptionJob job = response.transcriptionJob();

		return transcriptionJobToMap(job);
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context, TranscribeClient client) {
		String jobName = context.getParameter("transcriptionJobName", "");
		client.deleteTranscriptionJob(DeleteTranscriptionJobRequest.builder()
				.transcriptionJobName(jobName).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("transcriptionJobName", jobName);
		return result;
	}

	private Map<String, Object> handleGet(NodeExecutionContext context, TranscribeClient client) {
		String jobName = context.getParameter("transcriptionJobName", "");

		GetTranscriptionJobResponse response = client.getTranscriptionJob(
				GetTranscriptionJobRequest.builder()
						.transcriptionJobName(jobName).build());

		return transcriptionJobToMap(response.transcriptionJob());
	}

	private Map<String, Object> handleGetMany(NodeExecutionContext context, TranscribeClient client) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		String statusFilter = context.getParameter("statusFilter", "");
		String jobNameContains = context.getParameter("jobNameContains", "");

		ListTranscriptionJobsRequest.Builder builder = ListTranscriptionJobsRequest.builder();
		if (!statusFilter.isBlank()) {
			builder.status(statusFilter);
		}
		if (!jobNameContains.isBlank()) {
			builder.jobNameContains(jobNameContains);
		}
		if (!returnAll) {
			builder.maxResults(limit);
		}

		List<Map<String, Object>> jobs = new ArrayList<>();
		String nextToken = null;
		do {
			if (nextToken != null) {
				builder.nextToken(nextToken);
			}
			ListTranscriptionJobsResponse response = client.listTranscriptionJobs(builder.build());
			for (TranscriptionJobSummary summary : response.transcriptionJobSummaries()) {
				Map<String, Object> job = new LinkedHashMap<>();
				job.put("transcriptionJobName", summary.transcriptionJobName());
				job.put("status", summary.transcriptionJobStatusAsString());
				job.put("languageCode", summary.languageCode() != null ? summary.languageCode().toString() : null);
				job.put("creationTime", summary.creationTime() != null ? summary.creationTime().toString() : null);
				job.put("completionTime", summary.completionTime() != null ? summary.completionTime().toString() : null);
				jobs.add(job);
				if (!returnAll && jobs.size() >= limit) break;
			}
			nextToken = response.nextToken();
		} while (returnAll && nextToken != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("transcriptionJobs", jobs);
		return result;
	}

	private Map<String, Object> transcriptionJobToMap(TranscriptionJob job) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("transcriptionJobName", job.transcriptionJobName());
		result.put("transcriptionJobStatus", job.transcriptionJobStatusAsString());
		result.put("languageCode", job.languageCode() != null ? job.languageCode().toString() : null);
		result.put("mediaFormat", job.mediaFormatAsString());
		result.put("creationTime", job.creationTime() != null ? job.creationTime().toString() : null);
		result.put("completionTime", job.completionTime() != null ? job.completionTime().toString() : null);
		if (job.transcript() != null) {
			result.put("transcriptFileUri", job.transcript().transcriptFileUri());
		}
		if (job.media() != null) {
			result.put("mediaFileUri", job.media().mediaFileUri());
		}
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build()
						)).build(),
				NodeParameter.builder()
						.name("transcriptionJobName").displayName("Transcription Job Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("A unique name for the transcription job.").build(),
				NodeParameter.builder()
						.name("mediaFileUri").displayName("Media File URI")
						.type(ParameterType.STRING).defaultValue("")
						.description("S3 URI of the media file (e.g., s3://bucket/file.mp3).").build(),
				NodeParameter.builder()
						.name("identifyLanguage").displayName("Identify Language")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Auto-detect the language instead of specifying.").build(),
				NodeParameter.builder()
						.name("languageCode").displayName("Language Code")
						.type(ParameterType.OPTIONS)
						.defaultValue("en-US")
						.options(List.of(
								ParameterOption.builder().name("English (US)").value("en-US").build(),
								ParameterOption.builder().name("English (UK)").value("en-GB").build(),
								ParameterOption.builder().name("English (AU)").value("en-AU").build(),
								ParameterOption.builder().name("Spanish (US)").value("es-US").build(),
								ParameterOption.builder().name("Spanish (ES)").value("es-ES").build(),
								ParameterOption.builder().name("French (FR)").value("fr-FR").build(),
								ParameterOption.builder().name("French (CA)").value("fr-CA").build(),
								ParameterOption.builder().name("German").value("de-DE").build(),
								ParameterOption.builder().name("Italian").value("it-IT").build(),
								ParameterOption.builder().name("Portuguese (BR)").value("pt-BR").build(),
								ParameterOption.builder().name("Japanese").value("ja-JP").build(),
								ParameterOption.builder().name("Korean").value("ko-KR").build(),
								ParameterOption.builder().name("Chinese (Mandarin)").value("zh-CN").build()
						)).build(),
				NodeParameter.builder()
						.name("channelIdentification").displayName("Channel Identification")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Transcribe each audio channel separately.").build(),
				NodeParameter.builder()
						.name("showSpeakerLabels").displayName("Show Speaker Labels")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Identify different speakers in the audio.").build(),
				NodeParameter.builder()
						.name("maxSpeakerLabels").displayName("Max Speaker Labels")
						.type(ParameterType.NUMBER).defaultValue(2)
						.description("Maximum number of speakers to identify (2-10).").build(),
				NodeParameter.builder()
						.name("showAlternatives").displayName("Show Alternatives")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Generate alternative transcriptions.").build(),
				NodeParameter.builder()
						.name("maxAlternatives").displayName("Max Alternatives")
						.type(ParameterType.NUMBER).defaultValue(2)
						.description("Maximum number of alternatives (2-10).").build(),
				NodeParameter.builder()
						.name("vocabularyName").displayName("Vocabulary Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of a custom vocabulary to use.").build(),
				NodeParameter.builder()
						.name("vocabularyFilterName").displayName("Vocabulary Filter Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of a vocabulary filter to use.").build(),
				NodeParameter.builder()
						.name("vocabularyFilterMethod").displayName("Vocabulary Filter Method")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Mask").value("mask").build(),
								ParameterOption.builder().name("Tag").value("tag").build()
						)).build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build(),
				NodeParameter.builder()
						.name("statusFilter").displayName("Status Filter")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("All").value("").build(),
								ParameterOption.builder().name("Queued").value("QUEUED").build(),
								ParameterOption.builder().name("In Progress").value("IN_PROGRESS").build(),
								ParameterOption.builder().name("Completed").value("COMPLETED").build(),
								ParameterOption.builder().name("Failed").value("FAILED").build()
						)).build(),
				NodeParameter.builder()
						.name("jobNameContains").displayName("Job Name Contains")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter jobs by name substring.").build()
		);
	}
}
