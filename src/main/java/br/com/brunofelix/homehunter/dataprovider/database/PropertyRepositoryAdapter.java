package br.com.brunofelix.homehunter.dataprovider.database;

import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.model.PropertySearchCriteria;
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

@Component
public class PropertyRepositoryAdapter implements PropertyRepositoryPort {

    private final SpringDataPropertyRepository repository;
    private final PropertyDatabaseMapper mapper;

    public PropertyRepositoryAdapter(SpringDataPropertyRepository repository, PropertyDatabaseMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Property> findById(PropertyId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Property> search(PropertySearchCriteria criteria) {
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by("createdAt").descending());

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
    @Transactional
    public Property save(Property property) {
        PropertyEntity entity = mapper.toEntity(property);
        PropertyEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
