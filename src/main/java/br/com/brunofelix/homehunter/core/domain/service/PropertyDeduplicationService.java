package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import java.time.LocalDateTime;
import java.util.Optional;

public class PropertyDeduplicationService {

    public Property deduplicate(Optional<Property> existingProperty, CollectedProperty collected) {
        LocalDateTime now = LocalDateTime.now();
        if (existingProperty.isPresent()) {
            Property property = existingProperty.get();
            property.merge(collected, now);
            return property;
        } else {
            return Property.createFrom(collected, now);
        }
    }
}
