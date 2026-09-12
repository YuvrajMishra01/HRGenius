package com.hrgenius.onboarding;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps the default onboarding checklist to/from the ONBOARDINGS.CHECKLIST CLOB.
 *
 * Storage format: JSON array of {@code [{"label": "...", "done": true}, …]}.
 * A null/empty column is read as the default 8-item checklist, all unchecked
 * (matches Phase 6 requirements and keeps pre-V4 rows meaningful).
 */
@Converter
public class ChecklistConverter implements AttributeConverter<List<ChecklistItem>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<ChecklistItem> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize checklist", e);
        }
    }

    @Override
    public List<ChecklistItem> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return ChecklistItem.defaultChecklist();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<ChecklistItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read checklist: " + e.getOriginalMessage(), e);
        }
    }
}
