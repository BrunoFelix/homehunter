package br.com.brunofelix.homehunter.core.application.port.out;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import java.util.Optional;

public interface PropertyRepositoryPort {
    Optional<Property> findById(PropertyId id);
    PagedResult<Property> search(PropertySearchCriteria criteria);
    Property save(Property property);
}
