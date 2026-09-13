package br.com.brunofelix.homehunter.core.application.config;

import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyRepositoryPort;
import br.com.brunofelix.homehunter.core.application.usecase.SearchPropertiesUseCaseImpl;
import br.com.brunofelix.homehunter.core.application.usecase.SyncPropertiesUseCaseImpl;
import br.com.brunofelix.homehunter.core.domain.service.PropertyDeduplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class CoreBeanConfiguration {

    @Bean
    public PropertyDeduplicationService propertyDeduplicationService() {
        return new PropertyDeduplicationService();
    }

    @Bean
    public SyncPropertiesInputPort syncPropertiesInputPort(
            PropertyRepositoryPort repositoryPort,
            List<PropertyCollectorPort> collectorPorts,
            PropertyDeduplicationService deduplicationService
    ) {
        return new SyncPropertiesUseCaseImpl(repositoryPort, collectorPorts, deduplicationService);
    }

    @Bean
    public SearchPropertiesInputPort searchPropertiesInputPort(PropertyRepositoryPort repositoryPort) {
        return new SearchPropertiesUseCaseImpl(repositoryPort);
    }
}
