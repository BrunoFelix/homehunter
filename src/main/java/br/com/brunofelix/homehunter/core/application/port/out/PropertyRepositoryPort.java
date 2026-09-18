package br.com.brunofelix.homehunter.core.application.port.out;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.core.domain.model.PropertyStats;
import java.util.List;
import java.util.Optional;

public interface PropertyRepositoryPort {
    Optional<Property> findById(PropertyId id);
    Optional<Property> findBySource(PortalName portalName, String externalId);
    PagedResult<Property> search(PropertySearchCriteria criteria);
    List<String> findAllStates();
    List<String> findCitiesByState(String state);
    List<Property> saveAll(List<Property> properties);
    PropertyStats findStatsByNeighborhood(String state, String city, String neighborhood);
}
