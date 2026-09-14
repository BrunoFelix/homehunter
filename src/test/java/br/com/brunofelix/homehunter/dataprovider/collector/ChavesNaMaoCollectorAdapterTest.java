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
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ChavesNaMaoCollectorAdapterTest {

    private static final String REALISTIC_APTO =
            "{\"id\":44940247,\"title\":\"Apartamento com 2 quartos à venda na Rua Doutor Artur Gonçalves, Madalena, Recife\","
                    + "\"url\":\"/imovel/apartamento-a-venda-2-quartos-com-garagem-pe-recife-madalena-47m2-RS435000/id-44940247/\","
                    + "\"prices\":{\"rawPrice\":435000,\"main\":\"R$ 435.000\"},"
                    + "\"area\":{\"total\":\"47\",\"useful\":\"47\"},\"bedrooms\":{\"count\":2},"
                    + "\"location\":{\"neighborhood\":{\"name\":\"Madalena\"},\"city\":{\"name\":\"Recife\"},\"state\":{\"acronym\":\"PE\"}},"
                    + "\"realtyType\":{\"id\":1},\"createdAt\":\"2026-07-22T10:11:11.000\"}";

    private static final String REALISTIC_CASA =
            "{\"id\":44940248,\"title\":\"Casa com 3 quartos à venda no Pina, Recife\","
                    + "\"url\":\"/imovel/casa-a-venda-3-quartos-pe-recife-pina-120m2-RS480000/id-44940248/\","
                    + "\"prices\":{\"rawPrice\":480000,\"main\":\"R$ 480.000\"},"
                    + "\"area\":{\"total\":\"120\",\"useful\":\"110\"},\"bedrooms\":{\"count\":3},"
                    + "\"location\":{\"neighborhood\":{\"name\":\"Pina\"},\"city\":{\"name\":\"Recife\"},\"state\":{\"acronym\":\"PE\"}},"
                    + "\"realtyType\":{\"id\":4},\"createdAt\":\"2026-07-20T08:00:00.000\"}";

    private HttpServer server;
    private final List<Integer> requestedPages = new ArrayList<>();
    private Function<Integer, StubResponse> pageHandler;

    @BeforeEach
    void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String decoded = URLDecoder.decode(query == null ? "" : query, StandardCharsets.UTF_8);
            assertTrue(decoded.contains("level1=casas-a-venda&level2=pe-recife&filtro=cid:[5302],tim:[1],pmax:500000")
                            && decoded.contains("quebra=[6000]&server=0&viewport=desktop"),
                    "expected fixed Chaves na Mão API params, got: " + decoded);

            int pg = 1;
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("pg=")) {
                        pg = Integer.parseInt(part.substring(3));
                    }
                }
            }
            requestedPages.add(pg);

            StubResponse response = pageHandler.apply(pg);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
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

    private ChavesNaMaoCollectorAdapter adapter() {
        return adapter(0);
    }

    private ChavesNaMaoCollectorAdapter adapter(int maxPages) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new ChavesNaMaoCollectorAdapter(new PortalPropertyNormalizer(), baseUrl, "/", maxPages);
    }

    private static String listingPage(int declaredMaxPages, String... items) {
        return "{\"metadata\":{\"title\":\"Casas e Apartamentos à venda no Recife - PE\",\"totalPages\":" + declaredMaxPages + "},"
                + "\"items\":[" + String.join(",", items) + "]}";
    }

    private static String emptyPage() {
        return "{\"metadata\":{\"title\":\"x\",\"totalPages\":401},\"items\":[]}";
    }

    private static String markersOnlyPage() {
        return "{\"metadata\":{},\"items\":[{\"pagination\":true},{\"banner\":true}]}";
    }

    private record StubResponse(int status, String body) {
    }

    @Test
    void shouldParseRealisticItemsAndStopOnEmptyPage() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(401, REALISTIC_APTO, REALISTIC_CASA))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestedPages);

        CollectedProperty apto = results.stream()
                .filter(p -> p.externalId().equals("44940247"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.APARTAMENTO, apto.type());
        assertEquals("Apartamento com 2 quartos à venda na Rua Doutor Artur Gonçalves, Madalena, Recife", apto.title());
        assertEquals(435000, apto.price().value().intValue());
        assertEquals(47.0, apto.area().value());
        assertEquals(2, apto.bedrooms().value());
        assertEquals("PE", apto.address().state());
        assertEquals("RECIFE", apto.address().city());
        assertEquals("MADALENA", apto.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-07-22T13:11:11"), apto.announcedAt());
        assertTrue(apto.url().startsWith("http://127.0.0.1:"));

        CollectedProperty casa = results.stream()
                .filter(p -> p.externalId().equals("44940248"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.CASA, casa.type());
        assertEquals(110.0, casa.area().value());
        assertEquals("PINA", casa.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-07-20T11:00:00"), casa.announcedAt());
    }

    @Test
    void shouldSkipMarkersAndMalformedItems() {
        pageHandler = pg -> pg == 1
                ? new StubResponse(200, listingPage(401,
                        REALISTIC_APTO,
                        "{\"pagination\":true}",
                        "{\"banner\":true}",
                        REALISTIC_CASA,
                        "{\"id\":999,\"title\":null,\"url\":\"/x\"}",
                        "{\"id\":1000,\"title\":\"Sem preço\",\"url\":\"/x\"}"))
                : new StubResponse(200, emptyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("999")));
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("1000")));
    }

    @Test
    void shouldInjectSampleWhenHttpForbidden() {
        pageHandler = pg -> new StubResponse(403, "");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("chaves-sample-01", results.get(0).externalId());
        assertEquals(PortalName.CHAVES_NA_MAO, results.get(0).portalName());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldInjectSampleWhenFirstPageHasOnlyMarkers() {
        pageHandler = pg -> new StubResponse(200, markersOnlyPage());

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("chaves-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldRespectDeclaredMaxPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(1, REALISTIC_APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals(List.of(1), requestedPages);
    }

    @Test
    void shouldCapLoopAtConfiguredMaxPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(1000, REALISTIC_APTO));

        List<CollectedProperty> results = adapter(3).collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestedPages);
    }

    @Test
    void shouldPaginateUntilDeclaredTotalPages() {
        pageHandler = pg -> new StubResponse(200, listingPage(3, REALISTIC_APTO));

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestedPages);
    }

    @Test
    void shouldStopOnUnexpectedResponseStructure() {
        pageHandler = pg -> new StubResponse(200, "{\"something\":\"else\"}");

        List<CollectedProperty> results = adapter().collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("chaves-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestedPages);
    }
}