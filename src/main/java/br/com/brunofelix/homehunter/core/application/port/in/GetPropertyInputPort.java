package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import java.util.Optional;

public interface GetPropertyInputPort {
    Optional<Property> getById(PropertyId id);
}