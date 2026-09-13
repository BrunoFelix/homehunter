package br.com.brunofelix.homehunter.entrypoint.rest.dto;

import java.util.List;

public record PagedResultDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
