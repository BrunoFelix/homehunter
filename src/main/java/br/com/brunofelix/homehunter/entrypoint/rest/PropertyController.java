package br.com.brunofelix.homehunter.entrypoint.rest;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.in.GetPropertyInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.ApiResponseDto;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.PagedResultDto;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.PropertyResponseDto;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.SyncRequestDto;
import br.com.brunofelix.homehunter.entrypoint.rest.mapper.PropertyRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Properties", description = "Unified Real Estate Search and Sync API")
public class PropertyController {

    private final SearchPropertiesInputPort searchPort;
    private final SyncPropertiesInputPort syncPort;
    private final GetPropertyInputPort getPort;
    private final PropertyRestMapper mapper;

    public PropertyController(SearchPropertiesInputPort searchPort, SyncPropertiesInputPort syncPort, GetPropertyInputPort getPort, PropertyRestMapper mapper) {
        this.searchPort = searchPort;
        this.syncPort = syncPort;
        this.getPort = getPort;
        this.mapper = mapper;
    }

    @GetMapping("/states")
    @Operation(summary = "Get all available states")
    public ResponseEntity<List<String>> getStates() {
        return ResponseEntity.ok(searchPort.getAllStates());
    }

    @GetMapping("/cities")
    @Operation(summary = "Get all available cities for a state")
    public ResponseEntity<List<String>> getCities(@RequestParam String state) {
        return ResponseEntity.ok(searchPort.getCitiesByState(state));
    }

    @GetMapping
    @Operation(summary = "Search unified properties with filters and pagination")
    public ResponseEntity<PagedResultDto<PropertyResponseDto>> search(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) PropertyType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minArea,
            @RequestParam(required = false) Double maxArea,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        // ... need to handle sort logic
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                state, city, neighborhood, type, minPrice, maxPrice, minArea, maxArea, bedrooms, page, size, sort
        );
        PagedResult<Property> result = searchPort.search(criteria);
        return ResponseEntity.ok(mapper.toPagedDto(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get property details by ID including all portal sources")
    public ResponseEntity<PropertyResponseDto> getById(@PathVariable String id) {
        Optional<Property> property = getPort.getById(new PropertyId(id));
        return property.map(p -> ResponseEntity.ok(mapper.toDto(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    @Operation(summary = "Trigger asynchronous portal synchronization batch with optional pre-storage filters")
    public ResponseEntity<ApiResponseDto> sync(@RequestBody(required = false) SyncRequestDto requestDto) {
        SyncStatus status = syncPort.sync(mapper.toDomain(requestDto));
        if (status == SyncStatus.REJECTED_RUNNING) {
            return ResponseEntity.status(409).body(new ApiResponseDto("rejected", "Synchronization already in progress"));
        }
        return ResponseEntity.accepted().body(new ApiResponseDto("enqueued", "Portal synchronization batch started"));
    }
}
