package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import java.util.Optional;

public interface GetPropertyInputPort {
    Optional<Property> getById(PropertyId id);
    Optional<Property> toggleFavorite(PropertyId id);
    Optional<Property> toggleSeen(PropertyId id);
    Optional<Property> updateFavorite(PropertyId id, boolean favorite);
    Optional<Property> updateSeen(PropertyId id, boolean seen);
}