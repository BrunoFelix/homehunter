package br.com.brunofelix.homehunter.dataprovider.database.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_property", indexes = {
        @Index(name = "idx_prop_state", columnList = "state"),
        @Index(name = "idx_prop_city", columnList = "city"),
        @Index(name = "idx_prop_neighborhood", columnList = "neighborhood"),
        @Index(name = "idx_prop_type", columnList = "type"),
        @Index(name = "idx_prop_price", columnList = "price")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "area", nullable = false)
    private Double area;

    @Column(name = "bedrooms", nullable = false)
    private Integer bedrooms;

    @Column(name = "bathrooms")
    private Integer bathrooms;

    @Column(name = "suites")
    private Integer suites;

    @Column(name = "parking_spaces")
    private Integer parkingSpaces;

    @Column(name = "condo_fee", precision = 12, scale = 2)
    private BigDecimal condoFee;

    @Column(name = "iptu", precision = 12, scale = 2)
    private BigDecimal iptu;

    @Column(name = "state", length = 2, nullable = false)
    private String state;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "neighborhood", length = 100, nullable = false)
    private String neighborhood;

    @Column(name = "street")
    private String street;

    @Column(name = "portal_name", length = 50, nullable = false)
    private String portalName;

    @Column(name = "external_id", length = 100, nullable = false)
    private String externalId;

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PropertyImageEntity> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
