package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record SyncRequestDto(
        String state,
        List<String> cities,
        String type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minArea,
        Double maxArea,
        Integer bedrooms,
        String neighborhood
) {}
