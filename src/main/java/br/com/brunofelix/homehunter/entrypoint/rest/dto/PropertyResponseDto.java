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
        String state,
        String city,
        String neighborhood,
        String street,
        List<PropertySourceResponseDto> sources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
