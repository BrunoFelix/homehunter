package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PropertySourceResponseDto(
        Long id,
        String portalName,
        String externalId,
        String url,
        BigDecimal price,
        LocalDateTime announcedAt,
        LocalDateTime collectedAt
) {}
