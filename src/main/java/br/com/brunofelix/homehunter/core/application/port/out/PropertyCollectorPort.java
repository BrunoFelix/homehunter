package br.com.brunofelix.homehunter.core.application.port.out;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import java.util.List;

public interface PropertyCollectorPort {
    PortalName getPortalName();
    List<CollectedProperty> collect(CollectionScope scope);
}
