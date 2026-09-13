package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;

public record Area(Double value) {
    public Area {
        if (value == null || value <= 0.0) {
            throw new DomainException("Area must be greater than zero");
        }
    }
}
