package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;

public record Bedrooms(Integer value) {
    public Bedrooms {
        if (value == null || value < 0) {
            throw new DomainException("Bedrooms cannot be negative");
        }
    }
}
