package br.com.brunofelix.homehunter.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CollectedProperty(
        String title,
        PropertyType type,
        Price price,
        Area area,
        Bedrooms bedrooms,
        Address address,
        PortalName portalName,
        String externalId,
        String url,
        LocalDateTime announcedAt,
        Integer bathrooms,
        Integer suites,
        Integer parkingSpaces,
        BigDecimal condoFee,
        BigDecimal iptu
) {}
