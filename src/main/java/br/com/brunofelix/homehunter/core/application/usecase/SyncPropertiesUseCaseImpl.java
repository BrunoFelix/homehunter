package br.com.brunofelix.homehunter.core.application.usecase;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.Property;
import br.com.brunofelix.homehunter.core.domain.model.PropertyId;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SyncPropertiesUseCaseImpl implements SyncPropertiesInputPort {

    private static final Logger log = LoggerFactory.getLogger(SyncPropertiesUseCaseImpl.class);

    private final PropertyRepositoryPort repositoryPort;
    private final List<PropertyCollectorPort> collectorPorts;
    private final PropertyDeduplicationService deduplicationService;
    private final ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public SyncPropertiesUseCaseImpl(PropertyRepositoryPort repositoryPort, List<PropertyCollectorPort> collectorPorts, PropertyDeduplicationService deduplicationService, ExecutorService executorService) {
        this.repositoryPort = repositoryPort;
        this.collectorPorts = collectorPorts;
        this.deduplicationService = deduplicationService;
        this.executorService = executorService;
    }

    @Override
    public SyncStatus sync(CollectionScope scope) {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Sync execution requested, but another sync is already running.");
            return SyncStatus.REJECTED_RUNNING;
        }

        executorService.submit(() -> {
            try {
                log.info("Starting property synchronization batch across portals...");
                for (PropertyCollectorPort collector : collectorPorts) {
                    try {
                        log.info("Collecting properties from portal: {}", collector.getPortalName());
                        List<CollectedProperty> collectedList = collector.collect(scope);
                        if (scope.filter() != null && scope.filter().hasFilter()) {
                            collectedList = collectedList.stream()
                                    .filter(scope.filter()::matches)
                                    .collect(Collectors.toList());
                            log.info("Applied pre-storage filter: {} properties kept from {}", collectedList.size(), collector.getPortalName());
                        }

                        List<Property> consolidatedList = new ArrayList<>();
                        for (CollectedProperty collected : collectedList) {
                            try {
                                PropertyId tempId = PropertyId.generate(
                                        collected.address().state(),
                                        collected.address().city(),
                                        collected.address().neighborhood(),
                                        collected.type(),
                                        collected.area().value(),
                                        collected.bedrooms().value()
                                );
                                Optional<Property> existing = repositoryPort
                                        .findBySource(collected.portalName(), collected.externalId())
                                        .or(() -> repositoryPort.findById(tempId));
                                consolidatedList.add(deduplicationService.deduplicate(existing, collected));
                            } catch (Exception e) {
                                log.error("Failed to process collected property from {}: {}", collector.getPortalName(), e.getMessage(), e);
                            }
                        }

                        repositoryPort.saveAll(consolidatedList);
                        log.info("Committed {} properties from {}", consolidatedList.size(), collector.getPortalName());
                    } catch (Exception e) {
                        log.error("Portal collector failed for {}: {}", collector.getPortalName(), e.getMessage(), e);
                    }
                }
                log.info("Property synchronization batch completed successfully.");
            } finally {
                isRunning.set(false);
            }
        });

        return SyncStatus.ENQUEUED;
    }
}
