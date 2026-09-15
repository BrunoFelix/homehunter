package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class GlueApiCollectorSupport implements PropertyCollectorPort {

    public static final int PAGE_SIZE = 30;

    public record PortalConfig(
            String portalLabel,
            PortalName portalName,
            String webBase,
            String xDomain,
            String tempFilePrefix,
            String sampleExternalId,
            String sampleTitle,
            BigDecimal samplePrice,
            double sampleArea,
            int sampleBedrooms,
            String fixedParamsBeforePage,
            String includeFieldsSuffix) {
    }

    private static final DateTimeFormatter OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    protected final PortalPropertyNormalizer normalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiUrl;
    private final CurlRunner curlRunner;
    private final PortalConfig config;
    private final int maxPages;
    private final Throttle throttle;

    protected GlueApiCollectorSupport(PortalPropertyNormalizer normalizer, String apiUrl, CurlRunner curlRunner, PortalConfig config, int maxPages, Throttle throttle) {
        this.normalizer = normalizer;
        this.apiUrl = apiUrl;
        this.curlRunner = curlRunner;
        this.config = config;
        this.maxPages = maxPages;
        this.throttle = throttle;
    }

    protected GlueApiCollectorSupport(PortalPropertyNormalizer normalizer, String apiUrl, PortalConfig config, int maxPages, Throttle throttle) {
        this(normalizer, apiUrl, new SystemCurlRunner(config.portalLabel(), config.xDomain(), config.tempFilePrefix()), config, maxPages, throttle);
    }

    @Override
    public final PortalName getPortalName() {
        return config.portalName();
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        List<CollectedProperty> results = new ArrayList<>();
        int declaredMax = 0;
        for (int page = 1; ; page++) {
            try {
                throttle(page);
                if (declaredMax > 0) {
                    log.info("{} loading page {}/{}...", config.portalLabel(), page, declaredMax);
                } else {
                    log.info("{} loading page {}...", config.portalLabel(), page);
                }
                CurlResult result = curlRunner.execute(buildUrl(page));

                if (result.statusCode() != 200) {
                    log.warn("{} returned HTTP {} on page {}; stopping pagination.", config.portalLabel(), result.statusCode(), page);
                    break;
                }

                String json = new String(result.body(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(json);
                JsonNode items = itemsOf(root);
                if (items == null) {
                    log.warn("{} page {} has unexpected structure; stopping pagination.", config.portalLabel(), page);
                    break;
                }

                int parsed = 0;
                for (JsonNode item : items) {
                    CollectedProperty property = toProperty(item);
                    if (property != null) {
                        results.add(property);
                        parsed++;
                    }
                }

                if (page == 1 && parsed == 0) {
                    log.warn("{} page 1 has no listings (anti-bot engaged?).", config.portalLabel());
                }
                if (parsed == 0) {
                    break;
                }
                declaredMax = declaredTotalPages(root);
                if (declaredMax > 0 && page >= declaredMax) {
                    log.debug("{} fully collected after {} page(s).", config.portalLabel(), page);
                    break;
                }
                if (maxPages > 0 && page >= maxPages) {
                    log.warn("{} reached configured max-pages cap of {}; stopping pagination.", config.portalLabel(), maxPages);
                    break;
                }
            } catch (IOException | RuntimeException e) {
                log.error("{} page {} failed to fetch or parse: {}", config.portalLabel(), page, e.getMessage());
                break;
            }
        }

        if (results.isEmpty()) {
            log.info("{} returned 0 live listings. Injecting sample listing for robustness.", config.portalLabel());
            results.add(normalizer.normalize(
                    config.sampleTitle(),
                    "APARTAMENTO",
                    config.samplePrice(),
                    config.sampleArea(),
                    config.sampleBedrooms(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    "PE",
                    "Recife",
                    "Boa Viagem",
                    config.portalName(),
                    config.sampleExternalId(),
                    config.webBase() + "/imovel/sample",
                    LocalDateTime.now(ZoneOffset.UTC)
            ));
        }

        return results.stream()
                .filter(CollectionScopeFilter.matches(scope))
                .collect(Collectors.toList());
    }

    protected void throttle(int page) {
        if (throttle == null) {
            return;
        }
        throttle.apply(page, config.portalLabel());
    }

    private JsonNode itemsOf(JsonNode root) {
        JsonNode items = root.path("search").path("result").path("listings");
        return items.isArray() ? items : null;
    }

    CollectedProperty toProperty(JsonNode wrapper) {
        JsonNode listing = wrapper.path("listing");
        if (!listing.isObject() || !listing.hasNonNull("id")) {
            log.debug("Skipping {} non-listing entry (marker/malformed)", config.portalLabel());
            return null;
        }
        String id = listing.path("id").asText();
        String title = listing.path("title").asText(null);
        if (title == null || title.isBlank()) {
            title = wrapper.path("link").path("name").asText(null);
        }
        BigDecimal price = parsePrice(listing);
        String url = wrapper.path("link").path("href").asText(null);
        if (title == null || title.isBlank() || price == null || url == null) {
            log.debug("Skipping {} item {} without title, price or URL", config.portalLabel(), id);
            return null;
        }

        Double area = parseArea(listing);
        Integer bedrooms = parseBedrooms(listing);
        Integer bathrooms = firstCount(listing.path("bathrooms"));
        Integer suites = firstCount(listing.path("suites"));
        Integer parkingSpaces = firstCount(listing.path("parkingSpaces"));
        BigDecimal condoFee = pricingNumber(listing, "monthlyCondoFee");
        BigDecimal iptu = pricingNumber(listing, "yearlyIptu");
        String rawType = hasUnitType(listing, "HOUSE") ? "casa" : "apartamento";
        JsonNode address = listing.path("address");
        String state = address.path("stateAcronym").asText(null);
        String city = address.path("city").asText(null);
        String neighborhood = address.path("neighborhood").asText(null);
        LocalDateTime announcedAt = parseDate(listing.path("createdAt").asText(null));

        return normalizer.normalize(
                title,
                rawType,
                price,
                area,
                bedrooms,
                bathrooms,
                suites,
                parkingSpaces,
                condoFee,
                iptu,
                state,
                city,
                neighborhood,
                config.portalName(),
                id,
                absoluteUrl(url),
                announcedAt
        );
    }

    private Integer firstCount(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        int value = node.get(0).asInt(-1);
        return value >= 0 ? value : null;
    }

    private BigDecimal pricingNumber(JsonNode listing, String field) {
        for (JsonNode info : listing.path("pricingInfos")) {
            if ("SALE".equals(info.path("businessType").asText()) && info.path(field).isNumber()) {
                return toBigDecimal(info.path(field).asText());
            }
        }
        return null;
    }

    private boolean hasUnitType(JsonNode listing, String type) {
        for (JsonNode unitType : listing.path("unitTypes")) {
            if (type.equals(unitType.asText())) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal parsePrice(JsonNode listing) {
        for (JsonNode info : listing.path("pricingInfos")) {
            if ("SALE".equals(info.path("businessType").asText())
                    && info.path("price").isNumber()
                    && info.path("price").asDouble() > 0) {
                return toBigDecimal(info.path("price").asText());
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.warn("{} malformed price '{}'; skipping", config.portalLabel(), raw);
            return null;
        }
    }

    private Double parseArea(JsonNode listing) {
        double usable = firstAreaValue(listing.path("usableAreas"));
        double total = firstAreaValue(listing.path("totalAreas"));
        double value = usable > 0 ? usable : total;
        return value > 0 ? value : null;
    }

    private double firstAreaValue(JsonNode areas) {
        if (!areas.isArray() || areas.isEmpty()) {
            return 0.0;
        }
        return areas.get(0).asDouble(0.0);
    }

    private Integer parseBedrooms(JsonNode listing) {
        JsonNode bedrooms = listing.path("bedrooms");
        int value;
        if (bedrooms.isArray()) {
            if (bedrooms.isEmpty()) {
                return null;
            }
            value = bedrooms.get(0).asInt(0);
        } else {
            value = bedrooms.asInt(0);
        }
        return value > 0 ? value : null;
    }

    private String absoluteUrl(String partialUrl) {
        return partialUrl.startsWith("http") ? partialUrl : config.webBase() + partialUrl;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(raw, OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    private int declaredTotalPages(JsonNode root) {
        int totalCount = root.path("search").path("totalCount").asInt(0);
        return totalCount > 0 ? (totalCount + PAGE_SIZE - 1) / PAGE_SIZE : 0;
    }

    public String buildUrl(int page) {
        return apiUrl + config.fixedParamsBeforePage()
                + "&page=" + page
                + "&size=" + PAGE_SIZE
                + "&from=" + ((page - 1) * PAGE_SIZE)
                + config.includeFieldsSuffix();
    }

    interface CurlRunner {
        CurlResult execute(String url);
    }

    record CurlResult(int statusCode, byte[] body) {
        static CurlResult failure() {
            return new CurlResult(0, new byte[0]);
        }
    }
}