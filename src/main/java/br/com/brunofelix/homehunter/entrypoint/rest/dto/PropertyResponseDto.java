package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PropertyResponseDto(
        String id,
        String title,
        String type,
        BigDecimal price,
        Double area,
        Integer bedrooms,
        Integer bathrooms,
        Integer suites,
        Integer parkingSpaces,
        BigDecimal condoFee,
        BigDecimal iptu,
        String state,
        String city,
        String neighborhood,
        String street,
        String url,
        List<String> images,
        List<PropertySourceResponseDto> sources,
        LocalDateTime announcedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
