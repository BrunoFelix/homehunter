package br.com.brunofelix.homehunter.dataprovider.database.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_property_source", uniqueConstraints = {
        @UniqueConstraint(name = "uk_portal_external", columnNames = {"portal_name", "external_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private PropertyEntity property;

    @Column(name = "portal_name", length = 50, nullable = false)
    private String portalName;

    @Column(name = "external_id", length = 100, nullable = false)
    private String externalId;

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
