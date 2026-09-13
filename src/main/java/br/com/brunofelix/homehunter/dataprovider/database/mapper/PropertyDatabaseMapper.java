package br.com.brunofelix.homehunter.dataprovider.database.mapper;

import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertySourceEntity;
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
                .state(domain.getAddress().state())
                .city(domain.getAddress().city())
                .neighborhood(domain.getAddress().neighborhood())
                .street(domain.getAddress().street())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();

        List<PropertySourceEntity> sourceEntities = domain.getSources().stream()
                .map(s -> PropertySourceEntity.builder()
                        .id(s.id())
                        .property(entity)
                        .portalName(s.portalName().name())
                        .externalId(s.externalId())
                        .url(s.url())
                        .price(s.price().value())
                        .announcedAt(s.announcedAt())
                        .collectedAt(s.collectedAt())
                        .build())
                .collect(Collectors.toList());

        entity.setSources(sourceEntities);
        return entity;
    }

    public Property toDomain(PropertyEntity entity) {
        List<PropertySource> sources = entity.getSources().stream()
                .map(se -> new PropertySource(
                        se.getId(),
                        PortalName.valueOf(se.getPortalName()),
                        se.getExternalId(),
                        se.getUrl(),
                        new Price(se.getPrice()),
                        se.getAnnouncedAt(),
                        se.getCollectedAt()
                ))
                .collect(Collectors.toList());

        return new Property(
                new PropertyId(entity.getId()),
                entity.getTitle(),
                PropertyType.valueOf(entity.getType()),
                new Price(entity.getPrice()),
                new Area(entity.getArea()),
                new Bedrooms(entity.getBedrooms()),
                new Address(entity.getState(), entity.getCity(), entity.getNeighborhood(), entity.getStreet()),
                sources,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
