package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;

public record Address(String state, String city, String neighborhood, String street) {
    public Address {
        if (state == null || state.isBlank()) {
            throw new DomainException("State is required");
        }
        if (city == null || city.isBlank()) {
            throw new DomainException("City is required");
        }
        state = state.trim().toUpperCase();
        city = city.trim().toUpperCase();
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : "";
        street = street != null ? street.trim() : null;
    }
}
