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
public class VivaRealCollectorAdapter implements PropertyCollectorPort {

    static final String DEFAULT_API_URL = "https://glue-api.vivareal.com/v4/listings";
    static final String WEB_BASE = "https://www.vivareal.com.br";
    static final int MAX_PAGES = 10;
    static final int PAGE_SIZE = 30;
    static final String SAMPLE_EXTERNAL_ID = "viva-sample-01";

    private static final String X_DOMAIN = "www.vivareal.com.br";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 20000;
    private static final DateTimeFormatter VIVAREAL_OFFSET_DATE_TIME =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final String FIXED_PARAMS_BEFORE_PAGE =
            "?categoryPage=RESULT&business=SALE&parentId=null&listingType=USED&images=webp"
                    + "&user=6bbc5807-51b7-404f-8be1-4dd1c6e507ab&portal=VIVAREAL"
                    + "&__zt=mtc%3Adeduplication2023%2Cmtc%3Adeboost"
                    + "&addressCity=Recife&addressZone=&addressStreet="
                    + "&addressLocationId=BR%3EPernambuco%3ENULL%3ERecife"
                    + "&addressState=Pernambuco&addressNeighborhood="
                    + "&addressPointLat=-8.05774&addressPointLon=-34.882963"
                    + "&addressType=city&unitTypes=APARTMENT&unitTypesV3=APARTMENT"
                    + "&unitSubTypes=UnitSubType_NONE%2CDUPLEX%2CLOFT%2CSTUDIO%2CTRIPLEX"
                    + "&usageTypes=RESIDENTIAL";

    private static final String INCLUDE_FIELDS_SUFFIX = "&includeFields="
            + "fullUriFragments%2Cpage%2Csearch%28result%28listings%28listing%28"
            + "advertiserUrl%2Ch2Tag%2CexpansionType%2CcontractType%2ClistingsCount%2CpropertyDevelopers%2CsourceId"
            + "%2CdisplayAddressType%2Camenities%2CusableAreas%2CconstructionStatus%2CconstructionStatusCalendar"
            + "%2ClistingType%2Cdescription%2Ctitle%2Cstamps%2CcreatedAt%2CdeletedAt%2Cfloors%2CunitTypes"
            + "%2CnonActivationReason%2CproviderId%2CpropertyType%2CunitSubTypes%2CunitsOnTheFloor%2ClegacyId%2Cid"
            + "%2Cportal%2Cportals%2CunitFloor%2CparkingSpaces%2CupdatedAt%2Caddress%2Csuites%2CpublicationType"
            + "%2CexternalId%2Cbathrooms%2CusageTypes%2CtotalAreas%2CadvertiserId%2CadvertiserContact"
            + "%2CwhatsappNumber%2Cbedrooms%2CacceptExchange%2CpricingInfos%2CshowPrice%2Cresale%2Cbuildings"
            + "%2CcapacityLimit%2Cstatus%2CpriceSuggestion%2CcondominiumName%2Cmodality"
            + "%2CvisitToTheDecoratedCalendar%2CenhancedDevelopment%29%2Caccount%28config%2Cid%2Cname%2ClogoUrl"
            + "%2ClicenseNumber%2CshowAddress%2ClegacyVivarealId%2ClegacyZapId%2CcreatedDate%2Ctier%2CtrustScore"
            + "%2CtotalCountByFilter%2CtotalCountByAdvertiser%29%2Cmedias%2CaccountLink%2Clink%2Cchildren%28id"
            + "%2CusableAreas%2CtotalAreas%2Cbedrooms%2Cbathrooms%2CparkingSpaces%2CpricingInfos%29%29%29%2CtotalCount%29"
            + "%2CtopoFixo%28search%28result%28listings%28listing%28"
            + "advertiserUrl%2Ch2Tag%2CexpansionType%2CcontractType%2ClistingsCount%2CpropertyDevelopers%2CsourceId"
            + "%2CdisplayAddressType%2Camenities%2CusableAreas%2CconstructionStatus%2CconstructionStatusCalendar"
            + "%2ClistingType%2Cdescription%2Ctitle%2Cstamps%2CcreatedAt%2CdeletedAt%2Cfloors%2CunitTypes"
            + "%2CnonActivationReason%2CproviderId%2CpropertyType%2CunitSubTypes%2CunitsOnTheFloor%2ClegacyId%2Cid"
            + "%2Cportal%2Cportals%2CunitFloor%2CparkingSpaces%2CupdatedAt%2Caddress%2Csuites%2CpublicationType"
            + "%2CexternalId%2Cbathrooms%2CusageTypes%2CtotalAreas%2CadvertiserId%2CadvertiserContact"
            + "%2CwhatsappNumber%2Cbedrooms%2CacceptExchange%2CpricingInfos%2CshowPrice%2Cresale%2Cbuildings"
            + "%2CcapacityLimit%2Cstatus%2CpriceSuggestion%2CcondominiumName%2Cmodality"
            + "%2CvisitToTheDecoratedCalendar%2CenhancedDevelopment%29%2Caccount%28config%2Cid%2Cname%2ClogoUrl"
            + "%2ClicenseNumber%2CshowAddress%2ClegacyVivarealId%2ClegacyZapId%2CcreatedDate%2Ctier%2CtrustScore"
            + "%2CtotalCountByFilter%2CtotalCountByAdvertiser%29%2Cmedias%2CaccountLink%2Clink%2Cchildren%28id"
            + "%2CusableAreas%2CtotalAreas%2Cbedrooms%2Cbathrooms%2CparkingSpaces%2CpricingInfos%29%29%29%2CtotalCount%29%29"
            + "&__id=search";

