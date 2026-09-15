package br.com.brunofelix.homehunter.entrypoint.rest;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import br.com.brunofelix.homehunter.entrypoint.rest.dto.ApiResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponseDto> handleDomainException(DomainException ex) {
        return ResponseEntity.badRequest().body(ApiResponseDto.error(ex.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponseDto> handleInvalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponseDto.error("Invalid request parameter or body"));
    }
}