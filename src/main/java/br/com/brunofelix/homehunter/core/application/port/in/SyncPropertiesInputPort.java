package br.com.brunofelix.homehunter.core.application.port.in;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;

public interface SyncPropertiesInputPort {
    SyncStatus sync(CollectionScope scope);
}
