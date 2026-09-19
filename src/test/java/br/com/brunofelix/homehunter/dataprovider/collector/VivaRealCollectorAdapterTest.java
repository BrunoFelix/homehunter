package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.domain.model.CollectedProperty;
import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.core.domain.model.PropertyType;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class VivaRealCollectorAdapterTest {

    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern FROM_PARAM = Pattern.compile("[?&]from=(\\d+)");
    private static final Pattern CITY_PARAM = Pattern.compile("[?&]addressCity=([^&]+)");

    private static final String API_URL = "https://glue-api.vivareal.com/v4/listings";

    private static final String APTO =
            "{\"listing\":{\"id\":\"2909080219\",\"externalId\":\"12035-Lc\",\"title\":null,"
                    + "\"description\":\"Apartamento com 2 quartos em Recife\",\"status\":\"ACTIVE\","
                    + "\"createdAt\":\"2026-09-10T19:30:31.897+00:00\","
                    + "\"unitTypes\":[\"APARTMENT\"],\"propertyType\":\"UNIT\","
                    + "\"bedrooms\":[2],\"parkingSpaces\":[1],\"usableAreas\":[55,45],\"totalAreas\":[55,45],"
                    + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":299000,\"yearlyIptu\":0,\"monthlyCondoFee\":0}],"
                    + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\","
                    + "\"neighborhood\":\"Imbiribeira\",\"locationId\":\"BR>Pernambuco>NULL>Recife>Barrios>Imbiribeira\"}},"
                    + "\"account\":{\"id\":\"br-abc\",\"name\":\"Imob São José\"},"
                    + "\"link\":{\"name\":\"Apartamento com 2 quartos à venda, 55m²\","
                    + "\"href\":\"/imovel/apartamento-2-quartos-imbiribeira-bairros-recife-com-garagem-55m2-venda-RS299000-id-2909080219/\"}}";

    private static final String CASA =
            "{\"listing\":{\"id\":\"3012345678\",\"title\":\"Casa Térrea à venda em Recife\","
                    + "\"createdAt\":\"2026-09-12T09:30:00Z\","
                    + "\"unitTypes\":[\"HOUSE\"],\"propertyType\":\"UNIT\","
                    + "\"bedrooms\":[3],\"usableAreas\":[\"100\"],\"totalAreas\":[\"120\"],"
                    + "\"pricingInfos\":[{\"businessType\":\"SALE\",\"price\":520000}],"
                    + "\"address\":{\"state\":\"Pernambuco\",\"stateAcronym\":\"PE\",\"city\":\"Recife\",\"neighborhood\":null}},"
                    + "\"link\":{\"name\":\"Casa Térrea com 3 quartos\","
                    + "\"href\":\"/imovel/casa-terrea-3-quartos-id-3012345678/\"}}";

    @Test
    void shouldParseRealisticItemsAndStopOnEmptyPage() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> pg == 1
                ? jsonResult(listingPage(5340, APTO, CASA))
                : jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(2, results.size());
        assertEquals(List.of(1, 2), requestPages(urls));

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
        assertEquals(3, casa.bedrooms().value());
        assertEquals("CENTRO", casa.address().neighborhood());
        assertEquals(LocalDateTime.parse("2026-09-12T09:30:00"), casa.announcedAt());
    }

    @Test
    void shouldSkipMalformedItems() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> pg == 1
                ? jsonResult(listingPage(5340,
                        APTO,
                        "{\"listing\":{\"id\":null}}",
                        "{\"listing\":{\"id\":\"1\",\"pricingInfos\":[{\"businessType\":\"SALE\"}]}}",
                        "{\"listing\":{\"id\":\"2\",\"title\":\"Sem preço\"}}"))
                : jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("1")));
        assertTrue(results.stream().noneMatch(p -> p.externalId().equals("2")));
    }

    @Test
    void shouldInjectSampleWhenHttpForbidden() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> new GlueApiCollectorSupport.CurlResult(403, new byte[0]));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
        assertEquals(PortalName.VIVA_REAL, results.get(0).portalName());
        assertEquals(List.of(1), requestPages(urls));
    }

    @Test
    void shouldInjectSampleWhenFirstPageEmpty() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(emptyPage()));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestPages(urls));
    }

    @Test
    void shouldRespectDeclaredTotalPages() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(listingPage(30, APTO)));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals(List.of(1), requestPages(urls));
    }

    @Test
    void shouldCapLoopAtConfiguredMaxPages() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = new VivaRealCollectorAdapter(
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
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(listingPage(90, APTO)));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(3, results.size());
        assertEquals(List.of(1, 2, 3), requestPages(urls));
    }

    @Test
    void shouldStopOnUnexpectedResponseStructure() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult("{\"something\":\"else\"}"));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
        assertEquals(List.of(1), requestPages(urls));
    }

    @Test
    void shouldInjectSampleWhenCurlFails() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> GlueApiCollectorSupport.CurlResult.failure());

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE"), null));

        assertEquals(1, results.size());
        assertEquals("viva-sample-01", results.get(0).externalId());
    }

    @Test
    void shouldCollectFromMultipleCities() {
        List<String> urls = new ArrayList<>();
        VivaRealCollectorAdapter adapter = adapterWith(urls, pg -> jsonResult(listingPage(10, APTO)));

        List<CollectedProperty> results = adapter.collect(new CollectionScope("PE", List.of("RECIFE", "JABOATAO"), null));

        assertEquals(2, results.size());

        assertTrue(urls.stream().anyMatch(u -> u.contains("addressCity=RECIFE")), "expected a RECIFE request: " + urls);
        assertTrue(urls.stream().anyMatch(u -> u.contains("addressCity=JABOATAO")), "expected a JABOATAO request: " + urls);
    }

    @Test
    void buildUrlShouldExposeApiContract() {
        VivaRealCollectorAdapter adapter = new VivaRealCollectorAdapter(
                new PortalPropertyNormalizer(), API_URL, url -> GlueApiCollectorSupport.CurlResult.failure());

        for (int page = 1; page <= 3; page++) {
            String url = adapter.buildUrl(page, "RECIFE");
            assertValidApiUrl(url, page, "addressCity=RECIFE");
        }
    }

    private static void assertValidApiUrl(String url, int page, String cityFragment) {
        assertTrue(url.startsWith(API_URL + "?"), "expected base + query in: " + url);
        assertTrue(url.contains("categoryPage=RESULT"), "expected categoryPage in: " + url);
        assertTrue(url.contains("business=SALE"), "expected business in: " + url);
        assertTrue(url.contains(cityFragment), "expected " + cityFragment + " in: " + url);
        assertTrue(url.contains("addressState=Pernambuco"), "expected addressState in: " + url);
        assertTrue(url.contains("unitTypes=APARTMENT"), "expected unitTypes in: " + url);
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

    private static VivaRealCollectorAdapter adapterWith(List<String> urls, Function<Integer, GlueApiCollectorSupport.CurlResult> handler) {
        GlueApiCollectorSupport.CurlRunner runner = url -> {
            String city = cityOf(url);
            assertValidApiUrl(url, pageOf(url), "addressCity=" + city);
            urls.add(url);
            return handler.apply(pageOf(url));
        };
        return new VivaRealCollectorAdapter(new PortalPropertyNormalizer(), API_URL, runner);
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