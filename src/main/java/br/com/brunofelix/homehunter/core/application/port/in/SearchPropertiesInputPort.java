package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;

import java.util.List;

public interface SearchPropertiesInputPort {
    PagedResult<Property> search(PropertySearchCriteria criteria);
    List<String> getAllStates();
    List<String> getCitiesByState(String state);
}
