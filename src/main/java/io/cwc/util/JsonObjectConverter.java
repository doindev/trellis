package io.cwc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for Object &lt;-&gt; JSON String.
 * Used on entity fields typed as Object where Hibernate's @JdbcTypeCode(SqlTypes.JSON)
 * fails because it tries to cast the value to String directly.
 */
@Converter
public class JsonObjectConverter implements AttributeConverter<Object, String> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Object attribute) {
        if (attribute == null) return null;
        if (attribute instanceof String) return (String) attribute;
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    @Override
    public Object convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return mapper.readValue(dbData, Object.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }
}
