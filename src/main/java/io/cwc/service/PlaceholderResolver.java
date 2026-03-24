package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{env:VAR_NAME}} and {{env:VAR_NAME:default}} placeholders
 * in parsed JSON object trees (Maps, Lists, Strings).
 *
 * Resolution order: Spring Environment (env vars -> system props -> application.properties).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{env:([^}]+?)\\}\\}");

    private final Environment environment;

    /**
     * Recursively resolves all {{env:...}} placeholders in a parsed JSON object tree.
     *
     * @param jsonTree a Map, List, or String parsed from JSON
     * @return resolved result with the processed tree and any unresolved placeholders
     */
    public ResolvedResult resolve(Object jsonTree) {
        List<String> unresolved = new ArrayList<>();
        Object resolved = resolveNode(jsonTree, unresolved);
        return new ResolvedResult(resolved, unresolved);
    }

    @SuppressWarnings("unchecked")
    private Object resolveNode(Object node, List<String> unresolved) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                result.put(entry.getKey(), resolveNode(entry.getValue(), unresolved));
            }
            return result;
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(resolveNode(item, unresolved));
            }
            return result;
        } else if (node instanceof String) {
            return resolveString((String) node, unresolved);
        }
        return node;
    }

    private String resolveString(String value, List<String> unresolved) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.find()) {
            return value;
        }

        StringBuilder sb = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            String content = matcher.group(1);
            String varName;
            String defaultValue = null;

            // Split on first ':' to separate var name from default
            int colonIdx = content.indexOf(':');
            if (colonIdx >= 0) {
                varName = content.substring(0, colonIdx);
                defaultValue = content.substring(colonIdx + 1);
            } else {
                varName = content;
            }

            String resolved = environment.getProperty(varName);
            if (resolved != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            } else if (defaultValue != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(defaultValue));
            } else {
                // Leave the placeholder as-is and record as unresolved
                unresolved.add("{{env:" + content + "}}");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Result of placeholder resolution containing the resolved tree and any unresolved placeholders.
     */
    public record ResolvedResult(Object resolved, List<String> unresolved) {
        public boolean hasUnresolved() {
            return !unresolved.isEmpty();
        }
    }
}
