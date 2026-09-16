package br.com.brunofelix.homehunter.core.domain.model;

import br.com.brunofelix.homehunter.core.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.math.BigDecimal;

public record PropertyId(String value) {
    public PropertyId {
        if (value == null || value.isBlank()) {
            throw new DomainException("PropertyId cannot be blank");
        }
    }

    public static PropertyId generate(String state, String city, String neighborhood, PropertyType type, Double area, Integer bedrooms, Integer bathrooms, BigDecimal price) {
        if (state == null || city == null || type == null || area == null || bedrooms == null || price == null) {
            throw new DomainException("Missing required attributes for PropertyId generation");
        }
        int normBathrooms = bathrooms != null ? bathrooms : 0;
        String normState = state.trim().toUpperCase(Locale.ROOT);
        String normCity = city.trim().toUpperCase(Locale.ROOT);
        String normNeighborhood = neighborhood != null ? neighborhood.trim().toUpperCase(Locale.ROOT) : "";
        String rawKey = String.format("%s|%s|%s|%s|%.1f|%d|%d|%.2f", normState, normCity, normNeighborhood, type, area, bedrooms, normBathrooms, price);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return new PropertyId(hexString.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
