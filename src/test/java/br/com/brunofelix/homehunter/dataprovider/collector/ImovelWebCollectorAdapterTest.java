package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ImovelWebCollectorAdapterTest {

    private static final String APTO =
            "{\"postingId\":\"3037995631\",\"postingCode\":\"56787\","
                    + "\"title\":\"Apartamento à venda no EDIFÍCIO ESTAÇÃO CARMEM MARIA, TORRE, Recife, PE\","
                    + "\"priceOperationTypes\":[{\"operationType\":{\"name\":\"Venda\",\"operationTypeId\":\"1\"},"
                    + "\"prices\":[{\"currencyId\":\"3\",\"amount\":430959,\"formattedAmount\":\"430.959\",\"currency\":\"R$\"}]}],"
                    + "\"mainFeatures\":{\"CFT100\":{\"label\":\"Área total\",\"value\":\"50\"},"
                    + "\"CFT101\":{\"label\":\"Área útil\",\"value\":\"50\"},"
                    + "\"CFT2\":{\"label\":\"Quartos\",\"value\":\"2\"}},"
                    + "\"realEstateType\":{\"name\":\"Apartamentos\",\"realEstateTypeId\":\"2\"},"
                    + "\"url\":\"/propriedades/apartamento-a-venda-no-edificio-estacao-carmem-maria-3037995631.html\","
                    + "\"postingLocation\":{\"location\":{\"name\":\"Torre\",\"label\":\"ZONA\",\"depth\":3,"
                    + "\"parent\":{\"name\":\"Recife\",\"label\":\"CIUDAD\",\"depth\":2,"
                    + "\"parent\":{\"name\":\"Pernambuco\",\"label\":\"PROVINCIA\",\"depth\":1,\"acronym\":\"PE\"}}}},"
                    + "\"status\":\"ONLINE\",\"modified_date\":\"2026-09-13T15:58:11-0400\"}";

    private static final String CASA =
            "{\"postingId\":\"3012345678\",\"postingCode\":\"99999\","
                    + "\"title\":\"Casa à venda em Recife, PE\","
                    + "\"priceOperationTypes\":[{\"operationType\":{\"name\":\"Venda\",\"operationTypeId\":\"1\"},"
                    + "\"prices\":[{\"currencyId\":\"3\",\"amount\":520000,\"formattedAmount\":\"520.000\",\"currency\":\"R$\"}]}],"
                    + "\"mainFeatures\":{\"CFT100\":{\"label\":\"Área total\",\"value\":\"120\"},"
                    + "\"CFT101\":{\"label\":\"Área útil\",\"value\":\"100\"},"
                    + "\"CFT2\":{\"label\":\"Quartos\",\"value\":\"3\"}},"
                    + "\"realEstateType\":{\"name\":\"Casas\",\"realEstateTypeId\":\"1\"},"
                    + "\"url\":\"/propriedades/casa-a-venda-em-recife-3012345678.html\","
                    + "\"postingLocation\":{\"location\":{\"name\":\"Recife\",\"label\":\"CIUDAD\",\"depth\":2,"
                    + "\"parent\":{\"name\":\"Pernambuco\",\"label\":\"PROVINCIA\",\"depth\":1,\"acronym\":\"PE\"}}},"
                    + "\"status\":\"ONLINE\",\"modified_date\":\"2026-09-12T09:30:00-0300\"}";

    private HttpServer server;
    private final List<Integer> requestedPages = new ArrayList<>();
    private final List<String> requestBodies = new ArrayList<>();
    private Function<Integer, StubResponse> pageHandler;

    @BeforeEach
    void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        assertNotNull(contentType, "expected Content-Type header");
        assertTrue(contentType.startsWith("application/json"),
                "expected application/json Content-Type, got: " + contentType);

        String body = readBody(exchange.getRequestBody());
        requestBodies.add(body);
        assertTrue(body.contains("\"tipoDePropiedad\": \"2,1\""), "expected fixed tipoDePropiedad filter in: " + body);
        assertTrue(body.contains("\"tipoDeOperacion\": \"1\""), "expected fixed tipoDeOperacion filter in: " + body);
        assertTrue(body.contains("\"city\": \"105406,105302\""), "expected fixed Recife city filter in: " + body);

        int page = 1;
        int marker = body.indexOf("\"pagina\":");
        if (marker >= 0) {
            String after = body.substring(marker + "\"pagina\":".length()).trim();
            page = Integer.parseInt(after.substring(0, after.indexOf(',')).trim());
        }
        requestedPages.add(page);

        StubResponse response = pageHandler.apply(page);
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
        exchange.close();
    }

    private static String readBody(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDownServer() {
        server.stop(0);
    }

    private ImovelWebCollectorAdapter adapter() {
        return new ImovelWebCollectorAdapter(
                new PortalPropertyNormalizer(), "http://127.0.0.1:" + server.getAddress().getPort() + "/rplis-api/postings");
    }

    private static String listingPage(int currentPage, int totalPages, String... items) {
        return "{\"totalPosting\":\"5.322\","
                + "\"paging\":{\"total\":5322,\"offset\":0,\"limit\":30,\"totalPages\":" + totalPages
                + ",\"currentPage\":" + currentPage + "},"
                + "\"listPostings\":[" + String.join(",", items) + "]}";
    }

    private static String emptyPage() {
        return "{\"totalPosting\":\"5.322\",\"paging\":{\"total\":5322,\"totalPages\":178,\"currentPage\":1},\"listPostings\":[]}";
    }

    private record StubResponse(int status, String body) {
    }

    @Test
    void shouldParseRealisticItemsAndStopOnEmptyPage() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(1, 178, APTO, CASA))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestedPages);

        CollectedProperty apto = results.stream()
                .filter(p -> p.externalId().equals("3037995631"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.APARTAMENTO, apto.type());
        assertEquals("Apartamento à venda no EDIFÍCIO ESTAÇÃO CARMEM MARIA, TORRE, Recife, PE", apto.title());
        assertEquals(430959, apto.price().value().intValue());
        assertEquals(50.0, apto.area().value());
        assertEquals(2, apto.bedrooms().value());
        assertEquals("PE", apto.address().state());
        assertEquals("RECIFE", apto.address().city());
        assertEquals("TORRE", apto.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-09-13T15:58:11"), apto.announcedAt());
        assertTrue(apto.url().startsWith("https://www.imovelweb.com.br/propriedades/"));

        CollectedProperty casa = results.stream()
                .filter(p -> p.externalId().equals("3012345678"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.CASA, casa.type());
        assertEquals(100.0, casa.area().value());
        assertEquals("CENTRO", casa.address().neighborhood());
    }

    @Test
    void shouldSkipMalformedItems() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(1, 178,
                        APTO,
                        "{\"title\":\"Sem postingId\"}",
                        "{\"postingId\":\"123\",\"title\":\"Sem preço\",\"url\":\"/x\"}",
                        "{\"postingId\":\"456\",\"title\":\"Sem URL\"}"))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("123")));
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("456")));
    }

    @Test
    void shouldInjectSampleWhenHttpForbidden() {
        pageHandler = pg -> new StubResponse(403, "");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("imovelweb-sample-01", results.get(0).externalId());
        assertEquals(PortalName.IMOVELWEB, results.get(0).portalName());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldInjectSampleWhenFirstPageEmpty() {
        pageHandler = pg -> new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("imovelweb-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldRespectDeclaredTotalPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(1, 1, APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldCapLoopAtMaxPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(pg, 1000, APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(ImovelWebCollectorAdapter.MAX_PAGES, results.size());
        assertEquals(10, requestedPages.size());
        assertFalse(requestedPages.contains(11));
    }

    @Test
    void shouldStopOnUnexpectedResponseStructure() {
        pageHandler = pg -> new StubResponse(200, "{\"something\":\"else\"}");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("imovelweb-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }
}