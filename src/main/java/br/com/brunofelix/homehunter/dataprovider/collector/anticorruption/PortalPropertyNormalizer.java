package br.com.brunofelix.homehunter.dataprovider.collector.anticorruption;

import br.com.brunofelix.homehunter.core.domain.model.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PortalPropertyNormalizer {

    public CollectedProperty normalize(
            String title,
            String rawType,
            BigDecimal rawPrice,
            Double rawArea,
            Integer rawBedrooms,
            String state,
            String city,
            String neighborhood,
            PortalName portalName,
            String externalId,
            String url,
            LocalDateTime announcedAt
    ) {
        PropertyType type = normalizeType(rawType);
        Price price = new Price(rawPrice != null ? rawPrice : BigDecimal.valueOf(100000));
        Area area = new Area(rawArea != null && rawArea > 0 ? rawArea : 50.0);
        Bedrooms bedrooms = new Bedrooms(rawBedrooms != null && rawBedrooms >= 0 ? rawBedrooms : 1);
        Address address = new Address(
                state != null ? state : "PE",
                city != null ? city : "RECIFE",
                neighborhood != null ? neighborhood : "CENTRO",
                null
        );

        return new CollectedProperty(
                title != null ? title : "Imóvel em " + city,
                type,
                price,
                area,
                bedrooms,
                address,
                portalName,
                externalId,
                url,
                announcedAt
        );
    }

    private PropertyType normalizeType(String rawType) {
        if (rawType == null) return PropertyType.APARTAMENTO;
        String lower = rawType.toLowerCase();
        if (lower.contains("casa")) {
            return PropertyType.CASA;
        }
        return PropertyType.APARTAMENTO;
    }
}
