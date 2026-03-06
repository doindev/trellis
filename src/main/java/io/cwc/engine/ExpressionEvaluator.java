package io.cwc.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionEvaluator {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("=?\\{\\{(.+?)\\}\\}", Pattern.DOTALL);
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public Object resolveExpressions(Object value, ExpressionContext ctx) {
        if (value instanceof String str) {
            return resolveString(str, ctx);
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolved.put(entry.getKey(), resolveExpressions(entry.getValue(), ctx));
            }
            return resolved;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            return list.stream()
                    .map(item -> resolveExpressions(item, ctx))
                    .toList();
        }
        return value;
    }

    private Object resolveString(String str, ExpressionContext ctx) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(str);

        if (matcher.matches()) {
            String expression = matcher.group(1).trim();
            return evaluateExpression(expression, ctx);
        }

        if (!matcher.find()) {
            return str;
        }

        matcher.reset();
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Object evaluated = evaluateExpression(expression, ctx);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    evaluated != null ? String.valueOf(evaluated) : ""));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object evaluateExpression(String expression, ExpressionContext ctx) {
        try (Context jsContext = Context.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {

            StringBuilder setup = new StringBuilder();
            setup.append("var $json = ").append(toJson(ctx.getCurrentItemData())).append(";\n");
            setup.append("var $input = { ");
            setup.append("first: function() { return { json: ").append(toJson(getFirst(ctx.getInputItems()))).append(" }; }, ");
            setup.append("all: function() { return ").append(toJson(wrapItems(ctx.getInputItems()))).append("; }, ");
            setup.append("item: { json: ").append(toJson(ctx.getCurrentItemData())).append(" }");
            setup.append(" };\n");

            setup.append("var $node = {};\n");
            if (ctx.getNodeOutputs() != null) {
                for (Map.Entry<String, Object> entry : ctx.getNodeOutputs().entrySet()) {
                    setup.append("$node['").append(entry.getKey().replace("'", "\\'"))
                            .append("'] = { json: ").append(toJson(entry.getValue())).append(" };\n");
                }
            }

            // $('Node Name') function — returns { item: { json: ... }, first: fn, all: fn }
            setup.append("function $(name) { var d = $node[name] || { json: {} }; ");
            setup.append("return { item: d, first: function() { return d; }, json: d.json }; }\n");

            setup.append("var $env = ").append(toJson(ctx.getEnvVars() != null ? ctx.getEnvVars() : Map.of())).append(";\n");
            setup.append("var $vars = ").append(toJson(ctx.getVariables() != null ? ctx.getVariables() : Map.of())).append(";\n");

            setup.append("var $execution = { id: '").append(ctx.getExecutionId() != null ? ctx.getExecutionId() : "").append("' };\n");
            setup.append("var $now = '").append(Instant.now().toString()).append("';\n");
            setup.append("var $today = '").append(LocalDate.now().toString()).append("';\n");
            setup.append("var $runIndex = ").append(ctx.getRunIndex()).append(";\n");

            String fullScript = setup + "(" + expression + ")";

            if (log.isDebugEnabled()) {
                log.debug("Expression script for '{}':\n{}", expression, fullScript);
            }

            Value result = jsContext.eval("js", fullScript);
            return convertValue(result);
        } catch (Exception e) {
            String jsonKeys = ctx.getCurrentItemData() != null
                    ? ctx.getCurrentItemData().keySet().toString() : "null";
            String nodeNames = ctx.getNodeOutputs() != null
                    ? ctx.getNodeOutputs().keySet().toString() : "null";
            log.warn("Expression evaluation failed for '{}': {} | $json keys={}, $node names={}",
                    expression, e.getMessage(), jsonKeys, nodeNames);
            return "{{" + expression + "}}";
        }
    }

    private Object convertValue(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            return value.asDouble();
        }
        if (value.isString()) return value.asString();
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < value.getArraySize(); i++) {
                list.add(convertValue(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValue(value.getMember(key)));
            }
            return map;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFirst(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return Map.of();
        Map<String, Object> first = items.get(0);
        Object json = first.get("json");
        if (json instanceof Map) return (Map<String, Object>) json;
        return first;
    }

    private List<Map<String, Object>> wrapItems(List<Map<String, Object>> items) {
        if (items == null) return List.of();
        return items;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj != null ? obj : Map.of());
        } catch (Exception e) {
            log.warn("toJson serialization failed for {}: {}",
                    obj != null ? obj.getClass().getSimpleName() : "null", e.getMessage());
            return "{}";
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ExpressionContext {
        private Map<String, Object> currentItemData;
        private List<Map<String, Object>> inputItems;
        private Map<String, Object> nodeOutputs;
        private Map<String, String> envVars;
        private Map<String, String> variables;
        private String executionId;
        private int runIndex;
    }
}
