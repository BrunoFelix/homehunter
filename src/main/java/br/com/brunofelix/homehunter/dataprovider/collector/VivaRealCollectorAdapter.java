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
            FIXED_PARAMS_BEFORE_PAGE,
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
}