package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;

import java.util.Locale;
import java.util.function.Predicate;

final class CollectionScopeFilter {

    private CollectionScopeFilter() {
    }

    static Predicate<CollectedProperty> matches(CollectionScope scope) {
        return property -> stateMatches(scope, property) && cityMatches(scope, property);
    }

    private static boolean stateMatches(CollectionScope scope, CollectedProperty property) {
        if (scope == null || scope.state() == null || scope.state().isBlank()) {
            return true;
        }
        return scope.state().equalsIgnoreCase(property.address().state());
    }

    private static boolean cityMatches(CollectionScope scope, CollectedProperty property) {
        if (scope == null || scope.cities() == null || scope.cities().isEmpty()) {
            return true;
        }
        String city = property.address().city();
        return scope.cities().stream()
                .map(c -> c.toUpperCase(Locale.ROOT))
                .anyMatch(c -> c.equals(city));
    }
}