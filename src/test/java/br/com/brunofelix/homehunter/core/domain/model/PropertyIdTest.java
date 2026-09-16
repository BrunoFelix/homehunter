package br.com.brunofelix.homehunter.core.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PropertyIdTest {

    @Test
    void shouldGenerateSameIdForIdenticalNormalizedAttributes() {
        PropertyId id1 = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("100000.00"));
        PropertyId id2 = PropertyId.generate("pe", "recife", " boa viagem ", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("100000.00"));

        assertEquals(id1.value(), id2.value());
    }

    @Test
    void shouldGenerateDifferentIdForDifferentAttributes() {
        PropertyId id1 = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("100000.00"));
        PropertyId id2 = PropertyId.generate("PE", "Recife", "Pina", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("100000.00"));

        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void shouldGenerateDifferentIdForDifferentTypes() {
        PropertyId id1 = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("100000.00"));
        PropertyId id2 = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.CASA, 80.0, 3, 1, new BigDecimal("100000.00"));

        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void shouldReturnCorrectLengthForSha256() {
        PropertyId id = PropertyId.generate("PE", "Recife", "Boa Viagem", PropertyType.APARTAMENTO, 80.0, 3, 1, new BigDecimal("100000.00"));
        assertEquals(64, id.value().length());
    }
}
