package br.com.brunofelix.homehunter.core.application.model;

import java.util.List;

public record CollectionScope(
        String state,
        List<String> cities
) {
    public CollectionScope {
        if (state == null || state.isBlank()) state = "PE";
        if (cities == null || cities.isEmpty()) cities = List.of("RECIFE");
    }
}
