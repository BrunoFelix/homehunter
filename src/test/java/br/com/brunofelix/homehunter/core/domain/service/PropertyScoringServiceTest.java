package br.com.brunofelix.homehunter.core.domain.service;

import br.com.brunofelix.homehunter.core.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PropertyScoringServiceTest {

    private final PropertyScoringService scoringService = new PropertyScoringService();

    @Test
    void shouldReturnMaxScoreWhenCheapWithoutCondoAndFullPotential() {
        Property property = property(100.0, 2, 1, 1, null, new BigDecimal("500000"));

        Integer score = scoringService.calculateScore(property, new BigDecimal("10000"), new BigDecimal("100"));

        assertEquals(10, score);
    }

    @Test
    void shouldUseNeutralRatioWhenNoMarketData() {
        Property property = property(100.0, 1, null, null, new BigDecimal("1000"), new BigDecimal("500000"));

        Integer score = scoringService.calculateScore(property, null, null);

        assertEquals(4, score);
    }

    @Test
    void shouldNotGrantPotentialPointAtArea65Exactly() {
        Property atLimit = property(65.0, 1, null, null, null, new BigDecimal("500000"));
        Property above = property(65.1, 1, null, null, null, new BigDecimal("500000"));

        assertEquals(4, scoringService.calculateScore(atLimit, null, null));
        assertEquals(5, scoringService.calculateScore(above, null, null));
    }

    @Test
    void shouldTolerateNullSuitesAndParking() {
        Property property = property(50.0, 1, null, null, null, new BigDecimal("500000"));

        Integer score = scoringService.calculateScore(property, null, null);

        assertEquals(4, score);
    }

    @Test
    void shouldClampToMinScoreForExpensiveProperty() {
        Property property = property(50.0, 1, null, null, new BigDecimal("50000"), new BigDecimal("5000000"));

        Integer score = scoringService.calculateScore(property, new BigDecimal("10000"), new BigDecimal("10"));

        assertEquals(1, score);
    }

    @Test
    void shouldGrantMaxCondoPointsWithoutCondoFee() {
        Property withoutCondo = property(100.0, 1, null, null, null, new BigDecimal("500000"));
        Property withCondo = property(100.0, 1, null, null, new BigDecimal("5000"), new BigDecimal("500000"));

        assertEquals(5, scoringService.calculateScore(withoutCondo, null, new BigDecimal("10")));
        assertEquals(3, scoringService.calculateScore(withCondo, null, new BigDecimal("10")));
    }

    private Property property(double area, int bedrooms, Integer parkingSpaces, Integer suites, BigDecimal condoFee, BigDecimal price) {
        return new Property(
                new PropertyId("test-id"),
                "Apartamento Teste",
                PropertyType.APARTAMENTO,
                new Price(price),
                new Area(area),
                new Bedrooms(bedrooms),
                new Address("PE", "Recife", "Boa Viagem", null),
                2,
                suites,
                parkingSpaces,
                condoFee,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(),
                PortalName.ZAP_IMOVEIS,
                "ext-1",
                "https://zap.com/1",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}