package br.com.brunofelix.homehunter.dataprovider.database.mapper;

import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyImageEntity;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PropertyDatabaseMapper {

    public PropertyEntity toEntity(Property domain) {
        PropertyEntity entity = PropertyEntity.builder()
                .id(domain.getId().value())
                .title(domain.getTitle())
                .type(domain.getType().name())
                .price(domain.getPrice().value())
                .area(domain.getArea().value())
                .bedrooms(domain.getBedrooms().value())
                .bathrooms(domain.getBathrooms())
                .suites(domain.getSuites())
                .parkingSpaces(domain.getParkingSpaces())
                .condoFee(domain.getCondoFee())
                .iptu(domain.getIptu())
                .state(domain.getAddress().state())
                .city(domain.getAddress().city())
                .neighborhood(domain.getAddress().neighborhood())
                .street(domain.getAddress().street())
                .portalName(domain.getPortalName().name())
                .externalId(domain.getExternalId())
                .url(domain.getUrl())
                .announcedAt(domain.getAnnouncedAt())
                .collectedAt(domain.getCollectedAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .favorite(domain.isFavorite())
                .seen(domain.isSeen())
                .build();

        List<PropertyImageEntity> imageEntities = domain.getImages().stream()
                .map(url -> PropertyImageEntity.builder()
                        .property(entity)
                        .imageUrl(url)
                        .build())
                .collect(Collectors.toList());

        entity.setImages(imageEntities);
        return entity;
    }

    public Property toDomain(PropertyEntity entity) {
        List<String> images = entity.getImages() != null ? entity.getImages().stream()
                .map(PropertyImageEntity::getImageUrl)
                .collect(Collectors.toList()) : List.of();

        return new Property(
                new PropertyId(entity.getId() != null ? entity.getId() : "unknown"),
                entity.getTitle() != null ? entity.getTitle() : "Sem Título",
                entity.getType() != null ? PropertyType.valueOf(entity.getType()) : PropertyType.APARTAMENTO,
                new Price(entity.getPrice() != null ? entity.getPrice() : BigDecimal.valueOf(100000)),
                new Area(entity.getArea() != null ? entity.getArea() : 50.0),
                new Bedrooms(entity.getBedrooms() != null ? entity.getBedrooms() : 1),
                new Address(
                        entity.getState() != null ? entity.getState() : "PE",
                        entity.getCity() != null ? entity.getCity() : "Recife",
                        entity.getNeighborhood() != null ? entity.getNeighborhood() : "Centro",
                        entity.getStreet()
                ),
                entity.getBathrooms(),
                entity.getSuites(),
                entity.getParkingSpaces(),
                entity.getCondoFee(),
                entity.getIptu(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now(),
                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : LocalDateTime.now(),
                images,
                entity.getPortalName() != null ? PortalName.valueOf(entity.getPortalName()) : PortalName.ZAP_IMOVEIS,
                entity.getExternalId() != null ? entity.getExternalId() : "ext-0",
                entity.getUrl() != null ? entity.getUrl() : "https://example.com",
                entity.getAnnouncedAt(),
                entity.getCollectedAt() != null ? entity.getCollectedAt() : LocalDateTime.now(),
                entity.isFavorite(),
                entity.isSeen()
        );
    }
}
