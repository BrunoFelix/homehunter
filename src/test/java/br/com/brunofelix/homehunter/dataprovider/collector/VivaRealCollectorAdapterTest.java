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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class VivaRealCollectorAdapterTest {

    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern FROM_PARAM = Pattern.compile("[?&]from=(\\d+)");

    private static final String APTO =
            "{\"listing\":{\"id\":\"2909080219\",\"externalId\":\"12035-Lc\",\"title\":null,"
                    + "\"description\":\"Apartamento com 2 quartos em Recife\",\"status\":\"ACTIVE\","
                    + "\"createdAt\":\"2026-09-10T19:30:31.897+00:00\","
                    + "\"unitTypes\":[\"APARTMENT\"],\"propertyType\":\"UNIT\","
                    + "\"bedrooms\":2,\"parkingSpaces\":1,\"usableAreas\":[55,45],\"totalAreas\":[55,45],"
                    + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":299000,\"yearlyIptu\":0,\"monthlyCondoFee\":0}],"
                    + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\","
                    + "\"neighborhood\":\"Imbiribeira\",\"locationId\":\"BR>Pernambuco>NULL>Recife>Barrios>Imbiribeira\"}},"
                    + "\"account\":{\"id\":\"br-abc\",\"name\":\"Imob São José\"},"
                    + "\"link\":{\"name\":\"Apartamento com 2 quartos à venda, 55m²\","
                    + "\"href\":\"/imovel/apartamento-2-quartos-imbiribeira-bairros-recife-com-garagem-55m2-venda-RS299000-id-2909080219/\"}}";

    private static final String CASA =
            "{\"listing\":{\"id\":\"3012345678\",\"title\":\"Casa Térrea à venda em Recife\","
                    + "\"createdAt\":\"2026-09-12T09:30:00-03:00\","
                    + "\"unitTypes\":[\"HOUSE\"],\"propertyType\":\"UNIT\","
                    + "\"bedrooms\":3,\"usableAreas\":[\"100\"],\"totalAreas\":[\"120\"],"
                    + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":520000}],"
                    + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\",\"neighborhood\":null}},"
                    + "\"link\":{\"name\":\"Casa Térrea com 3 quartos\","
                    + "\"href\":\"/imovel/casa-terrea-3-quartos-id-3012345678/\"}}";

    private HttpServer server;
    private final List<Integer> requestedPages = new ArrayList<>();
    private Function<Integer, StubResponse> pageHandler;

    @BeforeEach
    void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String domain = exchange.getRequestHeaders().getFirst("x-domain");
        assertNotNull(domain, "expected x-domain header");
        assertEquals("www.vivareal.com.br", domain);

        String query = exchange.getRequestURI().getQuery();
        assertNotNull(query, "expected query string");
        assertTrue(query.contains("categoryPage=RESULT"), "expected categoryPage in: " + query);
        assertTrue(query.contains("business=SALE"), "expected business in: " + query);
        assertTrue(query.contains("addressCity=Recife"), "expected addressCity in: " + query);
        assertTrue(query.contains("addressState=Pernambuco"), "expected addressState in: " + query);
        assertTrue(query.contains("unitTypes=APARTMENT"), "expected unitTypes in: " + query);
        assertTrue(query.contains("__id=search"), "expected __id in: " + query);

        int page = 1;
        Matcher pm = PAGE_PARAM.matcher(query);
        if (pm.find()) {
            page = Integer.parseInt(pm.group(1));
        }
        Matcher fm = FROM_PARAM.matcher(query);
        assertTrue(fm.find(), "expected from param in: " + query);
        assertEquals((page - 1) * VivaRealCollectorAdapter.PAGE_SIZE, Integer.parseInt(fm.group(1)));
        assertTrue(query.contains("size=" + VivaRealCollectorAdapter.PAGE_SIZE), "expected size in: " + query);
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

    @AfterEach
    void tearDownServer() {
        server.stop(0);
    }

    private VivaRealCollectorAdapter adapter() {
        return new VivaRealCollectorAdapter(
                new PortalPropertyNormalizer(), "http://127.0.0.1:" + server.getAddress().getPort() + "/v4/listings");
    }

    private static String listingPage(int totalCount, String... wrappers) {
        return "{\"search\":{\"result\":{\"listings\":[" + String.join(",", wrappers) + "]},\"totalCount\":"
                + totalCount + "},\"page\":{}}";
    }

    private static String emptyPage() {
        return listingPage(5340);
    }

    private record StubResponse(int status, String body) {
    }

    @Test
    void shouldParseRealisticItemsAndStopOnEmptyPage() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(5340, APTO, CASA))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestedPages);

        CollectedProperty apto = results.stream()
                .filter(p -> p.externalId().equals("2909080219"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.APARTAMENTO, apto.type());
        assertEquals("Apartamento com 2 quartos à venda, 55m²", apto.title());
        assertEquals(299000, apto.price().value().intValue());
        assertEquals(55.0, apto.area().value());
        assertEquals(2, apto.bedrooms().value());
        assertEquals("PE", apto.address().state());
        assertEquals("RECIFE", apto.address().city());
        assertEquals("IMBIRIBEIRA", apto.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-09-10T19:30:31.897"), apto.announcedAt());
        assertEquals("https://www.vivareal.com.br/imovel/apartamento-2-quartos-imbiribeira-bairros-recife-com-garagem-55m2-venda-RS299000-id-2909080219/", apto.url());

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
                ? new StubResponse(200, listingPage(5340,
                        APTO,
                        "{\"listing\":{\"id\":null}}",
                        "{\"listing\":{\"id\":\"1\",\"pricingInfos\":[{\"businessType\":\"SALE\"}]}}",
                        "{\"listing\":{\"id\":\"2\",\"title\":\"Sem preço\"}}"))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("1")));
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("2")));
    }

    @Test
    void shouldInjectSampleWhenHttpForbidden() {
        pageHandler = pg -> new StubResponse(403, "");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
        assertEquals(PortalName.VIVA_REAL, results.get(0).portalName());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldInjectSampleWhenFirstPageEmpty() {
        pageHandler = pg -> new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldRespectDeclaredTotalPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(30, APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldCapLoopAtMaxPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(100000, APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(VivaRealCollectorAdapter.MAX_PAGES, results.size());
        assertEquals(10, requestedPages.size());
        assertFalse(requestedPages.contains(11));
    }

    @Test
    void shouldStopOnUnexpectedResponseStructure() {
        pageHandler = pg -> new StubResponse(200, "{\"something\":\"else\"}");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }
}