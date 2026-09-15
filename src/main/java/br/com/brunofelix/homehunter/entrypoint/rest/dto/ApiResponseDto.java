package br.com.brunofelix.homehunter.entrypoint.rest.dto;

public record ApiResponseDto(String status, String message) {

    public static ApiResponseDto error(String message) {
        return new ApiResponseDto("error", message);
    }
}