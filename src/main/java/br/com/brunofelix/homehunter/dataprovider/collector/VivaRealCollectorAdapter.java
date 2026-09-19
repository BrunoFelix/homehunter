package br.com.brunofelix.homehunter.dataprovider.collector;

import br.com.brunofelix.homehunter.core.domain.model.PortalName;
import br.com.brunofelix.homehunter.dataprovider.collector.anticorruption.PortalPropertyNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@ConditionalOnProperty(name = "app.collector.vivareal.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class VivaRealCollectorAdapter extends GlueApiCollectorSupport {

    static final String DEFAULT_API_URL = "https://glue-api.vivareal.com/v4/listings";

    private static final String FIXED_PARAMS_TEMPLATE =
            "?categoryPage=RESULT&business=SALE&parentId=null&listingType=USED&images=webp"
                    + "&user=6bbc5807-51b7-404f-8be1-4dd1c6e507ab&portal=VIVAREAL"
                    + "&__zt=mtc%3Adeduplication2023%2Cmtc%3Adeboost"
                    + "&addressCity=%s&addressZone=&addressStreet="
                    + "&addressLocationId=BR%3EPernambuco%3ENULL%3E%s"
                    + "&addressState=Pernambuco&addressNeighborhood="
                    + "&addressPointLat=-8.05774&addressPointLon=-34.882963"
                    + "&addressType=city&unitTypes=APARTMENT&unitTypesV3=APARTMENT"
                    + "&unitSubTypes=UnitSubType_NONE%2CDUPLEX%2CLOFT%2CSTUDIO%2CTRIPLEX"
                    + "&usageTypes=RESIDENTIAL";

    private static final String INCLUDE_FIELDS_SUFFIX = "&__id=search";

    private static final PortalConfig VIVA_CONFIG = new PortalConfig(
            "VivaReal",
            PortalName.VIVA_REAL,
            "https://www.vivareal.com.br",
            "www.vivareal.com.br",
            "vivareal",
            "viva-sample-01",
            "Apartamento Exemplo VivaReal",
            BigDecimal.valueOf(410000),
            80.0,
            3,
            FIXED_PARAMS_TEMPLATE,
            INCLUDE_FIELDS_SUFFIX
    );

    @Autowired
    public VivaRealCollectorAdapter(PortalPropertyNormalizer normalizer,
                                   @Value("${app.collector.max-pages:0}") int maxPages,
                                   @Value("${app.collector.pause-every-pages:20}") int pauseEveryPages,
                                   @Value("${app.collector.pause-duration:10s}") Duration pauseDuration,
                                   @Value("${app.collector.politeness-delay:500ms}") Duration politenessDelay) {
        super(normalizer, DEFAULT_API_URL, VIVA_CONFIG, maxPages, Throttle.of(politenessDelay, pauseEveryPages, pauseDuration));
    }

    VivaRealCollectorAdapter(PortalPropertyNormalizer normalizer, String apiUrl, CurlRunner curlRunner) {
        this(normalizer, apiUrl, curlRunner, 0);
    }

    VivaRealCollectorAdapter(PortalPropertyNormalizer normalizer, String apiUrl, CurlRunner curlRunner, int maxPages) {
        super(normalizer, apiUrl, curlRunner, VIVA_CONFIG, maxPages, Throttle.none());
    }

    @Override
    protected String buildUrl(int page, String city) {
        String formattedCity = city.replaceAll(" ", "+");
        String params = FIXED_PARAMS_TEMPLATE.replace("%s", formattedCity);
        return DEFAULT_API_URL + params
                + "&page=" + page
                + "&size=" + PAGE_SIZE
                + "&from=" + ((page - 1) * PAGE_SIZE)
                + INCLUDE_FIELDS_SUFFIX;
    }
}
