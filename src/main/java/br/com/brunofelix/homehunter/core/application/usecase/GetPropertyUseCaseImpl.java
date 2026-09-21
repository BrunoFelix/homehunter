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

    @Override
    public Optional<Property> toggleFavorite(PropertyId id) {
        return repositoryPort.findById(id).map(p -> {
            p.toggleFavorite();
            return repositoryPort.save(p);
        });
    }

    @Override
    public Optional<Property> toggleSeen(PropertyId id) {
        return repositoryPort.findById(id).map(p -> {
            p.toggleSeen();
            return repositoryPort.save(p);
        });
    }

    @Override
    public Optional<Property> updateFavorite(PropertyId id, boolean favorite) {
        return repositoryPort.findById(id).map(p -> {
            p.setFavorite(favorite);
            return repositoryPort.save(p);
        });
    }

    @Override
    public Optional<Property> updateSeen(PropertyId id, boolean seen) {
        return repositoryPort.findById(id).map(p -> {
            p.setSeen(seen);
            return repositoryPort.save(p);
        });
    }
}