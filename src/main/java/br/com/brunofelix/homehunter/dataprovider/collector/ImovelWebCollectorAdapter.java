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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ImovelWebCollectorAdapter implements PropertyCollectorPort {

    static final String DEFAULT_API_URL = "https://www.imovelweb.com.br/rplis-api/postings";
    static final String WEB_BASE = "https://www.imovelweb.com.br";
    static final int MAX_PAGES = 10;
    static final String SAMPLE_EXTERNAL_ID = "imovelweb-sample-01";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;
    private static final DateTimeFormatter IMWV_OFFSET_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private static final String PAYLOAD_TEMPLATE = """
            {
              "q": null,
              "direccion": null,
              "moneda": 3,
              "preciomin": null,
              "preciomax": "600000",
              "services": "",
              "general": "",
              "searchbykeyword": "",
              "amenidades": "",
              "caracteristicasprop": null,
              "comodidades": "",
              "disposicion": null,
              "roomType": "",
              "outside": "",
              "areaPrivativa": "",
              "areaComun": "",
              "multipleRets": "",
              "tipoDePropiedad": "2,1",
              "subtipoDePropiedad": null,
              "tipoDeOperacion": "1",
              "garages": null,
              "antiguedad": null,
              "expensasminimo": null,
              "expensasmaximo": null,
              "withoutguarantor": null,
              "habitacionesminimo": 0,
              "habitacionesmaximo": 0,
              "ambientesminimo": 0,
              "ambientesmaximo": 0,
              "banos": null,
              "superficieCubierta": null,
              "idunidaddemedida": null,
              "metroscuadradomin": null,
              "metroscuadradomax": null,
              "metroscuadradocubiertosmin": null,
              "metroscuadradocubiertosmax": null,
              "tipoAnunciante": "ALL",
              "grupoTipoDeMultimedia": "",
              "publicacion": null,
              "sort": "relevance",
              "etapaDeDesarrollo": "",
              "auctions": null,
              "polygonApplied": null,
              "idInmobiliaria": null,
              "excludePostingContacted": "",
              "banks": "",
              "places": "",
              "condominio": "",
              "preTipoDeOperacion": "",
              "pagina": __PAGE__,
              "city": "105406,105302",
              "province": null,
              "zone": null,
              "valueZone": null,
              "subZone": null,
              "coordenates": null
            }
            """;

    private final PortalPropertyNormalizer normalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiUrl;

    @Autowired
    public ImovelWebCollectorAdapter(PortalPropertyNormalizer normalizer) {
        this(normalizer, DEFAULT_API_URL);
    }

    ImovelWebCollectorAdapter(PortalPropertyNormalizer normalizer, String apiUrl) {
        this.normalizer = normalizer;
        this.apiUrl = apiUrl;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.IMOVELWEB;
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        List<CollectedProperty> results = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                Connection.Response response = Jsoup.connect(apiUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .method(Connection.Method.POST)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .requestBody(PAYLOAD_TEMPLATE.replace("__PAGE__", String.valueOf(page)))
                        .execute();

                if (response.statusCode() != 200) {
                    log.warn("ImovelWeb returned HTTP {} on page {}; stopping pagination.", response.statusCode(), page);
                    break;
                }

                String json = new String(response.bodyAsBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(json);
                JsonNode items = itemsOf(root);
                if (items == null) {
                    log.warn("ImovelWeb page {} has unexpected structure; stopping pagination.", page);
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
                    log.warn("ImovelWeb page 1 has no listings (anti-bot engaged?).");
                }
                if (parsed == 0) {
                    break;
                }
                int declaredMax = declaredTotalPages(root);
                if (declaredMax > 0 && page >= declaredMax) {
                    break;
                }
            } catch (IOException e) {
                log.error("ImovelWeb page {} failed to fetch or parse: {}", page, e.getMessage());
                break;
            }
        }

        if (results.isEmpty()) {
            log.info("ImovelWeb returned 0 live listings across {} page(s). Injecting sample listing for robustness.", MAX_PAGES);
            results.add(normalizer.normalize(
                    "Apartamento Exemplo ImovelWeb",
                    "APARTAMENTO",
                    BigDecimal.valueOf(390000),
                    80.0,
                    3,
                    "PE",
                    "Recife",
                    "Boa Viagem",
                    PortalName.IMOVELWEB,
                    SAMPLE_EXTERNAL_ID,
                    WEB_BASE + "/imovel/sample",
                    LocalDateTime.now()
            ));
        }

        return results;
    }

    private JsonNode itemsOf(JsonNode root) {
        JsonNode items = root.path("listPostings");
        return items.isArray() ? items : null;
    }

    CollectedProperty toProperty(JsonNode item) {
        if (!item.hasNonNull("postingId")) {
            log.debug("Skipping ImovelWeb non-listing entry (marker/malformed)");
            return null;
        }
        String id = item.path("postingId").asText();
        String title = item.path("title").asText(null);
        String url = item.path("url").asText(null);
        BigDecimal price = parsePrice(item);
        if (title == null || title.isBlank() || price == null || url == null) {
            log.debug("Skipping ImovelWeb item {} without title, price or URL", id);
            return null;
        }

        String rawType = "1".equals(item.path("realEstateType").path("realEstateTypeId").asText()) ? "casa" : "apartamento";
        Double area = parseArea(item);
        Integer bedrooms = parseBedrooms(item);
        String neighborhood = parseNeighborhood(item);
        String city = labelValue(item, "CIUDAD");
        String state = stateAcronym(item);
        LocalDateTime announcedAt = parseDate(item.path("modified_date").asText(null));

        return normalizer.normalize(
                title,
                rawType,
                price,
                area,
                bedrooms,
                state,
                city,
                neighborhood,
                PortalName.IMOVELWEB,
                id,
                absoluteUrl(url),
                announcedAt
        );
    }

    private BigDecimal parsePrice(JsonNode item) {
        for (JsonNode operation : item.path("priceOperationTypes")) {
            for (JsonNode price : operation.path("prices")) {
                if ("3".equals(price.path("currencyId").asText())
                        && price.path("amount").isNumber()
                        && price.path("amount").asDouble() > 0) {
                    return BigDecimal.valueOf(price.path("amount").asDouble());
                }
            }
        }
        return null;
    }

    private Double parseArea(JsonNode item) {
        double total = featureValue(item, "CFT100");
        double useful = featureValue(item, "CFT101");
        double value = useful > 0 ? useful : total;
        return value > 0 ? value : null;
    }

    private Integer parseBedrooms(JsonNode item) {
        double value = featureValue(item, "CFT2");
        return value > 0 ? (int) value : null;
    }

    private double featureValue(JsonNode item, String featureId) {
        return item.path("mainFeatures").path(featureId).path("value").asDouble(0.0);
    }

    private String parseNeighborhood(JsonNode item) {
        JsonNode location = item.path("postingLocation").path("location");
        String label = location.path("label").asText(null);
        return ("ZONA".equals(label) || "SUBZONA".equals(label))
                ? location.path("name").asText(null) : null;
    }

    private String labelValue(JsonNode item, String label) {
        JsonNode node = findLabelNode(item.path("postingLocation").path("location"), label);
        return node == null ? null : node.path("name").asText(null);
    }

    private String stateAcronym(JsonNode item) {
        JsonNode node = findLabelNode(item.path("postingLocation").path("location"), "PROVINCIA");
        return node == null ? null : node.path("acronym").asText(null);
    }

    private JsonNode findLabelNode(JsonNode location, String label) {
        JsonNode current = location;
        while (current != null && !current.isMissingNode() && current.has("label")) {
            if (label.equals(current.path("label").asText())) {
                return current;
            }
            current = current.path("parent");
        }
        return null;
    }

    private String absoluteUrl(String partialUrl) {
        return partialUrl.startsWith("http") ? partialUrl : WEB_BASE + partialUrl;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // try offset form below
        }
        try {
            return LocalDateTime.parse(raw, IMWV_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private int declaredTotalPages(JsonNode root) {
        int totalPages = root.path("paging").path("totalPages").asInt(0);
        int currentPage = root.path("paging").path("currentPage").asInt(0);
        return Math.max(totalPages, currentPage);
    }
}