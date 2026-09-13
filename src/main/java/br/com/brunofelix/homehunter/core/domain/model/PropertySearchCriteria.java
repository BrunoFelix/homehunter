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
        Integer bedrooms,
        int page,
        int size
) {
    public PropertySearchCriteria {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        state = state != null ? state.trim().toUpperCase() : "PE";
        city = city != null ? city.trim().toUpperCase() : "RECIFE";
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : null;
    }
}
