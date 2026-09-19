package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.Property;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PropertyScoringService {

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 10;

    private static final BigDecimal RATIO_CAP = new BigDecimal("2.0");
    private static final BigDecimal PRICE_WEIGHT = new BigDecimal("2.0");
    private static final BigDecimal CONDO_WEIGHT = BigDecimal.ONE;
    private static final BigDecimal POINT = BigDecimal.ONE;
    private static final BigDecimal NEUTRAL_RATIO = BigDecimal.ONE;
    private static final BigDecimal MIN_AREA_FOR_POINT = new BigDecimal("65");
    private static final int DIVISION_SCALE = 4;

    public Integer calculateScore(Property property, BigDecimal avgPricePerSqm, BigDecimal avgCondoPerSqm) {
        double areaDouble = property.getArea().value();
        if (areaDouble <= 0) return MIN_SCORE;

        BigDecimal area = BigDecimal.valueOf(areaDouble);
        BigDecimal score = BigDecimal.ZERO;

        score = score.add(pricePoints(property, area, avgPricePerSqm));
        score = score.add(condoPoints(property, area, avgCondoPerSqm));
        score = score.add(potentialPoints(property, area));

        int rounded = score.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, rounded));
    }

    private BigDecimal pricePoints(Property property, BigDecimal area, BigDecimal avgPricePerSqm) {
        BigDecimal pricePerSqm = property.getPrice().value().divide(area, DIVISION_SCALE, RoundingMode.HALF_UP);
        if (pricePerSqm.signum() <= 0) {
            return PRICE_WEIGHT.multiply(RATIO_CAP);
        }
        BigDecimal ratio = avgPricePerSqm != null
                ? avgPricePerSqm.divide(pricePerSqm, DIVISION_SCALE, RoundingMode.HALF_UP)
                : NEUTRAL_RATIO;
        return clamp(ratio).multiply(PRICE_WEIGHT);
    }

    private BigDecimal condoPoints(Property property, BigDecimal area, BigDecimal avgCondoPerSqm) {
        BigDecimal condoFee = property.getCondoFee() != null ? property.getCondoFee() : BigDecimal.ZERO;
        if (condoFee.signum() <= 0) {
            return CONDO_WEIGHT.multiply(RATIO_CAP);
        }
        BigDecimal condoPerSqm = condoFee.divide(area, DIVISION_SCALE, RoundingMode.HALF_UP);
        BigDecimal ratio = avgCondoPerSqm != null
                ? avgCondoPerSqm.divide(condoPerSqm, DIVISION_SCALE, RoundingMode.HALF_UP)
                : NEUTRAL_RATIO;
        return clamp(ratio).multiply(CONDO_WEIGHT);
    }

    private BigDecimal potentialPoints(Property property, BigDecimal area) {
        BigDecimal points = BigDecimal.ZERO;
        if (property.getParkingSpaces() != null && property.getParkingSpaces() >= 1) points = points.add(POINT);
        if (property.getBedrooms().value() >= 2) points = points.add(POINT);
        if (area.compareTo(MIN_AREA_FOR_POINT) > 0) points = points.add(POINT);
        if (property.getSuites() != null && property.getSuites() >= 1) points = points.add(POINT);
        return points;
    }

    private static BigDecimal clamp(BigDecimal ratio) {
        return ratio.max(BigDecimal.ZERO).min(RATIO_CAP);
    }
}