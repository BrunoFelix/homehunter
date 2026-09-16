package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import java.util.List;

public class SearchPropertiesUseCaseImpl implements SearchPropertiesInputPort {

    private final PropertyRepositoryPort repositoryPort;

    public SearchPropertiesUseCaseImpl(PropertyRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public PagedResult<Property> search(PropertySearchCriteria criteria) {
        return repositoryPort.search(criteria);
    }

    @Override
    public List<String> getAllStates() {
        return repositoryPort.findAllStates();
    }

    @Override
    public List<String> getCitiesByState(String state) {
        return repositoryPort.findCitiesByState(state);
    }
}
