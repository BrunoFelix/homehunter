package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GetPropertyUseCaseImplTest {

    private PropertyRepositoryPort repositoryPort;
    private GetPropertyUseCaseImpl getUseCase;
    private Property sampleProperty;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(PropertyRepositoryPort.class);
        getUseCase = new GetPropertyUseCaseImpl(repositoryPort);
        sampleProperty = Property.createFrom(
                new CollectedProperty(
                        "Apto Teste",
                        PropertyType.APARTAMENTO,
                        new Price(BigDecimal.valueOf(200000)),
                        new Area(60.0),
                        new Bedrooms(2),
                        new Address("PE", "Recife", "Boa Viagem", null),
                        PortalName.ZAP_IMOVEIS,
                        "ext-1",
                        "https://zap.com/1",
                        LocalDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                LocalDateTime.now()
        );
    }

    @Test
    void shouldReturnPropertyWhenFound() {
        when(repositoryPort.findById(any())).thenReturn(Optional.of(sampleProperty));

        Optional<Property> result = getUseCase.getById(sampleProperty.getId());

        assertTrue(result.isPresent());
        assertEquals(sampleProperty.getId().value(), result.get().getId().value());
        verify(repositoryPort).findById(sampleProperty.getId());
    }

    @Test
    void shouldReturnEmptyWhenMissing() {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());

        Optional<Property> result = getUseCase.getById(new PropertyId("nao-existe"));

        assertTrue(result.isEmpty());
        verify(repositoryPort).findById(new PropertyId("nao-existe"));
    }
}