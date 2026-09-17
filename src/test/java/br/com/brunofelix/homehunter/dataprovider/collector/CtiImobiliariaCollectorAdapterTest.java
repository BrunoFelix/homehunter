package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class CtiImobiliariaCollectorAdapterTest {

    private static final Pattern PAGE_PARAM = Pattern.compile("numeropagina=(\\d+)");

    private static final String APTO =
            "{\"tipo\":\"Apartamento\",\"bairro\":\"Cordeiro\",\"cidade\":\"Recife\",\"estado\":\"PE\","
                    + "\"numeroquartos\":\"2  -  3\",\"numerovagas\":\"1  -  2\",\"numerobanhos\":\"1  -  1\","
                    + "\"numerosuites\":\"1 at\u00e9 2\",\"datahoracadastro\":\"2026-08-17 17:20:22\","
                    + "\"codigo\":7894,\"titulo\":\"Apartamento \u00e0 venda, Cordeiro - Recife/PE\","
                    + "\"areainterna\":\"53   -  83 \",\"valor\":\"R$ 428.000,00 at\u00e9 R$ 730.000,00\","
                    + "\"valortratado\":0,\"url_amigavel\":\"apartamento-a-venda-cordeiro-recife-pe\"}";

    private static final String CASA =
            "{\"tipo\":\"Casa\",\"bairro\":\"Cordeiro\",\"cidade\":\"Recife\",\"estado\":\"PE\","
                    + "\"numeroquartos\":\"3\",\"numerovagas\":\"5\",\"numerobanhos\":\"3\",\"numerosuites\":\"1\","
                    + "\"datahoracadastro\":\"2026-09-11 11:22:00\",\"codigo\":7950,"
                    + "\"titulo\":\"Casa \u00e0 venda, Cordeiro - Recife/PE\",\"areainterna\":\"162,00\","
                    + "\"valor\":\"R$ 500.000,00\",\"valortratado\":500000,\"url_amigavel\":\"casa-a-venda-cordeiro-recife-pe\"}";

    private HttpServer server;
    private final List<Integer> requestedPages = new ArrayList<>();
    private Function<Integer, StubResponse> pageHandler;

    @BeforeEach
    void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String decoded = URLDecoder.decode(body, StandardCharsets.UTF_8);
            assertTrue(decoded.contains("finalidade=venda"), "expected fixed CTI Imobiliária payload, got: " + decoded);
            assertTrue(decoded.contains("tipos[0][nome]= Apartamentos"), "expected tipos apartment in: " + decoded);
            assertTrue(decoded.contains("cidades[nome]=Recife"), "expected cidades in: " + decoded);
            assertTrue(decoded.contains("valorate=500.000,00"), "expected valence in: " + decoded);
            assertTrue(decoded.contains("numeroregistros=20"), "expected numeroregistros in: " + decoded);

            Matcher pm = PAGE_PARAM.matcher(decoded);
            assertTrue(pm.find(), "expected numeropagina in: " + decoded);
            int page = Integer.parseInt(pm.group(1));
            requestedPages.add(page);

            StubResponse response = pageHandler.apply(page);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDownServer() {
        server.stop(0);
    }

    private CtiImobiliariaCollectorAdapter adapter() {
        return adapter(0);
    }

    private CtiImobiliariaCollectorAdapter adapter(int maxPages) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new CtiImobiliariaCollectorAdapter(new PortalPropertyNormalizer(), baseUrl, "/", maxPages);
    }

    private static String listingPage(int quantidade, String... items) {
        return "{\"quantidade\":" + quantidade + ",\"lista\":[" + String.join(",", items) + "]}";
    }

    private static String emptyPage() {
        return "{\"quantidade\":125,\"lista\":[]}";
    }

    private static String malformedPage() {
        return "{\"algo\":\"invalido\"}";
    }

    private record StubResponse(int status, String body) {
    }

    @Test
    void shouldParseRealisticItemsAndStopOnEmptyPage() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(125, APTO, CASA))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestedPages);

        CollectedProperty apto = results.stream()
                .filter(p -> p.externalId().equals("7894"))
                .findFirst().orElseThrow();
        assertEquals(PortalName.CTI_IMOBILIARIA, apto.portalName());
        assertEquals(PropertyType.APARTAMENTO, apto.type());
        assertEquals("Apartamento à venda, Cordeiro - Recife/PE", apto.title());
        assertEquals(428000, apto.price().value().intValue());
        assertEquals(53.0, apto.area().value());
        assertEquals(2, apto.bedrooms().value());
        assertEquals(1, apto.bathrooms());
        assertEquals(1, apto.suites());
        assertEquals(1, apto.parkingSpaces());
        assertEquals("PE", apto.address().state());
        assertEquals("RECIFE", apto.address().city());
        assertEquals("CORDEIRO", apto.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-08-17T20:20:22"), apto.announcedAt());
        assertEquals("http://127.0.0.1:" + server.getAddress().getPort() + "/imovel/apartamento-a-venda-cordeiro-recife-pe/7894", apto.url());

        CollectedProperty casa = results.stream()
                .filter(p -> p.externalId().equals("7950"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.CASA, casa.type());
        assertEquals(500000, casa.price().value().intValue());
        assertEquals(162.0, casa.area().value());
        assertEquals(3, casa.bedrooms().value());
        assertEquals("CORDEIRO", casa.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-09-11T14:22:00"), casa.announcedAt());
    }

    @Test
    void shouldSkipMalformedItems() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(125,
                        APTO,
                        "{\"codigo\":999,\"titulo\":null,\"url_amigavel\":\"slug\"}",
                        "{\"codigo\":1000,\"titulo\":\"Sem pre\u00e7o\",\"url_amigavel\":\"slug\"}",
                        "{\"codigo\":1001,\"titulo\":\"Sem url\",\"valor\":\"R$ 300.000,00\"}",
                        "{\"paginacao\":true}",
                        CASA))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("999")));
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("1000")));
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("1001")));
    }

    @Test
    void shouldInjectSampleWhenHttpForbidden() {
        pageHandler = pg -> new StubResponse(403, "");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("ctiimobiliaria-sample-01", results.get(0).externalId());
        assertEquals(PortalName.CTI_IMOBILIARIA, results.get(0).portalName());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldInjectSampleWhenFirstPageHasNoListings() {
        pageHandler = pg -> new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("ctiimobiliaria-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldRespectDeclaredTotalPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(20, APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldPaginateUntilDeclaredTotalPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(40, APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestedPages);
    }

    @Test
    void shouldCapLoopAtConfiguredMaxPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(1000, APTO));

        List<CollectedProperty> results = adapter(3).collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestedPages);
    }

    @Test
    void shouldStopOnUnexpectedResponseStructure() {
        pageHandler = pg -> new StubResponse(200, malformedPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("ctiimobiliaria-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void buildPayloadShouldExposeFixedContract() {
        String payload = CtiImobiliariaCollectorAdapter.buildPayload(1);
        assertEquals("numeropagina=1", payload.substring(payload.indexOf("numeropagina="), payload.indexOf("&numeroregistros")));
        assertTrue(payload.startsWith("finalidade=venda&"));
        assertTrue(payload.contains("cidades%5Bnome%5D=Recife"));
        assertTrue(payload.contains("valorate=500.000%2C00"));
        assertTrue(payload.endsWith("condominio%5BnomeUrl%5D=todos-os-condominios"));

        String page2 = CtiImobiliariaCollectorAdapter.buildPayload(2);
        assertEquals("numeropagina=2", page2.substring(page2.indexOf("numeropagina="), page2.indexOf("&numeroregistros")));
        assertEquals(payload.replace("numeropagina=1", "numeropagina=2"), page2);
    }
}