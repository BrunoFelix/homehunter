package br.com.brunofelix.homehunter.core.domain.model;

import java.math.BigDecimal;

public record PropertySearchCriteria(
        String state,
        String city,
        String neighborhood,
        PropertyType type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minArea,
        Double maxArea,
        BigDecimal maxCondoFee,
        Integer bedrooms,
        Boolean favorite,
        Boolean excludeSeen,
        int page,
        int size,
        String sort
) {
    public PropertySearchCriteria {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        state = (state != null && !state.isBlank()) ? state.trim().toUpperCase() : null;
        city = (city != null && !city.isBlank()) ? city.trim().toUpperCase() : null;
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : null;
    }
}