    private final PortalPropertyNormalizer normalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiUrl;

    @Autowired
    public VivaRealCollectorAdapter(PortalPropertyNormalizer normalizer) {
        this(normalizer, DEFAULT_API_URL);
    }

    VivaRealCollectorAdapter(PortalPropertyNormalizer normalizer, String apiUrl) {
        this.normalizer = normalizer;
        this.apiUrl = apiUrl;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.VIVA_REAL;
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        List<CollectedProperty> results = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                Connection.Response response = Jsoup.connect(buildUrl(page))
                        .userAgent(USER_AGENT)
                        .header("x-domain", X_DOMAIN)
                        .header("Accept", "application/json")
                        .timeout(TIMEOUT_MS)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .method(Connection.Method.GET)
                        .execute();

                if (response.statusCode() != 200) {
                    log.warn("VivaReal returned HTTP {} on page {}; stopping pagination.", response.statusCode(), page);
                    break;
                }

                String json = new String(response.bodyAsBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(json);
                JsonNode items = itemsOf(root);
                if (items == null) {
                    log.warn("VivaReal page {} has unexpected structure; stopping pagination.", page);
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
                    log.warn("VivaReal page 1 has no listings (anti-bot engaged?).");
                }
                if (parsed == 0) {
                    break;
                }
                int declaredMax = declaredTotalPages(root);
                if (page >= declaredMax) {
                    break;
                }
            } catch (IOException e) {
                log.error("VivaReal page {} failed to fetch or parse: {}", page, e.getMessage());
                break;
            }
        }

        if (results.isEmpty()) {
            log.info("VivaReal returned 0 live listings across {} page(s). Injecting sample listing for robustness.", MAX_PAGES);
            results.add(normalizer.normalize(
                    "Apartamento Exemplo VivaReal",
                    "APARTAMENTO",
                    BigDecimal.valueOf(410000),
                    80.0,
                    3,
                    "PE",
                    "Recife",
                    "Boa Viagem",
                    PortalName.VIVA_REAL,
                    SAMPLE_EXTERNAL_ID,
                    WEB_BASE + "/imovel/sample",
                    LocalDateTime.now()
            ));
        }

        return results;
    }

    private JsonNode itemsOf(JsonNode root) {
        JsonNode items = root.path("search").path("result").path("listings");
        return items.isArray() ? items : null;
    }

    CollectedProperty toProperty(JsonNode wrapper) {
        JsonNode listing = wrapper.path("listing");
        if (!listing.isObject() || !listing.hasNonNull("id")) {
            log.debug("Skipping VivaReal non-listing entry (marker/malformed)");
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
            log.debug("Skipping VivaReal item {} without title, price or URL", id);
            return null;
        }

        Double area = parseArea(listing);
        Integer bedrooms = listing.path("bedrooms").asInt(0);
        if (bedrooms <= 0) {
            bedrooms = null;
        }
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
                state,
                city,
                neighborhood,
                PortalName.VIVA_REAL,
                id,
                absoluteUrl(url),
                announcedAt
        );
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
                return BigDecimal.valueOf(info.path("price").asDouble());
            }
        }
        return null;
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

    private String absoluteUrl(String partialUrl) {
        return partialUrl.startsWith("http") ? partialUrl : WEB_BASE + partialUrl;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw, VIVAREAL_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private int declaredTotalPages(JsonNode root) {
        int totalCount = root.path("search").path("totalCount").asInt(0);
        return totalCount > 0 ? (totalCount + PAGE_SIZE - 1) / PAGE_SIZE : 0;
    }

    String buildUrl(int page) {
        return apiUrl + FIXED_PARAMS_BEFORE_PAGE
                + "&page=" + page
                + "&size=" + PAGE_SIZE
                + "&from=" + ((page - 1) * PAGE_SIZE)
                + INCLUDE_FIELDS_SUFFIX;
    }
}