package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.port.in.GetPropertyInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import java.util.Optional;

public class GetPropertyUseCaseImpl implements GetPropertyInputPort {

    private final PropertyRepositoryPort repositoryPort;

    public GetPropertyUseCaseImpl(PropertyRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public Optional<Property> getById(PropertyId id) {
        return repositoryPort.findById(id);
    }
}