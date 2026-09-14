package br.com.brunofelix.homehunter.core.application.config;

import br.com.brunofelix.homehunter.core.application.port.in.GetPropertyInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.application.usecase.GetPropertyUseCaseImpl;
import br.com.brunofelix.homehunter.core.application.usecase.SearchPropertiesUseCaseImpl;
import br.com.brunofelix.homehunter.core.application.usecase.SyncPropertiesUseCaseImpl;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CoreBeanConfiguration {

    @Bean
    public PropertyDeduplicationService propertyDeduplicationService() {
        return new PropertyDeduplicationService();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService syncExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    @Bean
    public SyncPropertiesInputPort syncPropertiesInputPort(
            PropertyRepositoryPort repositoryPort,
            List<PropertyCollectorPort> collectorPorts,
            PropertyDeduplicationService deduplicationService,
            ExecutorService syncExecutor
    ) {
        return new SyncPropertiesUseCaseImpl(repositoryPort, collectorPorts, deduplicationService, syncExecutor);
    }

    @Bean
    public SearchPropertiesInputPort searchPropertiesInputPort(PropertyRepositoryPort repositoryPort) {
        return new SearchPropertiesUseCaseImpl(repositoryPort);
    }

    @Bean
    public GetPropertyInputPort getPropertyInputPort(PropertyRepositoryPort repositoryPort) {
        return new GetPropertyUseCaseImpl(repositoryPort);
    }
}
