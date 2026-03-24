package io.cwc.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Obfuscates sensitive values in execution data before persistence.
 * Returns a deep-copied structure — the original in-memory data is untouched.
 */
public final class ExecutionDataObfuscator {

    private static final String OBFUSCATED = "******";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie",
            "x-api-key", "x-auth-token",
            "proxy-authorization", "www-authenticate"
    );

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "accesstoken", "refreshtoken", "idtoken",
            "apikey", "secret", "clientsecret",
            "password", "passwd", "token",
            "sessionid", "sessiontoken",
            "privatekey", "connectionstring"
    );

    private ExecutionDataObfuscator() {}

    public static Object obfuscate(Object data) {
        if (data instanceof Map<?, ?> map) {
            return obfuscateMap(map);
        }
        if (data instanceof List<?> list) {
            return obfuscateList(list);
        }
        return data;
    }

    private static Map<String, Object> obfuscateMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if ("headers".equals(key) && value instanceof Map<?, ?> headersMap) {
                result.put(key, obfuscateHeaders(headersMap));
            } else if (SENSITIVE_FIELDS.contains(normalize(key))) {
                result.put(key, OBFUSCATED);
            } else {
                result.put(key, obfuscate(value));
            }
        }
        return result;
    }

    private static Map<String, Object> obfuscateHeaders(Map<?, ?> headers) {
        Map<String, Object> result = new LinkedHashMap<>(headers.size());
        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (SENSITIVE_HEADERS.contains(key.toLowerCase())) {
                result.put(key, OBFUSCATED);
            } else {
                result.put(key, obfuscate(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Object> obfuscateList(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(obfuscate(item));
        }
        return result;
    }

    private static String normalize(String key) {
        return key.toLowerCase().replace("_", "");
    }
}
