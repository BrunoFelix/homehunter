package br.com.brunofelix.homehunter.entrypoint.rest.mapper;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.core.domain.model.SyncFilterCriteria;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.PagedResultDto;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.PropertyResponseDto;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.SyncRequestDto;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PropertyRestMapper {

    public PropertyResponseDto toDto(Property property) {
        return new PropertyResponseDto(
                property.getId().value(),
                property.getTitle(),
                property.getType().name(),
                property.getPrice().value(),
                property.getArea().value(),
                property.getBedrooms().value(),
                property.getBathrooms(),
                property.getSuites(),
                property.getParkingSpaces(),
                property.getCondoFee(),
                property.getIptu(),
                property.getAddress().state(),
                property.getAddress().city(),
                property.getAddress().neighborhood(),
                property.getAddress().street(),
                property.getUrl(),
                property.getImages(),
                property.getAnnouncedAt(),
                property.getCreatedAt(),
                property.getUpdatedAt(),
                property.getPortalName().name(),
                property.getExternalId()
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

    public CollectionScope toDomain(SyncRequestDto dto) {
        if (dto == null) {
            return new CollectionScope("PE", null, null);
        }
        SyncFilterCriteria filter = new SyncFilterCriteria(
                dto.type() != null ? PropertyType.valueOf(dto.type().toUpperCase()) : null,
                dto.minPrice(),
                dto.maxPrice(),
                dto.minArea(),
                dto.maxArea(),
                dto.bedrooms(),
                dto.neighborhood()
        );
        return new CollectionScope(dto.state(), dto.cities(), filter);
    }
}
