package io.trellis.util;

import java.util.List;
import java.util.Map;

/**
 * Generates self-contained HTML pages for forms, completion screens, and error pages.
 * All styles are inline — no external CSS dependencies.
 */
public final class FormHtmlGenerator {

    private FormHtmlGenerator() {}

    private static final String CSS = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
               background: #f0f2f5; min-height: 100vh; display: flex; align-items: center;
               justify-content: center; padding: 20px; color: #1a1a2e; }
        .card { background: #fff; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.08);
                max-width: 520px; width: 100%; padding: 32px; }
        h1 { font-size: 22px; font-weight: 600; margin-bottom: 4px; }
        .description { color: #666; font-size: 14px; margin-bottom: 24px; line-height: 1.5; }
        .field { margin-bottom: 18px; }
        .field label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #333; }
        .field .required { color: #e74c3c; margin-left: 2px; }
        .field input, .field textarea, .field select {
            width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 8px;
            font-size: 14px; font-family: inherit; transition: border-color 0.2s; outline: none; }
        .field input:focus, .field textarea:focus, .field select:focus { border-color: #6366f1; }
        .field textarea { resize: vertical; min-height: 80px; }
        .field input[type="checkbox"], .field input[type="radio"] { width: auto; margin-right: 8px; }
        .checkbox-group, .radio-group { display: flex; flex-direction: column; gap: 8px; }
        .checkbox-group label, .radio-group label { font-weight: 400; display: flex; align-items: center;
            cursor: pointer; font-size: 14px; }
        .hint { font-size: 12px; color: #888; margin-top: 4px; }
        .btn { display: inline-block; padding: 10px 24px; background: #6366f1; color: #fff;
               border: none; border-radius: 8px; font-size: 15px; font-weight: 600;
               cursor: pointer; transition: background 0.2s; width: 100%; text-align: center; }
        .btn:hover { background: #4f46e5; }
        .btn-row { margin-top: 24px; display: flex; gap: 12px; }
        .btn-approve { background: #22c55e; } .btn-approve:hover { background: #16a34a; }
        .btn-decline { background: #ef4444; } .btn-decline:hover { background: #dc2626; }
        .btn-secondary { background: #fff; color: #333; border: 1px solid #d1d5db; }
        .btn-secondary:hover { background: #f9fafb; }
        .success-icon { font-size: 48px; margin-bottom: 16px; }
        .error-icon { font-size: 48px; margin-bottom: 16px; }
        .center { text-align: center; }
        """;

    /**
     * Generate a full HTML form from a list of field definitions.
     * Each field map should contain: fieldLabel, fieldType, and optionally
     * placeholder, defaultValue, required (boolean), selectOptions (comma-separated).
     */
    public static String generateForm(Object formDefinition, String postAction) {
        return generateForm(formDefinition, postAction, "Form", null, "Submit");
    }

    @SuppressWarnings("unchecked")
    public static String generateForm(Object formDefinition, String postAction,
                                       String title, String description, String buttonLabel) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>").append(escapeHtml(title)).append("</title>");
        html.append("<style>").append(CSS).append("</style></head><body>");
        html.append("<div class=\"card\">");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");

        if (description != null && !description.isBlank()) {
            html.append("<p class=\"description\">").append(escapeHtml(description)).append("</p>");
        }

        html.append("<form method=\"POST\" action=\"").append(escapeHtml(postAction)).append("\">");

        if (formDefinition instanceof List<?> fields) {
            for (Object fieldObj : fields) {
                if (fieldObj instanceof Map) {
                    renderField(html, (Map<String, Object>) fieldObj);
                }
            }
        }

        html.append("<div class=\"btn-row\"><button type=\"submit\" class=\"btn\">");
        html.append(escapeHtml(buttonLabel != null ? buttonLabel : "Submit"));
        html.append("</button></div>");
        html.append("</form></div></body></html>");
        return html.toString();
    }

    /**
     * Generate an approval page with Approve and optionally Decline buttons.
     */
    public static String generateApprovalPage(String title, String message,
                                               String approveUrl, String declineUrl,
                                               String approveLabel, String declineLabel) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>").append(escapeHtml(title)).append("</title>");
        html.append("<style>").append(CSS).append("</style></head><body>");
        html.append("<div class=\"card center\">");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");

        if (message != null && !message.isBlank()) {
            html.append("<p class=\"description\">").append(escapeHtml(message)).append("</p>");
        }

        html.append("<div class=\"btn-row\" style=\"justify-content:center\">");
        html.append("<a href=\"").append(escapeHtml(approveUrl))
            .append("\" class=\"btn btn-approve\">").append(escapeHtml(approveLabel)).append("</a>");

        if (declineUrl != null) {
            html.append("<a href=\"").append(escapeHtml(declineUrl))
                .append("\" class=\"btn btn-decline\">").append(escapeHtml(declineLabel)).append("</a>");
        }

        html.append("</div></div></body></html>");
        return html.toString();
    }

    /**
     * Generate a completion page shown after form submission.
     */
    public static String completionPage(String title, String message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>").append(escapeHtml(title)).append("</title>");
        html.append("<style>").append(CSS).append("</style></head><body>");
        html.append("<div class=\"card center\">");
        html.append("<div class=\"success-icon\">&#10004;</div>");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");
        if (message != null && !message.isBlank()) {
            html.append("<p class=\"description\">").append(escapeHtml(message)).append("</p>");
        }
        html.append("</div></body></html>");
        return html.toString();
    }

    /**
     * Generate an error page.
     */
    public static String errorPage(String message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>Error</title>");
        html.append("<style>").append(CSS).append("</style></head><body>");
        html.append("<div class=\"card center\">");
        html.append("<div class=\"error-icon\">&#10060;</div>");
        html.append("<h1>Error</h1>");
        html.append("<p class=\"description\">").append(escapeHtml(message)).append("</p>");
        html.append("</div></body></html>");
        return html.toString();
    }

    /**
     * Generate a redirect page.
     */
    public static String redirectPage(String url) {
        return "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url="
                + escapeHtml(url) + "\"></head><body>Redirecting...</body></html>";
    }

    // ---- Field Rendering ----

    private static void renderField(StringBuilder html, Map<String, Object> field) {
        String label = strVal(field, "fieldLabel", "Field");
        String name = strVal(field, "fieldLabel", label).replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        String type = strVal(field, "fieldType", "text");
        String placeholder = strVal(field, "placeholder", "");
        String defaultValue = strVal(field, "defaultValue", "");
        boolean required = Boolean.TRUE.equals(field.get("required"))
                || Boolean.TRUE.equals(field.get("inputRequired"));
        String selectOptions = strVal(field, "selectOptions", "");

        html.append("<div class=\"field\">");

        switch (type) {
            case "textarea" -> {
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<textarea name=\"").append(escapeHtml(name)).append("\"");
                if (!placeholder.isEmpty()) html.append(" placeholder=\"").append(escapeHtml(placeholder)).append("\"");
                if (required) html.append(" required");
                html.append(">").append(escapeHtml(defaultValue)).append("</textarea>");
            }
            case "dropdown" -> {
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<select name=\"").append(escapeHtml(name)).append("\"");
                if (required) html.append(" required");
                html.append(">");
                html.append("<option value=\"\">-- Select --</option>");
                for (String opt : selectOptions.split(",")) {
                    String trimmed = opt.trim();
                    if (!trimmed.isEmpty()) {
                        html.append("<option value=\"").append(escapeHtml(trimmed)).append("\"");
                        if (trimmed.equals(defaultValue)) html.append(" selected");
                        html.append(">").append(escapeHtml(trimmed)).append("</option>");
                    }
                }
                html.append("</select>");
            }
            case "checkbox" -> {
                html.append("<label>").append(escapeHtml(label)).append("</label>");
                if (!selectOptions.isEmpty()) {
                    html.append("<div class=\"checkbox-group\">");
                    for (String opt : selectOptions.split(",")) {
                        String trimmed = opt.trim();
                        if (!trimmed.isEmpty()) {
                            html.append("<label><input type=\"checkbox\" name=\"")
                                .append(escapeHtml(name)).append("\" value=\"")
                                .append(escapeHtml(trimmed)).append("\"> ")
                                .append(escapeHtml(trimmed)).append("</label>");
                        }
                    }
                    html.append("</div>");
                } else {
                    html.append("<label><input type=\"checkbox\" name=\"")
                        .append(escapeHtml(name)).append("\" value=\"true\"");
                    if ("true".equalsIgnoreCase(defaultValue)) html.append(" checked");
                    html.append("> ").append(escapeHtml(label)).append("</label>");
                }
            }
            case "radio" -> {
                html.append("<label>").append(escapeHtml(label)).append("</label>");
                html.append("<div class=\"radio-group\">");
                for (String opt : selectOptions.split(",")) {
                    String trimmed = opt.trim();
                    if (!trimmed.isEmpty()) {
                        html.append("<label><input type=\"radio\" name=\"")
                            .append(escapeHtml(name)).append("\" value=\"")
                            .append(escapeHtml(trimmed)).append("\"");
                        if (trimmed.equals(defaultValue)) html.append(" checked");
                        html.append("> ").append(escapeHtml(trimmed)).append("</label>");
                    }
                }
                html.append("</div>");
            }
            case "number" -> {
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<input type=\"number\" name=\"").append(escapeHtml(name)).append("\"");
                if (!placeholder.isEmpty()) html.append(" placeholder=\"").append(escapeHtml(placeholder)).append("\"");
                if (!defaultValue.isEmpty()) html.append(" value=\"").append(escapeHtml(defaultValue)).append("\"");
                if (required) html.append(" required");
                html.append(">");
            }
            case "email" -> {
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<input type=\"email\" name=\"").append(escapeHtml(name)).append("\"");
                if (!placeholder.isEmpty()) html.append(" placeholder=\"").append(escapeHtml(placeholder)).append("\"");
                if (!defaultValue.isEmpty()) html.append(" value=\"").append(escapeHtml(defaultValue)).append("\"");
                if (required) html.append(" required");
                html.append(">");
            }
            case "password" -> {
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<input type=\"password\" name=\"").append(escapeHtml(name)).append("\"");
                if (!placeholder.isEmpty()) html.append(" placeholder=\"").append(escapeHtml(placeholder)).append("\"");
                if (required) html.append(" required");
                html.append(">");
            }
            case "date" -> {
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<input type=\"date\" name=\"").append(escapeHtml(name)).append("\"");
                if (!defaultValue.isEmpty()) html.append(" value=\"").append(escapeHtml(defaultValue)).append("\"");
                if (required) html.append(" required");
                html.append(">");
            }
            case "hidden" -> {
                String hiddenValue = strVal(field, "fieldValue", defaultValue);
                html.append("<input type=\"hidden\" name=\"").append(escapeHtml(name))
                    .append("\" value=\"").append(escapeHtml(hiddenValue)).append("\">");
            }
            default -> {
                // text and any unknown type
                html.append("<label>").append(escapeHtml(label));
                if (required) html.append("<span class=\"required\">*</span>");
                html.append("</label>");
                html.append("<input type=\"text\" name=\"").append(escapeHtml(name)).append("\"");
                if (!placeholder.isEmpty()) html.append(" placeholder=\"").append(escapeHtml(placeholder)).append("\"");
                if (!defaultValue.isEmpty()) html.append(" value=\"").append(escapeHtml(defaultValue)).append("\"");
                if (required) html.append(" required");
                html.append(">");
            }
        }

        html.append("</div>");
    }

    private static String strVal(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        if (val == null) return fallback;
        String s = String.valueOf(val);
        return s.isEmpty() ? fallback : s;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
