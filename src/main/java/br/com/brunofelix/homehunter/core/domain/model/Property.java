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
    private List<String> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Campos anteriormente em PropertySource
    private PortalName portalName;
    private String externalId;
    private String url;
    private LocalDateTime announcedAt;
    private LocalDateTime collectedAt;
    private boolean favorite;
    private boolean seen;

    public Property(PropertyId id, String title, PropertyType type, Price price, Area area, Bedrooms bedrooms, Address address, Integer bathrooms, Integer suites, Integer parkingSpaces, BigDecimal condoFee, BigDecimal iptu, LocalDateTime createdAt, LocalDateTime updatedAt, List<String> images, PortalName portalName, String externalId, String url, LocalDateTime announcedAt, LocalDateTime collectedAt) {
        this(id, title, type, price, area, bedrooms, address, bathrooms, suites, parkingSpaces, condoFee, iptu, createdAt, updatedAt, images, portalName, externalId, url, announcedAt, collectedAt, false, false);
    }

    public Property(PropertyId id, String title, PropertyType type, Price price, Area area, Bedrooms bedrooms, Address address, Integer bathrooms, Integer suites, Integer parkingSpaces, BigDecimal condoFee, BigDecimal iptu, LocalDateTime createdAt, LocalDateTime updatedAt, List<String> images, PortalName portalName, String externalId, String url, LocalDateTime announcedAt, LocalDateTime collectedAt, boolean favorite, boolean seen) {
        if (id == null) throw new DomainException("PropertyId is required");
        if (title == null || title.isBlank()) throw new DomainException("Title is required");
        if (type == null) throw new DomainException("PropertyType is required");
        if (price == null) throw new DomainException("Price is required");
        if (area == null) throw new DomainException("Area is required");
        if (bedrooms == null) throw new DomainException("Bedrooms is required");
        if (address == null) throw new DomainException("Address is required");
        if (portalName == null) throw new DomainException("PortalName is required");
        if (externalId == null || externalId.isBlank()) throw new DomainException("ExternalId is required");
        if (url == null || url.isBlank()) throw new DomainException("URL is required");
        if (price == null) throw new DomainException("Price is required");
        if (collectedAt == null) throw new DomainException("CollectedAt is required");

        this.id = id;
        this.title = title;
        this.type = type;
        this.price = price;
        this.area = area;
        this.bedrooms = bedrooms;
        this.address = address;
        this.images = images != null ? new ArrayList<>(images) : new ArrayList<>();
        this.bathrooms = bathrooms;
        this.suites = suites;
        this.parkingSpaces = parkingSpaces;
        this.condoFee = condoFee;
        this.iptu = iptu;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.portalName = portalName;
        this.externalId = externalId;
        this.url = url;
        this.announcedAt = announcedAt;
        this.collectedAt = collectedAt;
        this.favorite = favorite;
        this.seen = seen;
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

        return new Property(
                id,
                collected.title(),
                collected.type(),
                collected.price(),
                collected.area(),
                collected.bedrooms(),
                collected.address(),
                collected.bathrooms(),
                collected.suites(),
                collected.parkingSpaces(),
                collected.condoFee(),
                collected.iptu(),
                now,
                now,
                collected.images(),
                collected.portalName(),
                collected.externalId(),
                collected.url(),
                collected.announcedAt(),
                now
        );
    }

    public void merge(CollectedProperty collected, LocalDateTime now) {
        this.title = collected.title();
        this.area = collected.area();
        this.bedrooms = collected.bedrooms();
        this.address = collected.address();
        this.images.clear();
        this.images.addAll(collected.images());
        
        this.price = collected.price();
        this.bathrooms = collected.bathrooms();
        this.suites = collected.suites();
        this.parkingSpaces = collected.parkingSpaces();
        this.condoFee = collected.condoFee();
        this.iptu = collected.iptu();
        
        this.portalName = collected.portalName();
        this.externalId = collected.externalId();
        this.url = collected.url();
        this.announcedAt = collected.announcedAt();
        this.collectedAt = now;
        this.updatedAt = now;
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
    public List<String> getImages() { return List.copyOf(images); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public PortalName getPortalName() { return portalName; }
    public String getExternalId() { return externalId; }
    public String getUrl() { return url; }
    public LocalDateTime getAnnouncedAt() { return announcedAt; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public boolean isFavorite() { return favorite; }
    public boolean isSeen() { return seen; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public void setSeen(boolean seen) { this.seen = seen; }
    public void toggleFavorite() { this.favorite = !this.favorite; }
    public void toggleSeen() { this.seen = !this.seen; }
}
