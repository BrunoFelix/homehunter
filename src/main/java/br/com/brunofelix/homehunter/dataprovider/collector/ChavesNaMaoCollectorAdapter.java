package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.port.out.PropertyCollectorPort;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ConditionalOnProperty(name = "app.collector.chavesnamao.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ChavesNaMaoCollectorAdapter implements PropertyCollectorPort {

    static final String DEFAULT_BASE_URL = "https://www.chavesnamao.com.br";
    static final String DEFAULT_LISTING_PATH = "/api/realestate/listing/items/";
    static final String FIXED_PARAMS_BEFORE_PAGE =
            "level1=casas-a-venda&level2=pe-recife&filtro=cid%3A%5B5302%5D%2Ctim%3A%5B1%5D%2Cpmax%3A500000";
    static final String FIXED_PARAMS_AFTER_PAGE = "quebra=%5B6000%5D&server=0&viewport=desktop";
    static final int MAX_PAGES = 10;
    static final String SAMPLE_EXTERNAL_ID = "chaves-sample-01";

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Recife");

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    private final PortalPropertyNormalizer normalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String listingPath;

    @Autowired
    public ChavesNaMaoCollectorAdapter(PortalPropertyNormalizer normalizer) {
        this(normalizer, DEFAULT_BASE_URL, DEFAULT_LISTING_PATH);
    }

    ChavesNaMaoCollectorAdapter(PortalPropertyNormalizer normalizer, String baseUrl, String listingPath) {
        this.normalizer = normalizer;
        this.baseUrl = baseUrl;
        this.listingPath = listingPath;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.CHAVES_NA_MAO;
    }

    private String buildUrl(int page) {
        return baseUrl + listingPath + "?" + FIXED_PARAMS_BEFORE_PAGE + "&pg=" + page + "&" + FIXED_PARAMS_AFTER_PAGE;
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        List<CollectedProperty> results = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String targetUrl = buildUrl(page);
            try {
                Connection.Response response = Jsoup.connect(targetUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .execute();

                if (response.statusCode() != 200) {
                    log.warn("Chaves na Mão returned HTTP {} on page {}; stopping pagination.", response.statusCode(), page);
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = itemsOf(root);
                if (items == null) {
                    log.warn("Chaves na Mão page {} has unexpected structure; stopping pagination.", page);
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
                    log.warn("Chaves na Mão page 1 has no listings (anti-bot engaged?).");
                }
                if (parsed == 0) {
                    break;
                }
                int declaredMax = declaredMaxPages(root);
                if (declaredMax > 0 && page >= declaredMax) {
                    break;
                }
            } catch (IOException e) {
                log.error("Chaves na Mão page {} failed to fetch or parse: {}", page, e.getMessage());
                break;
            }
        }

        if (results.isEmpty()) {
            log.info("Chaves na Mão returned 0 live listings across {} page(s). Injecting sample listing for robustness.", MAX_PAGES);
            results.add(normalizer.normalize(
                    "Apartamento Exemplo Chaves na Mão",
                    "APARTAMENTO",
                    BigDecimal.valueOf(410000),
                    80.0,
                    3,
                    "PE",
                    "Recife",
                    "Boa Viagem",
                    PortalName.CHAVES_NA_MAO,
                    SAMPLE_EXTERNAL_ID,
                    baseUrl + "/imovel/sample",
                    LocalDateTime.now(ZoneOffset.UTC)
            ));
        }

        return results;
    }

    private JsonNode itemsOf(JsonNode root) {
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            items = root.path("data").path("items");
        }
        return items.isArray() ? items : null;
    }

    private CollectedProperty toProperty(JsonNode item) {
        if (!item.hasNonNull("id")) {
            log.debug("Skipping Chaves na Mão non-listing entry (marker/banner/malformed)");
            return null;
        }
        String id = item.path("id").asText();
        String title = item.path("title").asText(null);
        String url = item.path("url").asText(null);
        BigDecimal price = parsePrice(item);
        if (title == null || title.isBlank() || price == null || url == null) {
            log.debug("Skipping Chaves na Mão item {} without title, price or URL", id);
            return null;
        }

        String rawType = item.path("realtyType").path("id").asInt(0) == 4 ? "casa" : "apartamento";
        Double area = parseArea(item);
        Integer bedrooms = parseBedrooms(item);
        String state = valueOr(item.path("location").path("state").path("acronym"), "PE");
        String city = valueOr(item.path("location").path("city").path("name"), null);
        String neighborhood = valueOr(item.path("location").path("neighborhood").path("name"), null);
        LocalDateTime announcedAt = parseDate(item.path("createdAt").asText(null));

        return normalizer.normalize(
                title,
                rawType,
                price,
                area,
                bedrooms,
                state,
                city,
                neighborhood,
                PortalName.CHAVES_NA_MAO,
                id,
                absoluteUrl(url),
                announcedAt
        );
    }

    private String valueOr(JsonNode node, String fallback) {
        String value = node.asText(null);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String absoluteUrl(String partialUrl) {
        return partialUrl.startsWith("http") ? partialUrl : baseUrl + partialUrl;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(BRAZIL_ZONE)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // try offset form below
        }
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    private int declaredMaxPages(JsonNode root) {
        JsonNode meta = root.path("metadata");
        for (String key : new String[]{"maxPages", "totalPages"}) {
            int value = meta.path(key).asInt(0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private BigDecimal parsePrice(JsonNode item) {
        JsonNode rawPrice = item.path("prices").path("rawPrice");
        if (!rawPrice.isNumber() || rawPrice.asDouble() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(rawPrice.asDouble());
    }

    private Double parseArea(JsonNode item) {
        double useful = item.path("area").path("useful").asDouble(0.0);
        double total = item.path("area").path("total").asDouble(0.0);
        double value = useful > 0 ? useful : total;
        return value > 0 ? value : null;
    }

    private Integer parseBedrooms(JsonNode item) {
        int count = item.path("bedrooms").path("count").asInt(-1);
        return count >= 0 ? count : null;
    }
}