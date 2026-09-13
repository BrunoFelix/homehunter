package br.com.brunofelix.homehunter.entrypoint.rest.mapper;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.*;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class PropertyRestMapper {

    public PropertyResponseDto toDto(Property property) {
        var sources = property.getSources().stream()
                .map(s -> new PropertySourceResponseDto(
                        s.id(),
                        s.portalName().name(),
                        s.externalId(),
                        s.url(),
                        s.price().value(),
                        s.announcedAt(),
                        s.collectedAt()
                ))
                .collect(Collectors.toList());

        return new PropertyResponseDto(
                property.getId().value(),
                property.getTitle(),
                property.getType().name(),
                property.getPrice().value(),
                property.getArea().value(),
                property.getBedrooms().value(),
                property.getAddress().state(),
                property.getAddress().city(),
                property.getAddress().neighborhood(),
                property.getAddress().street(),
                sources,
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }

    public PagedResultDto<PropertyResponseDto> toPagedDto(PagedResult<Property> pagedResult) {
        var dtos = pagedResult.content().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PagedResultDto<>(
                dtos,
                pagedResult.page(),
                pagedResult.size(),
                pagedResult.totalElements(),
                pagedResult.totalPages()
        );
    }

    public CollectionScope toDomain(CollectionScopeRequestDto dto) {
        if (dto == null) {
            return new CollectionScope("PE", null);
        }
        return new CollectionScope(dto.state(), dto.cities());
    }
}
