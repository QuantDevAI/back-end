package com.filmae.entity.converter;

import com.filmae.entity.OnboardingStep;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OnboardingStepConverter implements AttributeConverter<OnboardingStep, String> {

    @Override
    public String convertToDatabaseColumn(OnboardingStep attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public OnboardingStep convertToEntityAttribute(String dbData) {
        return dbData != null ? OnboardingStep.fromValue(dbData) : null;
    }
}
