package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.math.BigDecimal;

public record Price(BigDecimal value) {
    public Price {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Price must be greater than zero");
        }
    }
}
