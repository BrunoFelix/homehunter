package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncPropertiesUseCaseImplTest {

    private PropertyRepositoryPort repositoryPort;
    private PropertyCollectorPort collectorPort;
    private PropertyDeduplicationService deduplicationService;
    private SyncPropertiesUseCaseImpl syncUseCase;

    @BeforeEach
    void setUp() {
        repositoryPort = mock(PropertyRepositoryPort.class);
        collectorPort = mock(PropertyCollectorPort.class);
        when(collectorPort.getPortalName()).thenReturn(PortalName.ZAP_IMOVEIS);
        when(collectorPort.collect(any())).thenReturn(List.of(
                new CollectedProperty(
                        "Apto Teste",
                        PropertyType.APARTAMENTO,
                        new Price(BigDecimal.valueOf(200000)),
                        new Area(60.0),
                        new Bedrooms(2),
                        new Address("PE", "Recife", "Boa Viagem", null),
                        PortalName.ZAP_IMOVEIS,
                        "ext-1",
                        "https://url.com",
                        LocalDateTime.now()
                )
        ));
        deduplicationService = new PropertyDeduplicationService();
        syncUseCase = new SyncPropertiesUseCaseImpl(repositoryPort, List.of(collectorPort), deduplicationService);
    }

    @Test
    void shouldEnqueueSyncSuccessfully() throws InterruptedException {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncStatus status = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));
        assertEquals(SyncStatus.ENQUEUED, status);

        Thread.sleep(500);
        verify(collectorPort, times(1)).collect(any());
        verify(repositoryPort, times(1)).save(any());
    }

    @Test
    void shouldRejectSyncWhenAlreadyRunning() {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncStatus first = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));
        SyncStatus second = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));

        assertEquals(SyncStatus.ENQUEUED, first);
        assertEquals(SyncStatus.REJECTED_RUNNING, second);
    }
}
