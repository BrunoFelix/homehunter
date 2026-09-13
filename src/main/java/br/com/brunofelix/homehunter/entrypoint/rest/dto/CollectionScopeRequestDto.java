package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.util.List;

public record CollectionScopeRequestDto(
        String state,
        List<String> cities
) {}
