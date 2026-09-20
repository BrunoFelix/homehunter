package br.com.brunofelix.homehunter.dataprovider.database;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.core.domain.model.PropertyStats;
import br.com.brunofelix.homehunter.dataprovider.database.entity.PropertyEntity;
import br.com.brunofelix.homehunter.dataprovider.database.mapper.PropertyDatabaseMapper;
import br.com.brunofelix.homehunter.dataprovider.database.repository.SpringDataPropertyRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PropertyRepositoryAdapter implements PropertyRepositoryPort {

    private static final int DIVISION_SCALE = 4;

    private final SpringDataPropertyRepository repository;
    private final PropertyDatabaseMapper mapper;
    private final NeighborhoodStatsCache statsCache;

    public PropertyRepositoryAdapter(SpringDataPropertyRepository repository, PropertyDatabaseMapper mapper, NeighborhoodStatsCache statsCache) {
        this.repository = repository;
        this.mapper = mapper;
        this.statsCache = statsCache;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Property> findById(PropertyId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Property> findBySource(PortalName portalName, String externalId) {
        return repository.findBySource(portalName.name(), externalId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Property> search(PropertySearchCriteria criteria) {
        Sort sort = Sort.by("announcedAt").descending();
        if (criteria.sort() != null && !criteria.sort().isBlank()) {
            String[] parts = criteria.sort().split(",");
            if (parts.length == 2) {
                sort = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
            }
        }
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), sort);

        Specification<PropertyEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.state() != null && !criteria.state().isBlank()) {
                predicates.add(cb.equal(root.get("state"), criteria.state()));
            }
            if (criteria.city() != null && !criteria.city().isBlank()) {
                predicates.add(cb.equal(root.get("city"), criteria.city()));
            }
            if (criteria.neighborhood() != null && !criteria.neighborhood().isBlank()) {
                predicates.add(cb.equal(root.get("neighborhood"), criteria.neighborhood()));
            }
            if (criteria.type() != null) {
                predicates.add(cb.equal(root.get("type"), criteria.type().name()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            if (criteria.minArea() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("area"), criteria.minArea()));
            }
            if (criteria.maxArea() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("area"), criteria.maxArea()));
            }
            if (criteria.maxCondoFee() != null) {
                predicates.add(cb.or(
                    cb.lessThanOrEqualTo(root.get("condoFee"), criteria.maxCondoFee()),
                    cb.isNull(root.get("condoFee"))
                ));
            }
            if (criteria.bedrooms() != null) {
                predicates.add(cb.equal(root.get("bedrooms"), criteria.bedrooms()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PropertyEntity> page = repository.findAll(spec, pageRequest);
        List<Property> content = page.getContent().stream().map(mapper::toDomain).collect(Collectors.toList());

        return new PagedResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAllStates() {
        return repository.findAllStates();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findCitiesByState(String state) {
        return repository.findCitiesByState(state);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyStats findStatsByNeighborhood(String state, String city, String neighborhood) {
        PropertyStats cached = statsCache.get(state, city, neighborhood);
        if (cached != null) return cached;

        List<PropertyEntity> props = repository.findByNeighborhood(state, city, neighborhood);
        BigDecimal sumPricePerSqm = BigDecimal.ZERO;
        BigDecimal sumCondoPerSqm = BigDecimal.ZERO;
        int priceCount = 0;
        int condoCount = 0;

        for (PropertyEntity p : props) {
            if (p.getArea() == null || p.getArea() <= 0 || p.getPrice() == null) continue;
            BigDecimal area = BigDecimal.valueOf(p.getArea());
            sumPricePerSqm = sumPricePerSqm.add(p.getPrice().divide(area, DIVISION_SCALE, RoundingMode.HALF_UP));
            priceCount++;
            if (p.getCondoFee() != null) {
                sumCondoPerSqm = sumCondoPerSqm.add(p.getCondoFee().divide(area, DIVISION_SCALE, RoundingMode.HALF_UP));
                condoCount++;
            }
        }

        PropertyStats stats;
        if (priceCount == 0) {
            stats = new PropertyStats(null, null);
        } else {
            BigDecimal avgPrice = sumPricePerSqm.divide(BigDecimal.valueOf(priceCount), DIVISION_SCALE, RoundingMode.HALF_UP);
            BigDecimal avgCondo = condoCount > 0
                    ? sumCondoPerSqm.divide(BigDecimal.valueOf(condoCount), DIVISION_SCALE, RoundingMode.HALF_UP)
                    : null;
            stats = new PropertyStats(avgPrice, avgCondo);
        }

        statsCache.put(state, city, neighborhood, stats);
        return stats;
    }

    @Override
    @Transactional
    public List<Property> saveAll(List<Property> properties) {
        List<PropertyEntity> entities = properties.stream()
                .map(mapper::toEntity)
                .collect(Collectors.toList());
        List<Property> saved = repository.saveAll(entities).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
        statsCache.clear();
        return saved;
    }
}
