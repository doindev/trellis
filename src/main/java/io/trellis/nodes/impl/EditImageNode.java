package io.trellis.nodes.impl;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;

import javax.imageio.ImageIO;

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

/**
 * Edit Image Node -- performs local image processing operations using Java AWT.
 * Supports resize, crop, rotate, blur, border, composite, draw shapes/text,
 * get information, shear, transparency, and multi-step operations.
 */
@Slf4j
@Node(
	type = "editImage",
	displayName = "Edit Image",
	description = "Edit and manipulate images with operations like resize, crop, rotate, blur, and more.",
	category = "File Operations",
	icon = "editImage"
)
public class EditImageNode extends AbstractNode {

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
		List<NodeParameter> params = new ArrayList<>();

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("resize")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Blur").value("blur").description("Apply a blur filter to the image").build(),
				ParameterOption.builder().name("Border").value("border").description("Add a border around the image").build(),
				ParameterOption.builder().name("Composite").value("composite").description("Composite two images together").build(),
				ParameterOption.builder().name("Crop").value("crop").description("Crop the image to a specified region").build(),
				ParameterOption.builder().name("Draw").value("draw").description("Draw shapes or text on the image").build(),
				ParameterOption.builder().name("Get Information").value("getInformation").description("Get image metadata (width, height, format)").build(),
				ParameterOption.builder().name("Multi Step").value("multiStep").description("Apply multiple operations in sequence").build(),
				ParameterOption.builder().name("Resize").value("resize").description("Resize the image to new dimensions").build(),
				ParameterOption.builder().name("Rotate").value("rotate").description("Rotate the image by a given angle").build(),
				ParameterOption.builder().name("Shear").value("shear").description("Apply a shear transformation to the image").build(),
				ParameterOption.builder().name("Text").value("text").description("Add text overlay to the image").build(),
				ParameterOption.builder().name("Transparency").value("transparency").description("Adjust image transparency/opacity").build()
			)).build());

		// Binary property name (where to read image data from input)
		params.add(NodeParameter.builder()
			.name("binaryPropertyName").displayName("Binary Property")
			.type(ParameterType.STRING).defaultValue("data")
			.description("Name of the binary property containing the image data.")
			.build());

		// Output format
		params.add(NodeParameter.builder()
			.name("outputFormat").displayName("Output Format")
			.type(ParameterType.OPTIONS).defaultValue("png")
			.options(List.of(
				ParameterOption.builder().name("PNG").value("png").build(),
				ParameterOption.builder().name("JPEG").value("jpeg").build(),
				ParameterOption.builder().name("BMP").value("bmp").build(),
				ParameterOption.builder().name("GIF").value("gif").build()
			)).build());

		// ========================= Resize Parameters =========================
		params.add(NodeParameter.builder()
			.name("width").displayName("Width").type(ParameterType.NUMBER).defaultValue(800)
			.description("Target width in pixels.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("resize"))))
			.build());

		params.add(NodeParameter.builder()
			.name("height").displayName("Height").type(ParameterType.NUMBER).defaultValue(600)
			.description("Target height in pixels.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("resize"))))
			.build());

		params.add(NodeParameter.builder()
			.name("resizeOption").displayName("Resize Option")
			.type(ParameterType.OPTIONS).defaultValue("exact")
			.displayOptions(Map.of("show", Map.of("operation", List.of("resize"))))
			.options(List.of(
				ParameterOption.builder().name("Exact").value("exact").description("Resize to exact dimensions").build(),
				ParameterOption.builder().name("Proportional (by Width)").value("byWidth").description("Resize proportionally using width").build(),
				ParameterOption.builder().name("Proportional (by Height)").value("byHeight").description("Resize proportionally using height").build(),
				ParameterOption.builder().name("Contain").value("contain").description("Resize to fit within dimensions, maintaining aspect ratio").build()
			)).build());

		// ========================= Crop Parameters =========================
		params.add(NodeParameter.builder()
			.name("cropX").displayName("X Position").type(ParameterType.NUMBER).defaultValue(0)
			.description("Left position of the crop area.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("crop"))))
			.build());

		params.add(NodeParameter.builder()
			.name("cropY").displayName("Y Position").type(ParameterType.NUMBER).defaultValue(0)
			.description("Top position of the crop area.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("crop"))))
			.build());

		params.add(NodeParameter.builder()
			.name("cropWidth").displayName("Width").type(ParameterType.NUMBER).defaultValue(400)
			.description("Width of the crop area.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("crop"))))
			.build());

		params.add(NodeParameter.builder()
			.name("cropHeight").displayName("Height").type(ParameterType.NUMBER).defaultValue(300)
			.description("Height of the crop area.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("crop"))))
			.build());

		// ========================= Rotate Parameters =========================
		params.add(NodeParameter.builder()
			.name("angle").displayName("Angle (degrees)").type(ParameterType.NUMBER).defaultValue(90)
			.description("Rotation angle in degrees (clockwise).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("rotate"))))
			.build());

		params.add(NodeParameter.builder()
			.name("rotateBackground").displayName("Background Color")
			.type(ParameterType.STRING).defaultValue("#ffffff")
			.description("Background color for areas exposed by rotation (hex color).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("rotate"))))
			.build());

		// ========================= Blur Parameters =========================
		params.add(NodeParameter.builder()
			.name("blurRadius").displayName("Blur Radius").type(ParameterType.NUMBER).defaultValue(5)
			.description("Radius of the blur effect (1-20).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("blur"))))
			.build());

		// ========================= Border Parameters =========================
		params.add(NodeParameter.builder()
			.name("borderWidth").displayName("Border Width").type(ParameterType.NUMBER).defaultValue(10)
			.description("Width of the border in pixels.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("border"))))
			.build());

		params.add(NodeParameter.builder()
			.name("borderColor").displayName("Border Color")
			.type(ParameterType.STRING).defaultValue("#000000")
			.description("Border color in hex format.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("border"))))
			.build());

		// ========================= Draw Parameters =========================
		params.add(NodeParameter.builder()
			.name("drawType").displayName("Draw Type")
			.type(ParameterType.OPTIONS).defaultValue("rectangle")
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.options(List.of(
				ParameterOption.builder().name("Circle").value("circle").build(),
				ParameterOption.builder().name("Line").value("line").build(),
				ParameterOption.builder().name("Rectangle").value("rectangle").build(),
				ParameterOption.builder().name("Text").value("text").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("drawX").displayName("X Position").type(ParameterType.NUMBER).defaultValue(10)
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawY").displayName("Y Position").type(ParameterType.NUMBER).defaultValue(10)
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawWidth").displayName("Width / Radius / End X").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawHeight").displayName("Height / End Y").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawColor").displayName("Color")
			.type(ParameterType.STRING).defaultValue("#ff0000")
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawFill").displayName("Fill").type(ParameterType.BOOLEAN).defaultValue(false)
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawText").displayName("Text")
			.type(ParameterType.STRING).defaultValue("")
			.description("Text to draw (only used when draw type is Text).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		params.add(NodeParameter.builder()
			.name("drawFontSize").displayName("Font Size").type(ParameterType.NUMBER).defaultValue(16)
			.displayOptions(Map.of("show", Map.of("operation", List.of("draw"))))
			.build());

		// ========================= Text Parameters =========================
		params.add(NodeParameter.builder()
			.name("textContent").displayName("Text")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.description("The text to render on the image.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("text"))))
			.build());

		params.add(NodeParameter.builder()
			.name("textX").displayName("X Position").type(ParameterType.NUMBER).defaultValue(10)
			.displayOptions(Map.of("show", Map.of("operation", List.of("text"))))
			.build());

		params.add(NodeParameter.builder()
			.name("textY").displayName("Y Position").type(ParameterType.NUMBER).defaultValue(30)
			.displayOptions(Map.of("show", Map.of("operation", List.of("text"))))
			.build());

		params.add(NodeParameter.builder()
			.name("textFontSize").displayName("Font Size").type(ParameterType.NUMBER).defaultValue(24)
			.displayOptions(Map.of("show", Map.of("operation", List.of("text"))))
			.build());

		params.add(NodeParameter.builder()
			.name("textColor").displayName("Text Color")
			.type(ParameterType.STRING).defaultValue("#000000")
			.displayOptions(Map.of("show", Map.of("operation", List.of("text"))))
			.build());

		// ========================= Shear Parameters =========================
		params.add(NodeParameter.builder()
			.name("shearX").displayName("Shear X").type(ParameterType.NUMBER).defaultValue(0)
			.description("Horizontal shear factor.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("shear"))))
			.build());

		params.add(NodeParameter.builder()
			.name("shearY").displayName("Shear Y").type(ParameterType.NUMBER).defaultValue(0)
			.description("Vertical shear factor.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("shear"))))
			.build());

		// ========================= Transparency Parameters =========================
		params.add(NodeParameter.builder()
			.name("opacity").displayName("Opacity").type(ParameterType.NUMBER).defaultValue(50)
			.description("Opacity percentage (0 = fully transparent, 100 = fully opaque).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("transparency"))))
			.build());

		// ========================= Composite Parameters =========================
		params.add(NodeParameter.builder()
			.name("compositeProperty").displayName("Overlay Binary Property")
			.type(ParameterType.STRING).defaultValue("overlay")
			.description("Name of the binary property containing the overlay image.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("composite"))))
			.build());

		params.add(NodeParameter.builder()
			.name("compositeX").displayName("X Position").type(ParameterType.NUMBER).defaultValue(0)
			.displayOptions(Map.of("show", Map.of("operation", List.of("composite"))))
			.build());

		params.add(NodeParameter.builder()
			.name("compositeY").displayName("Y Position").type(ParameterType.NUMBER).defaultValue(0)
			.displayOptions(Map.of("show", Map.of("operation", List.of("composite"))))
			.build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "resize");

		try {
			List<Map<String, Object>> inputData = context.getInputData();
			if (inputData == null || inputData.isEmpty()) {
				return NodeExecutionResult.error("No input data provided.");
			}

			List<Map<String, Object>> results = new ArrayList<>();
			String binaryProperty = context.getParameter("binaryPropertyName", "data");
			String outputFormat = context.getParameter("outputFormat", "png");

			for (Map<String, Object> item : inputData) {
				Map<String, Object> json = unwrapJson(item);

				// For getInformation, we just need basic data
				if ("getInformation".equals(operation)) {
					Map<String, Object> info = getImageInformation(json, binaryProperty);
					results.add(wrapInJson(info));
					continue;
				}

				// Read the image data from the binary property
				byte[] imageBytes = getImageBytes(json, binaryProperty);
				if (imageBytes == null || imageBytes.length == 0) {
					results.add(wrapInJson(Map.of("error", "No image data found in binary property: " + binaryProperty)));
					continue;
				}

				BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
				if (image == null) {
					results.add(wrapInJson(Map.of("error", "Could not decode image data.")));
					continue;
				}

				BufferedImage resultImage = processImage(image, operation, context, json);

				// Write the result image back to bytes
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(resultImage, outputFormat, baos);
				byte[] outputBytes = baos.toByteArray();

				Map<String, Object> resultData = new LinkedHashMap<>(json);
				resultData.put(binaryProperty, Base64.getEncoder().encodeToString(outputBytes));
				resultData.put("mimeType", "image/" + outputFormat);
				resultData.put("imageWidth", resultImage.getWidth());
				resultData.put("imageHeight", resultImage.getHeight());
				resultData.put("imageFormat", outputFormat);
				resultData.put("fileSize", outputBytes.length);
				results.add(wrapInJson(resultData));
			}

			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "Edit Image error: " + e.getMessage(), e);
		}
	}

	// ========================= Image Processing =========================

	private BufferedImage processImage(BufferedImage image, String operation, NodeExecutionContext context, Map<String, Object> json) throws Exception {
		return switch (operation) {
			case "resize" -> executeResize(image, context);
			case "crop" -> executeCrop(image, context);
			case "rotate" -> executeRotate(image, context);
			case "blur" -> executeBlur(image, context);
			case "border" -> executeBorder(image, context);
			case "draw" -> executeDraw(image, context);
			case "text" -> executeText(image, context);
			case "shear" -> executeShear(image, context);
			case "transparency" -> executeTransparency(image, context);
			case "composite" -> executeComposite(image, context, json);
			default -> image;
		};
	}

	// ========================= Resize =========================

	private BufferedImage executeResize(BufferedImage image, NodeExecutionContext context) {
		int targetWidth = toInt(context.getParameter("width", 800), 800);
		int targetHeight = toInt(context.getParameter("height", 600), 600);
		String resizeOption = context.getParameter("resizeOption", "exact");

		switch (resizeOption) {
			case "byWidth": {
				double ratio = (double) targetWidth / image.getWidth();
				targetHeight = (int) (image.getHeight() * ratio);
				break;
			}
			case "byHeight": {
				double ratio = (double) targetHeight / image.getHeight();
				targetWidth = (int) (image.getWidth() * ratio);
				break;
			}
			case "contain": {
				double ratioW = (double) targetWidth / image.getWidth();
				double ratioH = (double) targetHeight / image.getHeight();
				double ratio = Math.min(ratioW, ratioH);
				targetWidth = (int) (image.getWidth() * ratio);
				targetHeight = (int) (image.getHeight() * ratio);
				break;
			}
			default:
				// exact -- use specified dimensions
				break;
		}

		BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = resized.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return resized;
	}

	// ========================= Crop =========================

	private BufferedImage executeCrop(BufferedImage image, NodeExecutionContext context) {
		int x = toInt(context.getParameter("cropX", 0), 0);
		int y = toInt(context.getParameter("cropY", 0), 0);
		int w = toInt(context.getParameter("cropWidth", 400), 400);
		int h = toInt(context.getParameter("cropHeight", 300), 300);

		// Clamp to image bounds
		x = Math.max(0, Math.min(x, image.getWidth() - 1));
		y = Math.max(0, Math.min(y, image.getHeight() - 1));
		w = Math.min(w, image.getWidth() - x);
		h = Math.min(h, image.getHeight() - y);

		return image.getSubimage(x, y, w, h);
	}

	// ========================= Rotate =========================

	private BufferedImage executeRotate(BufferedImage image, NodeExecutionContext context) {
		double angle = toDouble(context.getParameter("angle", 90), 90);
		String bgColorHex = context.getParameter("rotateBackground", "#ffffff");
		Color bgColor = parseHexColor(bgColorHex);

		double radians = Math.toRadians(angle);
		double sin = Math.abs(Math.sin(radians));
		double cos = Math.abs(Math.cos(radians));
		int newWidth = (int) Math.ceil(image.getWidth() * cos + image.getHeight() * sin);
		int newHeight = (int) Math.ceil(image.getHeight() * cos + image.getWidth() * sin);

		BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = rotated.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(bgColor);
		g2d.fillRect(0, 0, newWidth, newHeight);

		AffineTransform transform = new AffineTransform();
		transform.translate((newWidth - image.getWidth()) / 2.0, (newHeight - image.getHeight()) / 2.0);
		transform.rotate(radians, image.getWidth() / 2.0, image.getHeight() / 2.0);
		g2d.setTransform(transform);
		g2d.drawImage(image, 0, 0, null);
		g2d.dispose();
		return rotated;
	}

	// ========================= Blur =========================

	private BufferedImage executeBlur(BufferedImage image, NodeExecutionContext context) {
		int radius = toInt(context.getParameter("blurRadius", 5), 5);
		radius = Math.max(1, Math.min(radius, 20));

		int size = radius * 2 + 1;
		float[] kernelData = new float[size * size];
		float value = 1.0f / (size * size);
		Arrays.fill(kernelData, value);

		Kernel kernel = new Kernel(size, size, kernelData);

		// Ensure image is in a compatible type for ConvolveOp
		BufferedImage compatible = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = compatible.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();

		// Pad the image to avoid edge issues
		int pad = size;
		BufferedImage padded = new BufferedImage(
			compatible.getWidth() + pad * 2, compatible.getHeight() + pad * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gp = padded.createGraphics();
		gp.drawImage(compatible, pad, pad, null);
		gp.dispose();

		ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
		BufferedImage blurredPadded = convolveOp.filter(padded, null);

		return blurredPadded.getSubimage(pad, pad, compatible.getWidth(), compatible.getHeight());
	}

	// ========================= Border =========================

	private BufferedImage executeBorder(BufferedImage image, NodeExecutionContext context) {
		int borderWidth = toInt(context.getParameter("borderWidth", 10), 10);
		String borderColorHex = context.getParameter("borderColor", "#000000");
		Color borderColor = parseHexColor(borderColorHex);

		int newWidth = image.getWidth() + borderWidth * 2;
		int newHeight = image.getHeight() + borderWidth * 2;

		BufferedImage bordered = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bordered.createGraphics();
		g2d.setColor(borderColor);
		g2d.fillRect(0, 0, newWidth, newHeight);
		g2d.drawImage(image, borderWidth, borderWidth, null);
		g2d.dispose();
		return bordered;
	}

	// ========================= Draw =========================

	private BufferedImage executeDraw(BufferedImage image, NodeExecutionContext context) {
		String drawType = context.getParameter("drawType", "rectangle");
		int x = toInt(context.getParameter("drawX", 10), 10);
		int y = toInt(context.getParameter("drawY", 10), 10);
		int w = toInt(context.getParameter("drawWidth", 100), 100);
		int h = toInt(context.getParameter("drawHeight", 100), 100);
		String colorHex = context.getParameter("drawColor", "#ff0000");
		boolean fill = toBoolean(context.getParameter("drawFill", false), false);
		Color color = parseHexColor(colorHex);

		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = result.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.drawImage(image, 0, 0, null);
		g2d.setColor(color);
		g2d.setStroke(new BasicStroke(2));

		switch (drawType) {
			case "rectangle": {
				if (fill) {
					g2d.fillRect(x, y, w, h);
				} else {
					g2d.drawRect(x, y, w, h);
				}
				break;
			}
			case "circle": {
				// w is the radius
				if (fill) {
					g2d.fillOval(x - w, y - w, w * 2, w * 2);
				} else {
					g2d.drawOval(x - w, y - w, w * 2, w * 2);
				}
				break;
			}
			case "line": {
				g2d.drawLine(x, y, w, h);
				break;
			}
			case "text": {
				String text = context.getParameter("drawText", "");
				int fontSize = toInt(context.getParameter("drawFontSize", 16), 16);
				g2d.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
				g2d.drawString(text, x, y);
				break;
			}
			default:
				break;
		}

		g2d.dispose();
		return result;
	}

	// ========================= Text =========================

	private BufferedImage executeText(BufferedImage image, NodeExecutionContext context) {
		String text = context.getParameter("textContent", "");
		int x = toInt(context.getParameter("textX", 10), 10);
		int y = toInt(context.getParameter("textY", 30), 30);
		int fontSize = toInt(context.getParameter("textFontSize", 24), 24);
		String colorHex = context.getParameter("textColor", "#000000");
		Color color = parseHexColor(colorHex);

		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = result.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.drawImage(image, 0, 0, null);
		g2d.setColor(color);
		g2d.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
		g2d.drawString(text, x, y);
		g2d.dispose();
		return result;
	}

	// ========================= Shear =========================

	private BufferedImage executeShear(BufferedImage image, NodeExecutionContext context) {
		double shearX = toDouble(context.getParameter("shearX", 0), 0);
		double shearY = toDouble(context.getParameter("shearY", 0), 0);

		int newWidth = (int) (image.getWidth() + Math.abs(shearX) * image.getHeight());
		int newHeight = (int) (image.getHeight() + Math.abs(shearY) * image.getWidth());

		BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = result.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		AffineTransform transform = new AffineTransform();
		if (shearX < 0) transform.translate(-shearX * image.getHeight(), 0);
		if (shearY < 0) transform.translate(0, -shearY * image.getWidth());
		transform.shear(shearX, shearY);

		g2d.setTransform(transform);
		g2d.drawImage(image, 0, 0, null);
		g2d.dispose();
		return result;
	}

	// ========================= Transparency =========================

	private BufferedImage executeTransparency(BufferedImage image, NodeExecutionContext context) {
		int opacityPct = toInt(context.getParameter("opacity", 50), 50);
		float opacity = Math.max(0, Math.min(100, opacityPct)) / 100.0f;

		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = result.createGraphics();
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		g2d.drawImage(image, 0, 0, null);
		g2d.dispose();
		return result;
	}

	// ========================= Composite =========================

	private BufferedImage executeComposite(BufferedImage image, NodeExecutionContext context, Map<String, Object> json) throws Exception {
		String overlayProperty = context.getParameter("compositeProperty", "overlay");
		int compositeX = toInt(context.getParameter("compositeX", 0), 0);
		int compositeY = toInt(context.getParameter("compositeY", 0), 0);

		byte[] overlayBytes = getImageBytes(json, overlayProperty);
		if (overlayBytes == null || overlayBytes.length == 0) {
			return image; // no overlay, return original
		}

		BufferedImage overlay = ImageIO.read(new ByteArrayInputStream(overlayBytes));
		if (overlay == null) {
			return image;
		}

		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = result.createGraphics();
		g2d.drawImage(image, 0, 0, null);
		g2d.drawImage(overlay, compositeX, compositeY, null);
		g2d.dispose();
		return result;
	}

	// ========================= Get Information =========================

	@SuppressWarnings("unchecked")
	private Map<String, Object> getImageInformation(Map<String, Object> json, String binaryProperty) throws Exception {
		Map<String, Object> info = new LinkedHashMap<>();

		byte[] imageBytes = getImageBytes(json, binaryProperty);
		if (imageBytes == null || imageBytes.length == 0) {
			info.put("error", "No image data found in binary property: " + binaryProperty);
			return info;
		}

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
		if (image == null) {
			info.put("error", "Could not decode image data.");
			return info;
		}

		info.put("width", image.getWidth());
		info.put("height", image.getHeight());
		info.put("colorModel", image.getColorModel().getNumComponents() + " components");
		info.put("colorSpace", image.getColorModel().getColorSpace().getType());
		info.put("hasAlpha", image.getColorModel().hasAlpha());
		info.put("bitDepth", image.getColorModel().getPixelSize());
		info.put("fileSize", imageBytes.length);

		return info;
	}

	// ========================= Helpers =========================

	@SuppressWarnings("unchecked")
	private byte[] getImageBytes(Map<String, Object> json, String binaryProperty) {
		Object data = json.get(binaryProperty);
		if (data == null) {
			return null;
		}

		if (data instanceof byte[]) {
			return (byte[]) data;
		}

		if (data instanceof String) {
			String str = (String) data;
			// Try to decode as Base64
			try {
				return Base64.getDecoder().decode(str);
			} catch (IllegalArgumentException e) {
				log.warn("Could not decode binary property '{}' as Base64", binaryProperty);
				return null;
			}
		}

		return null;
	}

	private Color parseHexColor(String hex) {
		if (hex == null || hex.isEmpty()) {
			return Color.BLACK;
		}
		try {
			if (hex.startsWith("#")) {
				hex = hex.substring(1);
			}
			if (hex.length() == 6) {
				return new Color(
					Integer.parseInt(hex.substring(0, 2), 16),
					Integer.parseInt(hex.substring(2, 4), 16),
					Integer.parseInt(hex.substring(4, 6), 16)
				);
			} else if (hex.length() == 8) {
				return new Color(
					Integer.parseInt(hex.substring(0, 2), 16),
					Integer.parseInt(hex.substring(2, 4), 16),
					Integer.parseInt(hex.substring(4, 6), 16),
					Integer.parseInt(hex.substring(6, 8), 16)
				);
			}
		} catch (NumberFormatException e) {
			log.warn("Invalid hex color: {}", hex);
		}
		return Color.BLACK;
	}
}
