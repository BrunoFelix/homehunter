package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Property {
    private final PropertyId id;
    private String title;
    private PropertyType type;
    private Price price;
    private Area area;
    private Bedrooms bedrooms;
    private Address address;
    private final List<PropertySource> sources;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Property(PropertyId id, String title, PropertyType type, Price price, Area area, Bedrooms bedrooms, Address address, List<PropertySource> sources, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) throw new DomainException("PropertyId is required");
        if (title == null || title.isBlank()) throw new DomainException("Title is required");
        if (type == null) throw new DomainException("PropertyType is required");
        if (price == null) throw new DomainException("Price is required");
        if (area == null) throw new DomainException("Area is required");
        if (bedrooms == null) throw new DomainException("Bedrooms is required");
        if (address == null) throw new DomainException("Address is required");
        if (sources == null || sources.isEmpty()) throw new DomainException("At least one PropertySource is required");

        this.id = id;
        this.title = title;
        this.type = type;
        this.price = price;
        this.area = area;
        this.bedrooms = bedrooms;
        this.address = address;
        this.sources = new ArrayList<>(sources);
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public static Property createFrom(CollectedProperty collected, LocalDateTime now) {
        PropertyId id = PropertyId.generate(
                collected.address().state(),
                collected.address().city(),
                collected.address().neighborhood(),
                collected.type(),
                collected.area().value(),
                collected.bedrooms().value()
        );

        PropertySource source = new PropertySource(
                null,
                collected.portalName(),
                collected.externalId(),
                collected.url(),
                collected.price(),
                collected.announcedAt(),
                now
        );

        return new Property(
                id,
                collected.title(),
                collected.type(),
                collected.price(),
                collected.area(),
                collected.bedrooms(),
                collected.address(),
                List.of(source),
                now,
                now
        );
    }

    public void merge(CollectedProperty collected, LocalDateTime now) {
        this.title = collected.title();
        this.area = collected.area();
        this.bedrooms = collected.bedrooms();
        this.address = collected.address();

        for (int i = 0; i < sources.size(); i++) {
            PropertySource source = sources.get(i);
            if (source.portalName().equals(collected.portalName()) && source.externalId().equals(collected.externalId())) {
                sources.set(i, new PropertySource(
                        source.id(),
                        collected.portalName(),
                        collected.externalId(),
                        collected.url(),
                        collected.price(),
                        collected.announcedAt(),
                        now
                ));
                recalculateConsolidatedPrice();
                this.updatedAt = now;
                return;
            }
        }

        sources.add(new PropertySource(
                null,
                collected.portalName(),
                collected.externalId(),
                collected.url(),
                collected.price(),
                collected.announcedAt(),
                now
        ));

        recalculateConsolidatedPrice();
        this.updatedAt = now;
    }

    private void recalculateConsolidatedPrice() {
        PropertySource winningSource = sources.stream()
                .min(Comparator
                        .comparing(PropertySource::collectedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PropertySource::announcedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(s -> s.price().value())
                )
                .orElse(sources.get(0));

        this.price = winningSource.price();
    }

    public PropertyId getId() { return id; }
    public String getTitle() { return title; }
    public PropertyType getType() { return type; }
    public Price getPrice() { return price; }
    public Area getArea() { return area; }
    public Bedrooms getBedrooms() { return bedrooms; }
    public Address getAddress() { return address; }
    public List<PropertySource> getSources() { return List.copyOf(sources); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
