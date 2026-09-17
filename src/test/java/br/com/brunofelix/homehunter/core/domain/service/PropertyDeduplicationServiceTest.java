package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDeduplicationServiceTest {

    private final PropertyDeduplicationService service = new PropertyDeduplicationService();

    @Test
    void shouldCreateNewPropertyWhenNoneExists() {
        CollectedProperty collected = new CollectedProperty(
                "Apartamento Lindo",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(300000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", "Av Boa Viagem"),
                PortalName.ZAP_IMOVEIS,
                "ext-123",
                "https://zap.com/123",
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        Property property = service.deduplicate(Optional.empty(), collected);

        assertNotNull(property);
        assertEquals("ext-123", property.getExternalId());
        assertEquals(PortalName.ZAP_IMOVEIS, property.getPortalName());
        assertEquals(BigDecimal.valueOf(300000), property.getPrice().value());
    }

    @Test
    void shouldMergeSourceIntoExistingProperty() {
        CollectedProperty existingCollected = new CollectedProperty(
                "Apto Boa Viagem",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(300000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.ZAP_IMOVEIS,
                "ext-123",
                "https://zap.com/123",
                LocalDateTime.now().minusDays(2),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        Property existing = Property.createFrom(existingCollected, LocalDateTime.now().minusDays(2));

        CollectedProperty newCollected = new CollectedProperty(
                "Apartamento Vista Mar",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(290000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.VIVA_REAL,
                "viva-456",
                "https://vivareal.com/456",
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        Property merged = service.deduplicate(Optional.of(existing), newCollected);

        assertEquals("viva-456", merged.getExternalId());
        assertEquals(BigDecimal.valueOf(290000), merged.getPrice().value());
    }

    @Test
    void shouldKeepExistingSourceWhenSamePortalAndExternalId() {
        CollectedProperty existingCollected = new CollectedProperty(
                "Apto Boa Viagem",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(300000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.ZAP_IMOVEIS,
                "ext-123",
                "https://zap.com/123",
                LocalDateTime.now().minusDays(2),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        Property existing = Property.createFrom(existingCollected, LocalDateTime.now().minusDays(2));

        CollectedProperty updatedCollected = new CollectedProperty(
                "Apto Boa Viagem Atualizado",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(310000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.ZAP_IMOVEIS,
                "ext-123",
                "https://zap.com/123-v2",
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        Property merged = service.deduplicate(Optional.of(existing), updatedCollected);

        assertEquals("https://zap.com/123-v2", merged.getUrl());
    }

    @Test
    void shouldPreserveSourceIdWhenMergingSamePortalAndExternalId() {
        LocalDateTime now = LocalDateTime.now();
        Property existing = new Property(
                PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("410000")),
                "Apartamento Exemplo",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(410000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                null,
                null,
                null,
                null,
                null,
                now.minusDays(1),
                now.minusDays(1),
                List.of(),
                PortalName.ZAP_IMOVEIS,
                "zap-sample-01",
                "https://zap.com/sample",
                now.minusDays(1),
                now.minusDays(1)
        );

        CollectedProperty reCollected = new CollectedProperty(
                "Apartamento Exemplo",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(415000)),
                new Area(80.0),
                new Bedrooms(3),
                new Address("PE", "Recife", "Boa Viagem", null),
                PortalName.ZAP_IMOVEIS,
                "zap-sample-01",
                "https://zap.com/sample",
                now,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        Property merged = service.deduplicate(Optional.of(existing), reCollected);

        assertEquals("https://zap.com/sample", merged.getUrl());
    }
}
