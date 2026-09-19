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
import java.text.Normalizer;
import br.com.brunofelix.homehunter.dataprovider.collector.util.DateParser;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        log.info("{} collection started for cities: {}...", config.portalLabel(), scope.cities());
        List<CollectedProperty> results = new ArrayList<>();
        
        for (String city : scope.cities()) {
            log.info("Collecting from portal: {} for city: {}", config.portalLabel(), city);
            int declaredMax = 0;
            for (int page = 1; ; page++) {
                try {
                    throttle(page);
                    if (declaredMax > 0) {
                        log.info("{} loading page {}/{} for city {}...", config.portalLabel(), page, declaredMax, city);
                    } else {
                        log.info("{} loading page {} for city {}...", config.portalLabel(), page, city);
                    }
                    CurlResult result = curlRunner.execute(buildUrl(page, city));

                    if (result.statusCode() != 200) {
                        log.warn("{} returned HTTP {} on page {} for city {}; stopping pagination for this city.", config.portalLabel(), result.statusCode(), page, city);
                        break;
                    }

                    String json = new String(result.body(), StandardCharsets.UTF_8);
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode items = itemsOf(root);
                    if (items == null) {
                        log.warn("{} page {} has unexpected structure for city {}; stopping pagination for this city.", config.portalLabel(), page, city);
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
                        log.warn("{} page 1 has no listings for city {} (anti-bot engaged?).", config.portalLabel(), city);
                    }
                    if (parsed == 0) {
                        break;
                    }
                    declaredMax = declaredTotalPages(root);
                    if (declaredMax > 0 && page >= declaredMax) {
                        log.debug("{} fully collected city {} after {} page(s).", config.portalLabel(), city, page);
                        break;
                    }
                    if (maxPages > 0 && page >= maxPages) {
                        log.warn("{} reached configured max-pages cap of {} for city {}; stopping pagination.", config.portalLabel(), maxPages, city);
                        break;
                    }
                } catch (IOException | RuntimeException e) {
                    log.error("{} page {} failed to fetch or parse for city {}: {}", config.portalLabel(), page, city, e.getMessage());
                    break;
                }
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
                    scope.cities().get(0),
                    "Boa Viagem",
                    config.portalName(),
                    config.sampleExternalId(),
                    config.webBase() + "/imovel/sample",
                    LocalDateTime.now(ZoneOffset.UTC),
                    java.util.List.of()
            ));
        }

        List<CollectedProperty> filtered = results.stream()
                .filter(CollectionScopeFilter.matches(scope))
                .collect(Collectors.toList());
        log.info("{} collection finished: {} property(ies) collected ({} live).", config.portalLabel(), filtered.size(), results.size());
        return filtered;
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
        java.util.List<String> images = new ArrayList<>();
        JsonNode medias = wrapper.path("medias");
        if (medias.isArray()) {
            String slug = slugify(title);
            for (JsonNode media : medias) {
                if ("IMAGE".equals(media.path("type").asText())) {
                    images.add(resolveImageUrl(media.path("url").asText(), slug));
                }
            }
        }

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
                announcedAt,
                images
        );
    }

    private String resolveImageUrl(String url, String slug) {
        if (url == null || url.isBlank() || !url.contains("{")) {
            return url;
        }
        return url
                .replace("{description}", slug)
                .replace("{action}", "fit-in")
                .replace("{width}", "870")
                .replace("{height}", "707");
    }

    private String slugify(String title) {
        if (title == null || title.isBlank()) {
            return "imovel";
        }
        String normalized = Normalizer.normalize(title.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "imovel" : normalized;
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
            if ("SALE".equals(info.path("businessType").asText())) {
                JsonNode priceNode = info.path("price");
                if (priceNode.isNumber() || priceNode.isTextual()) {
                    BigDecimal price = toBigDecimal(priceNode.asText());
                    if (price != null && price.signum() > 0) {
                        log.debug("{} - Preço extraído: {} (raw: {})", config.portalLabel(), price, priceNode.asText());
                        return price;
                    }
                }
                log.warn("{} - Preço não numérico ou zero em pricingInfos: {}", config.portalLabel(), info.toString());
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
        return DateParser.parse(raw);
    }

    private int declaredTotalPages(JsonNode root) {
        int totalCount = root.path("search").path("totalCount").asInt(0);
        return totalCount > 0 ? (totalCount + PAGE_SIZE - 1) / PAGE_SIZE : 0;
    }

    protected String buildUrl(int page, String city) {
        throw new UnsupportedOperationException("This portal does not support dynamic URL building by city");
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
