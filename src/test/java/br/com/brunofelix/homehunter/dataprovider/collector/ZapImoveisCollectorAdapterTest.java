package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ZapImoveisCollectorAdapterTest {

    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern FROM_PARAM = Pattern.compile("[?&]from=(\\d+)");
    private static final Pattern CITY_PARAM = Pattern.compile("[?&]addressCity=([^&]+)");

    private static final String API_URL = "https://glue-api.zapimoveis.com.br/v4/listings";

    private static final String APTO =
            "{\"listing\":{\"id\":\"zap-001\",\"externalId\":\"5701-Lz\",\"title\":null,"
                    + "\"description\":\"Apartamento com 2 quartos em Boa Viagem\",\"status\":\"ACTIVE\","
                    + "\"createdAt\":\"2026-09-10T19:30:31.897+00:00\","
                    + "\"unitTypes\":[\"APARTMENT\"],\"propertyType\":\"UNIT\","
                    + "\"bedrooms\":[2],\"bathrooms\":[2],\"suites\":[1],\"parkingSpaces\":[1],\"usableAreas\":[55,45],\"totalAreas\":[55,45],"
                    + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":410000,\"yearlyIptu\":135,\"monthlyCondoFee\":740}],"
                    + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\","
                    + "\"neighborhood\":\"Boa Viagem\",\"locationId\":\"BR>Pernambuco>NULL>Recife>Barrios>Boa Viagem\"}},"
                    + "\"medias\":[{\"url\":\"https://resizedimgs.vivareal.com/img/vr-listing/hash-zap-001/{description}.webp?action={action}&dimension={width}x{height}\",\"type\":\"IMAGE\"},"
                    + "{\"url\":\"https://img.zap.com.br/id-zap-001/img2\",\"type\":\"IMAGE\"}],"
                    + "\"account\":{\"id\":\"br-zap\",\"name\":\"Imob Z\"},"
                    + "\"link\":{\"name\":\"Apartamento com 2 quartos à venda, 55m²\","
                    + "\"href\":\"/imovel/apartamento-2-quartos-boa-viagem-bairros-recife-com-garagem-55m2-venda-RS410000-id-zap-001/\"}}";

    private static final String CASA =
            "{\"listing\":{\"id\":\"zap-002\",\"title\":\"Casa Térrea à venda em Recife\","
                    + "\"createdAt\":\"2026-09-12T09:30:00Z\","
                    + "\"unitTypes\":[\"HOUSE\"],\"propertyType\":\"UNIT\","
                    + "\"bedrooms\":[3],\"usableAreas\":[\"100\"],\"totalAreas\":[\"120\"],"
                    + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":520000}],"
                    + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\",\"neighborhood\":null}},"
                    + "\"link\":{\"name\":\"Casa Térrea com 3 quartos\","
                    + "\"href\":\"/imovel/casa-terrea-3-quartos-id-zap-002/\"}}";

    @Test
    void shouldParseRealisticItemsAndStopOnEmptyPage() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> pg == 1
                ? jsonResult(listingPage(5340, APTO, CASA))
                : jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestPages(urls));

        CollectedProperty apto = results.stream()
                .filter(p -> p.externalId().equals("zap-001"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.APARTAMENTO, apto.type());
        assertEquals(PortalName.ZAP_IMOVEIS, apto.portalName());
        assertEquals("Apartamento com 2 quartos à venda, 55m²", apto.title());
        assertEquals(410000, apto.price().value().intValue());
        assertEquals(55.0, apto.area().value());
        assertEquals(2, apto.bedrooms().value());
        assertEquals(2, apto.bathrooms());
        assertEquals(1, apto.suites());
        assertEquals(1, apto.parkingSpaces());
        assertEquals(740, apto.condoFee().intValue());
        assertEquals(135, apto.iptu().intValue());
        assertEquals("PE", apto.address().state());
        assertEquals("RECIFE", apto.address().city());
        assertEquals("BOA VIAGEM", apto.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-09-10T19:30:31.897"), apto.announcedAt());
        assertEquals("https://www.zapimoveis.com.br/imovel/apartamento-2-quartos-boa-viagem-bairros-recife-com-garagem-55m2-venda-RS410000-id-zap-001/", apto.url());
        assertEquals(List.of(
                        "https://resizedimgs.vivareal.com/img/vr-listing/hash-zap-001/apartamento-com-2-quartos-a-venda-55m.webp?action=fit-in&dimension=870x707",
                        "https://img.zap.com.br/id-zap-001/img2"),
                apto.images());

        CollectedProperty casa = results.stream()
                .filter(p -> p.externalId().equals("zap-002"))
                .findFirst().orElseThrow();
        assertEquals(PropertyType.CASA, casa.type());
        assertEquals(100.0, casa.area().value());
        assertEquals(3, casa.bedrooms().value());
        assertEquals("CENTRO", casa.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-09-12T09:30:00"), casa.announcedAt());
    }

    @Test
    void shouldInjectSampleWhenHttpForbidden() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> new GlueApiCollectorSupport.CurlResult(403, new byte[0]));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("zapimoveis-sample-01", results.get(0).externalId());
        assertEquals(PortalName.ZAP_IMOVEIS, results.get(0).portalName());
        assertEquals(List.of(1), requestPages(urls));
    }

    @Test
    void shouldInjectSampleWhenFirstPageEmpty() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("zapimoveis-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestPages(urls));
    }

    @Test
    void shouldCapLoopAtConfiguredMaxPages() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = new ZapImoveisCollectorAdapter(
                new PortalPropertyNormalizer(), API_URL, url -> {
                    urls.add(url);
                    return jsonResult(listingPage(100000, APTO));
                }, 3);

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestPages(urls));
    }

    @Test
    void shouldPaginateUntilDeclaredTotalPages() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(listingPage(90, APTO)));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestPages(urls));
    }

    @Test
    void shouldPauseAtBatchBoundary() {
        List<String> urls = new ArrayList<>();
        GlueApiCollectorSupport.CurlRunner runner = url -> {
            assertValidZapApiUrl(url, pageOf(url), "addressCity=" + cityOf(url));
            urls.add(url);
            return jsonResult(listingPage(605, APTO));
        };
        ZapImoveisCollectorAdapter adapter = new ZapImoveisCollectorAdapter(
                new PortalPropertyNormalizer(), API_URL, runner, 0, 20, Duration.ofMillis(300));

        long start = System.currentTimeMillis();
        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(21, results.size());
        assertEquals(21, requestPages(urls).size());
        assertTrue(elapsed >= 250, "expected a pause between page batches, took " + elapsed + " ms");
    }

    @Test
    void shouldApplyPolitenessDelayPerPage() {
        List<String> urls = new ArrayList<>();
        GlueApiCollectorSupport.CurlRunner runner = url -> {
            assertValidZapApiUrl(url, pageOf(url), "addressCity=" + cityOf(url));
            urls.add(url);
            return jsonResult(listingPage(90, APTO));
        };
        Throttle throttle = Throttle.of(Duration.ofMillis(200), 0, Duration.ZERO);
        ZapImoveisCollectorAdapter adapter = new ZapImoveisCollectorAdapter(
                new PortalPropertyNormalizer(), API_URL, runner, 0, throttle);

        long start = System.currentTimeMillis();
        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestPages(urls));
        assertTrue(elapsed >= 400, "expected ~200ms politeness delay per page, took " + elapsed + " ms");
    }

    @Test
    void shouldInjectSampleWhenCurlFails() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> GlueApiCollectorSupport.CurlResult.failure());

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("zapimoveis-sample-01", results.get(0).externalId());
    }

    @Test
    void shouldFilterResultsByGeographicScope() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> pg == 1
                ? jsonResult(listingPage(5340, APTO))
                : jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("OLINDA"), null));

        assertTrue(results.isEmpty(), "items outside the requested scope must be dropped (no sample leak)");
    }

    @Test
    void shouldCollectFromMultipleCities() {
        List<String> urls = new ArrayList<>();
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(listingPage(10, APTO)));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE", "JABOATAO"), null));

        assertEquals(2, results.size());
        
        // Verifica se ambas as cidades foram requisitadas
        assertTrue(urls.stream().anyMatch(u -> u.contains("addressCity=RECIFE")));
        assertTrue(urls.stream().anyMatch(u -> u.contains("addressCity=JABOATAO")));
    }

    @Test
    void shouldParseQuotedPriceAsNumber() {
        List<String> urls = new ArrayList<>();
        String quotedPrice = "{\"listing\":{\"id\":\"zap-quoted\",\"title\":\"Apartamento com preço textual\","
                + "\"createdAt\":\"2026-09-10T19:30:31.897+00:00\","
                + "\"unitTypes\":[\"APARTMENT\"],\"propertyType\":\"UNIT\","
                + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":\"585000\"}],"
                + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\",\"neighborhood\":\"Casa Forte\"}},"
                + "\"link\":{\"name\":\"Apartamento com preço textual\","
                + "\"href\":\"/imovel/apartamento-preco-textual-id-zap-quoted/\"}}";
        ZapImoveisCollectorAdapter adapter = adapterWith(urls, pg -> pg == 1
                ? jsonResult(listingPage(1, quotedPrice))
                : jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("zap-quoted", results.get(0).externalId());
        assertEquals(585000, results.get(0).price().value().intValue());
    }

    @Test
    void buildUrlShouldExposeApiContract() {
        ZapImoveisCollectorAdapter adapter = new ZapImoveisCollectorAdapter(
                new PortalPropertyNormalizer(), API_URL, url -> GlueApiCollectorSupport.CurlResult.failure());

        for (int page = 1; page <= 3; page++) {
            String url = adapter.buildUrl(page, "RECIFE");
            assertValidZapApiUrl(url, page, "addressCity=RECIFE");
        }
    }

    private static void assertValidZapApiUrl(String url, int page, String cityFragment) {
        assertTrue(url.startsWith(API_URL + "?"), "expected base + query in: " + url);
        assertTrue(url.contains("categoryPage=RESULT"), "expected categoryPage in: " + url);
        assertTrue(url.contains("business=SALE"), "expected business in: " + url);
        assertTrue(url.contains(cityFragment), "expected " + cityFragment + " in: " + url);
        assertTrue(url.contains("addressState=Pernambuco"), "expected addressState in: " + url);
        assertTrue(url.contains("user=ba1dea62-766d-40c1-be67-2ee852f4e384"), "expected zapimoveis user token in: " + url);
        assertTrue(url.contains("unitTypes=HOME%2CAPARTMENT"), "expected HOME+APARTMENT unit types in: " + url);
        assertTrue(url.contains("KITNET"), "expected KITNET unit subtype in: " + url);
        assertTrue(url.contains("legacyVivarealId"), "expected legacyVivarealId in includeFields: " + url);
        assertTrue(url.contains("__id=search"), "expected __id in: " + url);

        Matcher pm = PAGE_PARAM.matcher(url);
        assertTrue(pm.find(), "expected page param in: " + url);
        assertEquals(page, Integer.parseInt(pm.group(1)));

        Matcher fm = FROM_PARAM.matcher(url);
        assertTrue(fm.find(), "expected from param in: " + url);
        assertEquals((page - 1) * GlueApiCollectorSupport.PAGE_SIZE, Integer.parseInt(fm.group(1)));

        assertTrue(url.contains("size=" + GlueApiCollectorSupport.PAGE_SIZE), "expected size in: " + url);
    }

    private static List<Integer> requestPages(List<String> urls) {
        List<Integer> pages = new ArrayList<>();
        for (String url : urls) {
            Matcher pm = PAGE_PARAM.matcher(url);
            assertTrue(pm.find(), "expected page param in: " + url);
            pages.add(Integer.parseInt(pm.group(1)));
        }
        return pages;
    }

    private static ZapImoveisCollectorAdapter adapterWith(List<String> urls, Function<Integer, GlueApiCollectorSupport.CurlResult> handler) {
        GlueApiCollectorSupport.CurlRunner runner = url -> {
            String city = cityOf(url);
            assertValidZapApiUrl(url, pageOf(url), "addressCity=" + city);
            urls.add(url);
            return handler.apply(pageOf(url));
        };
        return new ZapImoveisCollectorAdapter(new PortalPropertyNormalizer(), API_URL, runner);
    }

    private static String cityOf(String url) {
        Matcher cm = CITY_PARAM.matcher(url);
        if (!cm.find()) {
            throw new IllegalArgumentException("no addressCity param in: " + url);
        }
        return cm.group(1);
    }

    private static int pageOf(String url) {
        Matcher pm = PAGE_PARAM.matcher(url);
        if (!pm.find()) {
            throw new IllegalArgumentException("no page param in: " + url);
        }
        return Integer.parseInt(pm.group(1));
    }

    private static String listingPage(int totalCount, String... wrappers) {
        return "{\"search\":{\"result\":{\"listings\":[" + String.join(",", wrappers) + "]},\"totalCount\":"
                + totalCount + "},\"page\":{}}";
    }

    private static String emptyPage() {
        return listingPage(5340);
    }

    private static GlueApiCollectorSupport.CurlResult jsonResult(String json) {
        return new GlueApiCollectorSupport.CurlResult(200, json.getBytes(StandardCharsets.UTF_8));
    }
}