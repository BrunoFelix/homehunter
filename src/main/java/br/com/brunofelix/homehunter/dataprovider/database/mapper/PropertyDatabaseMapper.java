package br.com.brunofelix.homehunter.dataprovider.database.mapper;

import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import org.springframework.stereotype.Component;

@Component
public class PropertyDatabaseMapper {

    public PropertyEntity toEntity(Property domain) {
        // Assume domain.getSources() is not empty and pick the first or most recent one
        CollectedProperty source = domain.getSources().get(0); 

        return PropertyEntity.builder()
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
                .portalName(source.portalName().name())
                .externalId(source.externalId())
                .url(source.url())
                .announcedAt(source.announcedAt())
                .collectedAt(source.collectedAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public Property toDomain(PropertyEntity entity) {
        PropertySource source = new PropertySource(
                PortalName.valueOf(entity.getPortalName()),
                entity.getExternalId(),
                entity.getUrl(),
                new Price(entity.getPrice()),
                entity.getBathrooms(),
                entity.getSuites(),
                entity.getParkingSpaces(),
                entity.getCondoFee(),
                entity.getIptu(),
                entity.getAnnouncedAt(),
                entity.getCollectedAt()
        );

        return new Property(
                new PropertyId(entity.getId()),
                entity.getTitle(),
                PropertyType.valueOf(entity.getType()),
                new Price(entity.getPrice()),
                new Area(entity.getArea()),
                new Bedrooms(entity.getBedrooms()),
                new Address(entity.getState(), entity.getCity(), entity.getNeighborhood(), entity.getStreet()),
                List.of(source),
                entity.getBathrooms(),
                entity.getSuites(),
                entity.getParkingSpaces(),
                entity.getCondoFee(),
                entity.getIptu(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
