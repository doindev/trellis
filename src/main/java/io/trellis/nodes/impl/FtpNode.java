package io.trellis.nodes.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "ftp",
	displayName = "FTP",
	description = "Transfer files via FTP or SFTP.",
	category = "Core",
	icon = "globe",
	credentials = {"ftp", "sftp"}
)
public class FtpNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("protocol")
				.displayName("Protocol")
				.description("The file transfer protocol to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("ftp")
				.options(List.of(
					ParameterOption.builder().name("FTP").value("ftp").description("File Transfer Protocol").build(),
					ParameterOption.builder().name("SFTP").value("sftp").description("SSH File Transfer Protocol").build()
				))
				.build(),

			NodeParameter.builder()
				.name("operation")
				.displayName("Operation")
				.description("The operation to perform.")
				.type(ParameterType.OPTIONS)
				.defaultValue("list")
				.options(List.of(
					ParameterOption.builder().name("List").value("list")
						.description("List folder contents").action("List folder content").build(),
					ParameterOption.builder().name("Download").value("download")
						.description("Download a file").action("Download a file").build(),
					ParameterOption.builder().name("Upload").value("upload")
						.description("Upload a file").action("Upload a file").build(),
					ParameterOption.builder().name("Delete").value("delete")
						.description("Delete a file or folder").action("Delete a file or folder").build(),
					ParameterOption.builder().name("Rename").value("rename")
						.description("Rename or move a file or folder").action("Rename / move a file or folder").build()
				))
				.build(),

			// List parameters
			NodeParameter.builder()
				.name("path")
				.displayName("Path")
				.description("The path of the file or folder.")
				.type(ParameterType.STRING)
				.defaultValue("/")
				.displayOptions(Map.of("show", Map.of("operation", List.of("list", "download", "upload", "delete"))))
				.build(),

			NodeParameter.builder()
				.name("recursive")
				.displayName("Recursive")
				.description("List files recursively.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("operation", List.of("list"))))
				.build(),

			// Upload parameters
			NodeParameter.builder()
				.name("binaryData")
				.displayName("Binary Data")
				.description("Use binary data from the input.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("operation", List.of("upload"))))
				.build(),

			NodeParameter.builder()
				.name("binaryPropertyName")
				.displayName("Binary Property")
				.description("The name of the input field containing the Base64-encoded binary data.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.displayOptions(Map.of("show", Map.of("operation", List.of("upload"), "binaryData", List.of(true))))
				.build(),

			NodeParameter.builder()
				.name("fileContent")
				.displayName("File Content")
				.description("The text content to upload.")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 5))
				.displayOptions(Map.of("show", Map.of("operation", List.of("upload"), "binaryData", List.of(false))))
				.build(),

			// Delete parameters
			NodeParameter.builder()
				.name("folder")
				.displayName("Is Folder")
				.description("Whether to delete a folder instead of a file.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("operation", List.of("delete"))))
				.build(),

			NodeParameter.builder()
				.name("deleteRecursive")
				.displayName("Recursive")
				.description("Delete folder contents recursively.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("operation", List.of("delete"), "folder", List.of(true))))
				.build(),

			// Rename parameters
			NodeParameter.builder()
				.name("oldPath")
				.displayName("Old Path")
				.description("The current path of the file or folder.")
				.type(ParameterType.STRING)
				.required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("rename"))))
				.build(),

			NodeParameter.builder()
				.name("newPath")
				.displayName("New Path")
				.description("The new path for the file or folder.")
				.type(ParameterType.STRING)
				.required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("rename"))))
				.build(),

			NodeParameter.builder()
				.name("createDirectories")
				.displayName("Create Directories")
				.description("Create any missing parent directories for the new path.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("operation", List.of("rename"))))
				.build(),

			// Shared parameter
			NodeParameter.builder()
				.name("timeout")
				.displayName("Timeout")
				.description("Connection timeout in milliseconds.")
				.type(ParameterType.NUMBER)
				.defaultValue(10000)
				.isNodeSetting(true)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			inputData = List.of(Map.of("json", Map.of()));
		}

		String protocol = context.getParameter("protocol", "ftp");
		String operation = context.getParameter("operation", "list");
		int timeout = toInt(context.getParameter("timeout", 10000), 10000);

		try {
			if ("sftp".equals(protocol)) {
				return executeSftp(context, inputData, operation, timeout);
			} else {
				return executeFtp(context, inputData, operation, timeout);
			}
		} catch (Exception e) {
			return handleError(context, "FTP node error: " + e.getMessage(), e);
		}
	}

	// ========== FTP Implementation ==========

	private NodeExecutionResult executeFtp(NodeExecutionContext context,
			List<Map<String, Object>> inputData, String operation, int timeout) throws Exception {
		Map<String, Object> credentials = context.getCredentials();
		String host = toString(credentials.get("host"));
		int port = toInt(credentials.get("port"), 21);
		String username = toString(credentials.get("username"));
		String password = toString(credentials.get("password"));

		FTPClient ftp = new FTPClient();
		ftp.setConnectTimeout(timeout);
		ftp.setDefaultTimeout(timeout);

		try {
			ftp.connect(host, port);
			int reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				throw new IOException("FTP server refused connection: " + reply);
			}

			if (username != null && !username.isEmpty()) {
				if (!ftp.login(username, password)) {
					throw new IOException("FTP login failed for user: " + username);
				}
			}

			ftp.enterLocalPassiveMode();
			ftp.setFileType(FTP.BINARY_FILE_TYPE);

			switch (operation) {
				case "list": return ftpList(ftp, context, inputData);
				case "download": return ftpDownload(ftp, context, inputData);
				case "upload": return ftpUpload(ftp, context, inputData);
				case "delete": return ftpDelete(ftp, context, inputData);
				case "rename": return ftpRename(ftp, context);
				default: return NodeExecutionResult.error("Unknown operation: " + operation);
			}
		} finally {
			if (ftp.isConnected()) {
				try { ftp.disconnect(); } catch (IOException ignored) {}
			}
		}
	}

	private NodeExecutionResult ftpList(FTPClient ftp, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws IOException {
		String path = context.getParameter("path", "/");
		boolean recursive = toBoolean(context.getParameter("recursive", false), false);

		List<Map<String, Object>> result = new ArrayList<>();
		listFtpDirectory(ftp, path, recursive, result);

		log.debug("FTP: listed {} entries in {}", result.size(), path);
		return NodeExecutionResult.success(result);
	}

	private void listFtpDirectory(FTPClient ftp, String path, boolean recursive,
			List<Map<String, Object>> result) throws IOException {
		FTPFile[] files = ftp.listFiles(path);
		if (files == null) return;

		for (FTPFile file : files) {
			if (".".equals(file.getName()) || "..".equals(file.getName())) continue;

			String fullPath = path.endsWith("/") ? path + file.getName() : path + "/" + file.getName();
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("name", file.getName());
			entry.put("path", fullPath);
			entry.put("type", file.isDirectory() ? "directory" : "file");
			entry.put("size", file.getSize());
			entry.put("modifyTime", file.getTimestamp() != null ? file.getTimestamp().getTimeInMillis() : null);
			result.add(wrapInJson(entry));

			if (recursive && file.isDirectory()) {
				listFtpDirectory(ftp, fullPath, true, result);
			}
		}
	}

	private NodeExecutionResult ftpDownload(FTPClient ftp, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws IOException {
		String path = context.getParameter("path", "");
		if (path.isEmpty()) {
			return NodeExecutionResult.error("Path is required for download operation.");
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		boolean success = ftp.retrieveFile(path, out);
		if (!success) {
			throw new IOException("Failed to download file: " + path + " (reply: " + ftp.getReplyString() + ")");
		}

		String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("fileName", fileName);
		json.put("fileSize", out.size());
		json.put("data", Base64.getEncoder().encodeToString(out.toByteArray()));

		log.debug("FTP: downloaded {} ({} bytes)", path, out.size());
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private NodeExecutionResult ftpUpload(FTPClient ftp, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws IOException {
		String path = context.getParameter("path", "");
		if (path.isEmpty()) {
			return NodeExecutionResult.error("Path is required for upload operation.");
		}

		byte[] content = getUploadContent(context, inputData);

		try (InputStream is = new ByteArrayInputStream(content)) {
			boolean success = ftp.storeFile(path, is);
			if (!success) {
				throw new IOException("Failed to upload file: " + path + " (reply: " + ftp.getReplyString() + ")");
			}
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("fileName", path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path);
		json.put("path", path);
		json.put("fileSize", content.length);
		json.put("success", true);

		log.debug("FTP: uploaded {} ({} bytes)", path, content.length);
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private NodeExecutionResult ftpDelete(FTPClient ftp, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws IOException {
		String path = context.getParameter("path", "");
		if (path.isEmpty()) {
			return NodeExecutionResult.error("Path is required for delete operation.");
		}

		boolean isFolder = toBoolean(context.getParameter("folder", false), false);

		if (isFolder) {
			boolean recursive = toBoolean(context.getParameter("deleteRecursive", false), false);
			if (recursive) {
				deleteFtpDirectoryRecursive(ftp, path);
			}
			boolean success = ftp.removeDirectory(path);
			if (!success) {
				throw new IOException("Failed to delete directory: " + path + " (reply: " + ftp.getReplyString() + ")");
			}
		} else {
			boolean success = ftp.deleteFile(path);
			if (!success) {
				throw new IOException("Failed to delete file: " + path + " (reply: " + ftp.getReplyString() + ")");
			}
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("path", path);
		json.put("success", true);

		log.debug("FTP: deleted {}", path);
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private void deleteFtpDirectoryRecursive(FTPClient ftp, String path) throws IOException {
		FTPFile[] files = ftp.listFiles(path);
		if (files == null) return;

		for (FTPFile file : files) {
			if (".".equals(file.getName()) || "..".equals(file.getName())) continue;
			String fullPath = path.endsWith("/") ? path + file.getName() : path + "/" + file.getName();
			if (file.isDirectory()) {
				deleteFtpDirectoryRecursive(ftp, fullPath);
				ftp.removeDirectory(fullPath);
			} else {
				ftp.deleteFile(fullPath);
			}
		}
	}

	private NodeExecutionResult ftpRename(FTPClient ftp, NodeExecutionContext context) throws IOException {
		String oldPath = context.getParameter("oldPath", "");
		String newPath = context.getParameter("newPath", "");
		if (oldPath.isEmpty() || newPath.isEmpty()) {
			return NodeExecutionResult.error("Both old path and new path are required for rename operation.");
		}

		boolean createDirs = toBoolean(context.getParameter("createDirectories", false), false);
		if (createDirs) {
			String parentDir = newPath.contains("/") ? newPath.substring(0, newPath.lastIndexOf('/')) : "";
			if (!parentDir.isEmpty()) {
				ftpMkdirs(ftp, parentDir);
			}
		}

		boolean success = ftp.rename(oldPath, newPath);
		if (!success) {
			throw new IOException("Failed to rename " + oldPath + " to " + newPath + " (reply: " + ftp.getReplyString() + ")");
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("oldPath", oldPath);
		json.put("newPath", newPath);
		json.put("success", true);

		log.debug("FTP: renamed {} -> {}", oldPath, newPath);
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private void ftpMkdirs(FTPClient ftp, String path) throws IOException {
		String[] parts = path.split("/");
		StringBuilder current = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			current.append("/").append(part);
			ftp.makeDirectory(current.toString());
		}
	}

	// ========== SFTP Implementation ==========

	private NodeExecutionResult executeSftp(NodeExecutionContext context,
			List<Map<String, Object>> inputData, String operation, int timeout) throws Exception {
		Map<String, Object> credentials = context.getCredentials();
		String host = toString(credentials.get("host"));
		int port = toInt(credentials.get("port"), 22);
		String username = toString(credentials.get("username"));
		String password = toString(credentials.get("password"));
		String privateKey = toString(credentials.get("privateKey"));

		JSch jsch = new JSch();

		if (privateKey != null && !privateKey.isEmpty()) {
			jsch.addIdentity("key", privateKey.getBytes(StandardCharsets.UTF_8), null, null);
		}

		Session session = jsch.getSession(username, host, port);
		if (password != null && !password.isEmpty() && (privateKey == null || privateKey.isEmpty())) {
			session.setPassword(password);
		}
		session.setConfig("StrictHostKeyChecking", "no");
		session.setTimeout(timeout);
		session.connect(timeout);

		try {
			ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
			channel.connect(timeout);

			try {
				switch (operation) {
					case "list": return sftpList(channel, context, inputData);
					case "download": return sftpDownload(channel, context, inputData);
					case "upload": return sftpUpload(channel, context, inputData);
					case "delete": return sftpDelete(channel, context, inputData);
					case "rename": return sftpRename(channel, context);
					default: return NodeExecutionResult.error("Unknown operation: " + operation);
				}
			} finally {
				channel.disconnect();
			}
		} finally {
			session.disconnect();
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult sftpList(ChannelSftp channel, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String path = context.getParameter("path", "/");
		boolean recursive = toBoolean(context.getParameter("recursive", false), false);

		List<Map<String, Object>> result = new ArrayList<>();
		listSftpDirectory(channel, path, recursive, result);

		log.debug("SFTP: listed {} entries in {}", result.size(), path);
		return NodeExecutionResult.success(result);
	}

	@SuppressWarnings("unchecked")
	private void listSftpDirectory(ChannelSftp channel, String path, boolean recursive,
			List<Map<String, Object>> result) throws Exception {
		Vector<ChannelSftp.LsEntry> entries = channel.ls(path);

		for (ChannelSftp.LsEntry entry : entries) {
			String name = entry.getFilename();
			if (".".equals(name) || "..".equals(name)) continue;

			String fullPath = path.endsWith("/") ? path + name : path + "/" + name;
			SftpATTRS attrs = entry.getAttrs();

			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", name);
			item.put("path", fullPath);
			item.put("type", attrs.isDir() ? "directory" : "file");
			item.put("size", attrs.getSize());
			item.put("modifyTime", (long) attrs.getMTime() * 1000);
			result.add(wrapInJson(item));

			if (recursive && attrs.isDir()) {
				listSftpDirectory(channel, fullPath, true, result);
			}
		}
	}

	private NodeExecutionResult sftpDownload(ChannelSftp channel, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String path = context.getParameter("path", "");
		if (path.isEmpty()) {
			return NodeExecutionResult.error("Path is required for download operation.");
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		channel.get(path, out);

		String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("fileName", fileName);
		json.put("fileSize", out.size());
		json.put("data", Base64.getEncoder().encodeToString(out.toByteArray()));

		log.debug("SFTP: downloaded {} ({} bytes)", path, out.size());
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private NodeExecutionResult sftpUpload(ChannelSftp channel, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String path = context.getParameter("path", "");
		if (path.isEmpty()) {
			return NodeExecutionResult.error("Path is required for upload operation.");
		}

		byte[] content = getUploadContent(context, inputData);

		try (InputStream is = new ByteArrayInputStream(content)) {
			channel.put(is, path);
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("fileName", path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path);
		json.put("path", path);
		json.put("fileSize", content.length);
		json.put("success", true);

		log.debug("SFTP: uploaded {} ({} bytes)", path, content.length);
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private NodeExecutionResult sftpDelete(ChannelSftp channel, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String path = context.getParameter("path", "");
		if (path.isEmpty()) {
			return NodeExecutionResult.error("Path is required for delete operation.");
		}

		boolean isFolder = toBoolean(context.getParameter("folder", false), false);

		if (isFolder) {
			boolean recursive = toBoolean(context.getParameter("deleteRecursive", false), false);
			if (recursive) {
				deleteSftpDirectoryRecursive(channel, path);
			}
			channel.rmdir(path);
		} else {
			channel.rm(path);
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("path", path);
		json.put("success", true);

		log.debug("SFTP: deleted {}", path);
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	@SuppressWarnings("unchecked")
	private void deleteSftpDirectoryRecursive(ChannelSftp channel, String path) throws Exception {
		Vector<ChannelSftp.LsEntry> entries = channel.ls(path);
		for (ChannelSftp.LsEntry entry : entries) {
			String name = entry.getFilename();
			if (".".equals(name) || "..".equals(name)) continue;
			String fullPath = path.endsWith("/") ? path + name : path + "/" + name;
			if (entry.getAttrs().isDir()) {
				deleteSftpDirectoryRecursive(channel, fullPath);
				channel.rmdir(fullPath);
			} else {
				channel.rm(fullPath);
			}
		}
	}

	private NodeExecutionResult sftpRename(ChannelSftp channel, NodeExecutionContext context) throws Exception {
		String oldPath = context.getParameter("oldPath", "");
		String newPath = context.getParameter("newPath", "");
		if (oldPath.isEmpty() || newPath.isEmpty()) {
			return NodeExecutionResult.error("Both old path and new path are required for rename operation.");
		}

		boolean createDirs = toBoolean(context.getParameter("createDirectories", false), false);
		if (createDirs) {
			String parentDir = newPath.contains("/") ? newPath.substring(0, newPath.lastIndexOf('/')) : "";
			if (!parentDir.isEmpty()) {
				sftpMkdirs(channel, parentDir);
			}
		}

		channel.rename(oldPath, newPath);

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("oldPath", oldPath);
		json.put("newPath", newPath);
		json.put("success", true);

		log.debug("SFTP: renamed {} -> {}", oldPath, newPath);
		return NodeExecutionResult.success(List.of(wrapInJson(json)));
	}

	private void sftpMkdirs(ChannelSftp channel, String path) throws Exception {
		String[] parts = path.split("/");
		StringBuilder current = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			current.append("/").append(part);
			try {
				channel.stat(current.toString());
			} catch (Exception e) {
				channel.mkdir(current.toString());
			}
		}
	}

	// ========== Shared Helpers ==========

	private byte[] getUploadContent(NodeExecutionContext context, List<Map<String, Object>> inputData) {
		boolean binaryData = toBoolean(context.getParameter("binaryData", false), false);

		if (binaryData) {
			String propertyName = context.getParameter("binaryPropertyName", "data");
			if (!inputData.isEmpty()) {
				Map<String, Object> json = unwrapJson(inputData.get(0));
				Object data = json.get(propertyName);
				if (data instanceof String) {
					return Base64.getDecoder().decode((String) data);
				}
			}
			return new byte[0];
		} else {
			String content = context.getParameter("fileContent", "");
			return content.getBytes(StandardCharsets.UTF_8);
		}
	}
}
