package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.Property;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;

@Service
public class PropertyScoringService {

    public Integer calculateScore(Property property, BigDecimal avgPricePerSqm, BigDecimal avgCondoPerSqm) {
        if (property.getArea().value().doubleValue() <= 0) return 1; // Proteção

        double score = 0.0;

        // 1. Preço (40% - Máx 4 pontos)
        double pricePerSqm = property.getPrice().value().doubleValue() / property.getArea().value().doubleValue();
        double priceRatio = avgPricePerSqm.doubleValue() / pricePerSqm;
        score += Math.max(0, Math.min(priceRatio, 2.0)) * 2.0; 

        // 2. Condomínio (20% - Máx 2 pontos)
        BigDecimal condoFee = property.getCondoFee() != null ? property.getCondoFee() : BigDecimal.ZERO;
        double condoPerSqm = condoFee.doubleValue() / property.getArea().value().doubleValue();
        double condoRatio = avgCondoPerSqm.doubleValue() / (condoPerSqm > 0 ? condoPerSqm : 1.0);
        score += Math.max(0, Math.min(condoRatio, 2.0)) * 1.0;

        // 3. Atualidade (20% - Máx 2 pontos)
        long daysOld = ChronoUnit.DAYS.between(property.getAnnouncedAt(), LocalDateTime.now());
        if (daysOld <= 7) score += 2.0;
        else if (daysOld <= 30) score += 1.0;

        // 4. Potencial (20% - Máx 2 pontos)
        if (property.getParkingSpaces() != null && property.getParkingSpaces() >= 1) score += 1.0;
        if (property.getBedrooms().value() >= 2) score += 1.0;

        return (int) Math.max(1, Math.min(10, Math.round(score)));
    }
}
