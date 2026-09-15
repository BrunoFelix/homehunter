package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PropertySourceResponseDto(
        Long id,
        String portalName,
        String externalId,
        String url,
        BigDecimal price,
        Integer bathrooms,
        Integer suites,
        Integer parkingSpaces,
        BigDecimal condoFee,
        BigDecimal iptu,
        LocalDateTime announcedAt,
        LocalDateTime collectedAt
) {}
