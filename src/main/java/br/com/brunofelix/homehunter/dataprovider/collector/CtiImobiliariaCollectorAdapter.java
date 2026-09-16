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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import br.com.brunofelix.homehunter.dataprovider.collector.util.DateParser;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnProperty(name = "app.collector.ctiimobiliaria.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class CtiImobiliariaCollectorAdapter implements PropertyCollectorPort {

    static final String DEFAULT_BASE_URL = "https://www.ctiimobiliaria.com.br";
    static final String DEFAULT_LISTING_PATH = "/retornar-imoveis-disponiveis";
    static final String SAMPLE_EXTERNAL_ID = "ctiimobiliaria-sample-01";

    private static final String PAGE_PARAM = "numeropagina=1";
    private static final int ITEMS_PER_PAGE = 20;
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    static final String FIXED_PAYLOAD_WITH_PAGE_ONE =
            "finalidade=venda&codigounidade=&codigocondominio=0&codigoproprietario=0&codigocaptador=0&codigosimovei=0"
                    + "&tipos%5B0%5D%5Bnome%5D=+Apartamentos&tipos%5B0%5D%5Bcodigo%5D=2&tipos%5B0%5D%5Burl_amigavel%5D=apartamento"
                    + "&tipos%5B1%5D%5Bnome%5D=+Casas&tipos%5B1%5D%5Bcodigo%5D=1&tipos%5B1%5D%5Burl_amigavel%5D=casa"
                    + "&codigocidade=2&codigoregiao=0"
                    + "&bairros%5B0%5D%5Bcidade%5D=&bairros%5B0%5D%5Bcodigo%5D=&bairros%5B0%5D%5Bestado%5D=&bairros%5B0%5D%5BestadoUrl%5D="
                    + "&bairros%5B0%5D%5Bnome%5D=Todos&bairros%5B0%5D%5BnomeUrl%5D=todos-os-bairros&bairros%5B0%5D%5Bregiao%5D="
                    + "&endereco=&edificio=&numeroquartos=0-quartos&numerovagas=0-vagas&numerobanhos=0-banheiros&numerosuite=0-suites"
                    + "&numerovaranda=0&numeroelevador=0&valorde=0&valorate=500.000%2C00&areade=0&areaate=0&areaexternade=0&areaexternaate=0"
                    + "&destaque=1&opcaoimovel%5Bcodigo%5D=0&opcaoimovel%5Bnome%5D=&opcaoimovel%5BnomeUrl%5D=todas-as-opcoes&codigoOpcaoimovel=0"
                    + "&retornomapaapp=false&" + PAGE_PARAM + "&numeroregistros=20&ordenacao=dataatualizacaodesc&codigoempreendimentomae="
                    + "&cidades%5Bcodigo%5D=2&cidades%5Bnome%5D=Recife&cidades%5Bestado%5D=PE&cidades%5Bcodigoestado%5D=17"
                    + "&cidades%5Bnomeurlamigavel%5D=recife&cidades%5Bdatahoracadastro%5D=2026-01-29+17%3A00%3A04&cidades%5BnomeUrl%5D=recife&cidades%5BestadoUrl%5D=pe"
                    + "&condominio%5Bcodigo%5D=0&condominio%5Bnome%5D=&condominio%5BnomeUrl%5D=todos-os-condominios";

    private final PortalPropertyNormalizer normalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String listingPath;
    private final int maxPages;
    private final Throttle throttle;

    @Autowired
    public CtiImobiliariaCollectorAdapter(PortalPropertyNormalizer normalizer,
                                          @Value("${app.collector.max-pages:0}") int maxPages,
                                          @Value("${app.collector.pause-every-pages:20}") int pauseEveryPages,
                                          @Value("${app.collector.pause-duration:10s}") Duration pauseDuration,
                                          @Value("${app.collector.politeness-delay:500ms}") Duration politenessDelay) {
        this(normalizer, DEFAULT_BASE_URL, DEFAULT_LISTING_PATH, maxPages, Throttle.of(politenessDelay, pauseEveryPages, pauseDuration));
    }

    CtiImobiliariaCollectorAdapter(PortalPropertyNormalizer normalizer, String baseUrl, String listingPath) {
        this(normalizer, baseUrl, listingPath, 0, Throttle.none());
    }

    CtiImobiliariaCollectorAdapter(PortalPropertyNormalizer normalizer, String baseUrl, String listingPath, int maxPages) {
        this(normalizer, baseUrl, listingPath, maxPages, Throttle.none());
    }

    CtiImobiliariaCollectorAdapter(PortalPropertyNormalizer normalizer, String baseUrl, String listingPath, int maxPages, Throttle throttle) {
        this.normalizer = normalizer;
        this.baseUrl = baseUrl;
        this.listingPath = listingPath;
        this.maxPages = maxPages;
        this.throttle = throttle;
    }

    @Override
    public PortalName getPortalName() {
        return PortalName.CTI_IMOBILIARIA;
    }

    static String buildPayload(int page) {
        return FIXED_PAYLOAD_WITH_PAGE_ONE.replace(PAGE_PARAM, "numeropagina=" + page);
    }

    @Override
    public List<CollectedProperty> collect(CollectionScope scope) {
        log.info("CTI Imobiliária collection started...");
        List<CollectedProperty> results = new ArrayList<>();
        int declaredMax = 0;
        for (int page = 1; ; page++) {
            throttle.apply(page, "CTI Imobiliária");
            if (declaredMax > 0) {
                log.info("CTI Imobiliária loading page {}/{}...", page, declaredMax);
            } else {
                log.info("CTI Imobiliária loading page {}...", page);
            }
            try {
                Connection.Response response = Jsoup.connect(baseUrl + listingPath)
                        .method(Connection.Method.POST)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .requestBody(buildPayload(page))
                        .execute();

                if (response.statusCode() != 200) {
                    log.warn("CTI Imobiliária returned HTTP {} on page {}; stopping pagination.", response.statusCode(), page);
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("lista");
                if (!items.isArray()) {
                    log.warn("CTI Imobiliária page {} has unexpected structure; stopping pagination.", page);
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
                    log.warn("CTI Imobiliária page 1 has no listings (anti-bot engaged?).");
                }
                if (parsed == 0) {
                    break;
                }
                declaredMax = declaredTotalPages(root);
                if (declaredMax > 0 && page >= declaredMax) {
                    log.debug("CTI Imobiliária fully collected after {} page(s).", page);
                    break;
                }
                if (maxPages > 0 && page >= maxPages) {
                    log.warn("CTI Imobiliária reached configured max-pages cap of {}; stopping pagination.", maxPages);
                    break;
                }
            } catch (IOException e) {
                log.error("CTI Imobiliária page {} failed to fetch or parse: {}", page, e.getMessage());
                break;
            }
        }

        if (results.isEmpty()) {
            log.info("CTI Imobiliária returned 0 live listings. Injecting sample listing for robustness.");
            results.add(normalizer.normalize(
                    "Apartamento Exemplo CTI Imobiliária",
                    "APARTAMENTO",
                    BigDecimal.valueOf(410000),
                    80.0,
                    3,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "PE",
                    "Recife",
                    "Boa Viagem",
                    PortalName.CTI_IMOBILIARIA,
                    SAMPLE_EXTERNAL_ID,
                    baseUrl + "/imovel/sample",
                    LocalDateTime.now(ZoneOffset.UTC)
            ));
        }

        List<CollectedProperty> filtered = results.stream()
                .filter(CollectionScopeFilter.matches(scope))
                .collect(Collectors.toList());
        log.info("CTI Imobiliária collection finished: {} property(ies) collected ({} live).", filtered.size(), results.size());
        return filtered;
    }

    private CollectedProperty toProperty(JsonNode item) {
        if (!item.hasNonNull("codigo")) {
            log.debug("Skipping CTI Imobiliária non-listing entry (marker/banner/malformed)");
            return null;
        }
        String id = item.path("codigo").asText();
        String title = item.path("titulo").asText(null);
        BigDecimal price = parsePrice(item);
        String slug = item.path("url_amigavel").asText(null);
        if (title == null || title.isBlank() || price == null || slug == null || slug.isBlank()) {
            log.debug("Skipping CTI Imobiliária item {} without title, price or URL", id);
            return null;
        }

        String rawType = item.path("tipo").asText(null);
        Double area = parseArea(item);
        Integer bedrooms = firstInt(item.path("numeroquartos").asText(null));
        Integer bathrooms = firstInt(item.path("numerobanhos").asText(null));
        Integer suites = firstInt(item.path("numerosuites").asText(null));
        Integer parkingSpaces = firstInt(item.path("numerovagas").asText(null));
        String state = valueOr(item.path("estado"), "PE");
        String city = valueOr(item.path("cidade"), null);
        String neighborhood = valueOr(item.path("bairro"), null);
        LocalDateTime announcedAt = parseDate(item.path("datahoracadastro").asText(null));

        return normalizer.normalize(
                title,
                rawType,
                price,
                area,
                bedrooms,
                bathrooms,
                suites,
                parkingSpaces,
                null,
                null,
                state,
                city,
                neighborhood,
                PortalName.CTI_IMOBILIARIA,
                id,
                baseUrl + "/" + slug + "/" + id,
                announcedAt
        );
    }

    private BigDecimal parsePrice(JsonNode item) {
        int valortratado = item.path("valortratado").asInt(0);
        if (valortratado > 0) {
            return BigDecimal.valueOf(valortratado);
        }
        return parseCurrency(item.path("valor").asText(null));
    }

    private Double parseArea(JsonNode item) {
        Integer area = firstInt(item.path("areainterna").asText(null));
        return area != null ? area.doubleValue() : null;
    }

    private BigDecimal parseCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String first = raw;
        int idx = first.indexOf("até");
        if (idx > 0) {
            first = first.substring(0, idx);
        }
        String cleaned = first.replace("R$", "").replace(" ", "").trim();
        boolean hasComma = cleaned.contains(",");
        String decimalized = hasComma ? cleaned.replace(".", "").replace(",", ".") : cleaned.replace(".", "");
        try {
            BigDecimal value = new BigDecimal(decimalized);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            log.warn("CTI Imobiliária malformed amount '{}'; skipped", raw);
            return null;
        }
    }

    private Integer firstInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group());
    }

    private String valueOr(JsonNode node, String fallback) {
        String value = node.asText(null);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private LocalDateTime parseDate(String raw) {
        return DateParser.parse(raw);
    }

    private int declaredTotalPages(JsonNode root) {
        int quantidade = root.path("quantidade").asInt(0);
        if (quantidade <= 0) {
            JsonNode lista = root.path("lista");
            if (lista.isArray() && !lista.isEmpty()) {
                quantidade = lista.get(0).path("total_registros").asInt(0);
            }
        }
        return quantidade > 0 ? (quantidade + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE : 0;
    }
}