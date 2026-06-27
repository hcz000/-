package com.example.demo.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Converter
public class JsonbListStringConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("将列表转换为 JSON 时出错", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return List.of();
        }
        try {
            if (dbData.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("将列表转换为 JSON 时出错", e);
        }
    }
}
