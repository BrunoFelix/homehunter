package br.com.brunofelix.homehunter.core.domain.model;

import java.math.BigDecimal;

public record SyncFilterCriteria(
        PropertyType type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minArea,
        Double maxArea,
        Integer bedrooms,
        String neighborhood
) {
    public SyncFilterCriteria {
        neighborhood = neighborhood != null ? neighborhood.trim().toUpperCase() : null;
    }

    public boolean hasFilter() {
        return type != null || minPrice != null || maxPrice != null || minArea != null || maxArea != null || bedrooms != null || neighborhood != null;
    }

    public boolean matches(CollectedProperty collected) {
        if (type != null && !collected.type().equals(type)) return false;
        if (minPrice != null && collected.price().value().compareTo(minPrice) < 0) return false;
        if (maxPrice != null && collected.price().value().compareTo(maxPrice) > 0) return false;
        if (minArea != null && collected.area().value() < minArea) return false;
        if (maxArea != null && collected.area().value() > maxArea) return false;
        if (bedrooms != null && !collected.bedrooms().value().equals(bedrooms)) return false;
        if (neighborhood != null && !collected.address().neighborhood().equals(neighborhood)) return false;
        return true;
    }
}
