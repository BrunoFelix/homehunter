package br.com.brunofelix.homehunter.dataprovider.database.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_property_image", indexes = {
        @Index(name = "idx_img_property", columnList = "property_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private PropertyEntity property;

    @Column(name = "image_url", columnDefinition = "TEXT", nullable = false)
    private String imageUrl;
}
