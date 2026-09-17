package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.math.BigDecimal;
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
    private Integer bathrooms;
    private Integer suites;
    private Integer parkingSpaces;
    private BigDecimal condoFee;
    private BigDecimal iptu;
    private Address address;
    private final List<PropertySource> sources;
    private final List<String> images;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Property(PropertyId id, String title, PropertyType type, Price price, Area area, Bedrooms bedrooms, Address address, List<PropertySource> sources, Integer bathrooms, Integer suites, Integer parkingSpaces, BigDecimal condoFee, BigDecimal iptu, LocalDateTime createdAt, LocalDateTime updatedAt, List<String> images) {
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
        this.images = images != null ? new ArrayList<>(images) : new ArrayList<>();
        this.bathrooms = bathrooms;
        this.suites = suites;
        this.parkingSpaces = parkingSpaces;
        this.condoFee = condoFee;
        this.iptu = iptu;
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
                collected.bedrooms().value(),
                collected.bathrooms(),
                collected.price().value()
        );

        PropertySource source = new PropertySource(
                null,
                collected.portalName(),
                collected.externalId(),
                collected.url(),
                collected.price(),
                collected.bathrooms(),
                collected.suites(),
                collected.parkingSpaces(),
                collected.condoFee(),
                collected.iptu(),
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
                collected.bathrooms(),
                collected.suites(),
                collected.parkingSpaces(),
                collected.condoFee(),
                collected.iptu(),
                now,
                now,
                collected.images()
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
                        collected.bathrooms(),
                        collected.suites(),
                        collected.parkingSpaces(),
                        collected.condoFee(),
                        collected.iptu(),
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
                collected.bathrooms(),
                collected.suites(),
                collected.parkingSpaces(),
                collected.condoFee(),
                collected.iptu(),
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
        this.bathrooms = winningSource.bathrooms();
        this.suites = winningSource.suites();
        this.parkingSpaces = winningSource.parkingSpaces();
        this.condoFee = winningSource.condoFee();
        this.iptu = winningSource.iptu();
    }

    public PropertyId getId() { return id; }
    public String getTitle() { return title; }
    public PropertyType getType() { return type; }
    public Price getPrice() { return price; }
    public Area getArea() { return area; }
    public Bedrooms getBedrooms() { return bedrooms; }
    public Integer getBathrooms() { return bathrooms; }
    public Integer getSuites() { return suites; }
    public Integer getParkingSpaces() { return parkingSpaces; }
    public BigDecimal getCondoFee() { return condoFee; }
    public BigDecimal getIptu() { return iptu; }
    public Address getAddress() { return address; }
    public List<PropertySource> getSources() { return List.copyOf(sources); }
    public List<String> getImages() { return List.copyOf(images); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
