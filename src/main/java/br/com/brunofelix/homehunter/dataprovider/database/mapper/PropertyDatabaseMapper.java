package br.com.brunofelix.homehunter.dataprovider.database.mapper;

import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyImageEntity;
import org.springframework.stereotype.Component;
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
        List<String> images = entity.getImages().stream()
                .map(PropertyImageEntity::getImageUrl)
                .collect(Collectors.toList());

        return new Property(
                new PropertyId(entity.getId()),
                entity.getTitle(),
                PropertyType.valueOf(entity.getType()),
                new Price(entity.getPrice()),
                new Area(entity.getArea()),
                new Bedrooms(entity.getBedrooms()),
                new Address(entity.getState(), entity.getCity(), entity.getNeighborhood(), entity.getStreet()),
                entity.getBathrooms(),
                entity.getSuites(),
                entity.getParkingSpaces(),
                entity.getCondoFee(),
                entity.getIptu(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                images,
                PortalName.valueOf(entity.getPortalName()),
                entity.getExternalId(),
                entity.getUrl(),
                entity.getAnnouncedAt(),
                entity.getCollectedAt()
        );
    }
}
