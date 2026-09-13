package br.com.brunofelix.homehunter.core.domain.model;

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
        LocalDateTime announcedAt
) {}
