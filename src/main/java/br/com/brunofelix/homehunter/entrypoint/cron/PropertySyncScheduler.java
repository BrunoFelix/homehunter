package br.com.brunofelix.homehunter.entrypoint.cron;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class PropertySyncScheduler {

    private final SyncPropertiesInputPort syncPort;

    public PropertySyncScheduler(SyncPropertiesInputPort syncPort) {
        this.syncPort = syncPort;
    }

    @Scheduled(cron = "${app.collector.cron:0 0 3 * * *}")
    public void scheduledSync() {
        log.info("Scheduled property synchronization triggered.");
        syncPort.sync(new CollectionScope("PE", null));
    }
}
