package br.com.brunofelix.homehunter.dataprovider.collector.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateParser {

    private static final DateTimeFormatter CTI_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Recife");

    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }

        // Try CTI format (with conversion)
        try {
            return LocalDateTime.parse(raw, CTI_FORMATTER)
                    .atZone(BRAZIL_ZONE)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {}

        // Try ISO Offset
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {}

        // Try ISO Local (without conversion)
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {}

        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
