package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.time.LocalDateTime;

public record PropertySource(
        Long id,
        PortalName portalName,
        String externalId,
        String url,
        Price price,
        LocalDateTime announcedAt,
        LocalDateTime collectedAt
) {
    public PropertySource {
        if (portalName == null) throw new DomainException("PortalName is required");
        if (externalId == null || externalId.isBlank()) throw new DomainException("ExternalId is required");
        if (url == null || url.isBlank()) throw new DomainException("URL is required");
        if (price == null) throw new DomainException("Price is required");
        if (collectedAt == null) throw new DomainException("CollectedAt is required");
    }
}
