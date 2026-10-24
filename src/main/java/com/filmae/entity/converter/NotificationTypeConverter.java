package com.filmae.entity.converter;

import com.filmae.entity.NotificationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
        return dbData != null ? NotificationType.fromValue(dbData) : null;
    }
}
