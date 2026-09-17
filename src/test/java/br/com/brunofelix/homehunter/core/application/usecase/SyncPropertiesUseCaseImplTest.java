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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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
                        LocalDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )
        ));
        deduplicationService = new PropertyDeduplicationService();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        syncUseCase = new SyncPropertiesUseCaseImpl(repositoryPort, List.of(collectorPort), deduplicationService, executorService);
    }

    @Test
    void shouldEnqueueSyncSuccessfully() throws InterruptedException {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.findBySource(any(), any())).thenReturn(Optional.empty());
        when(repositoryPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncStatus status = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));
        assertEquals(SyncStatus.ENQUEUED, status);

        Thread.sleep(500);
        verify(collectorPort, times(1)).collect(any());
        verify(repositoryPort, times(1)).saveAll(any());
    }

    @Test
    void shouldMergeIntoPropertyFoundBySourceWhenFingerprintMisses() throws InterruptedException {
        Property existing = new Property(
                new PropertyId("old-fingerprint"),
                "Apartamento Exemplo",
                PropertyType.APARTAMENTO,
                new Price(BigDecimal.valueOf(200000)),
                new Area(60.0),
                new Bedrooms(2),
                new Address("PE", "Recife", "Boa Viagem", null),
                List.of(new PropertySource(
                        42L,
                        PortalName.ZAP_IMOVEIS,
                        "ext-1",
                        "https://zap.com/1",
                        new Price(BigDecimal.valueOf(200000)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )),
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of()
        );
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.findBySource(PortalName.ZAP_IMOVEIS, "ext-1")).thenReturn(Optional.of(existing));
        when(repositoryPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncStatus status = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));
        assertEquals(SyncStatus.ENQUEUED, status);

        Thread.sleep(500);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Property>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositoryPort).saveAll(captor.capture());
        List<Property> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("old-fingerprint", saved.get(0).getId().value());
        assertEquals(42L, saved.get(0).getSources().get(0).id());
    }

    @Test
    void shouldRejectSyncWhenAlreadyRunning() {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.findBySource(any(), any())).thenReturn(Optional.empty());
        when(repositoryPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncStatus first = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));
        SyncStatus second = syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));

        assertEquals(SyncStatus.ENQUEUED, first);
        assertEquals(SyncStatus.REJECTED_RUNNING, second);
    }

    @Test
    void shouldDeduplicateRepeatedSourceWithinSameBatch() throws InterruptedException {
        when(repositoryPort.findById(any())).thenReturn(Optional.empty());
        when(repositoryPort.findBySource(any(), any())).thenReturn(Optional.empty());
        when(repositoryPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

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
                        LocalDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ),
                new CollectedProperty(
                        "Apto Teste (repetido em outra página, fingerprint diferente)",
                        PropertyType.APARTAMENTO,
                        new Price(BigDecimal.valueOf(200000)),
                        new Area(61.0),
                        new Bedrooms(2),
                        new Address("PE", "Recife", "Boa Viagem", null),
                        PortalName.ZAP_IMOVEIS,
                        "ext-1",
                        "https://url.com",
                        LocalDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )
        ));

        syncUseCase.sync(new CollectionScope("PE", List.of("Recife"), null));
        Thread.sleep(500);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Property>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositoryPort).saveAll(captor.capture());
        List<Property> saved = captor.getValue();
        assertEquals(1, saved.size(), "same portal+externalId repeated in the batch must yield a single property");
        assertEquals(1, saved.get(0).getSources().size());
        assertEquals("ext-1", saved.get(0).getSources().get(0).externalId());
    }
}
