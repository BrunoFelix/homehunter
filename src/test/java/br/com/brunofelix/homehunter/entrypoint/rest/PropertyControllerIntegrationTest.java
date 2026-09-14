package br.com.brunofelix.homehunter.entrypoint.rest;

import br.com.brunofelix.homehunter.core.application.model.CollectionScope;
import br.com.brunofelix.homehunter.core.application.model.PagedResult;
import br.com.brunofelix.homehunter.core.application.model.SyncStatus;
import br.com.brunofelix.homehunter.core.application.port.in.GetPropertyInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SearchPropertiesInputPort;
import br.com.brunofelix.homehunter.core.application.port.in.SyncPropertiesInputPort;
import br.com.brunofelix.homehunter.core.domain.model.*;
import br.com.brunofelix.homehunter.entrypoint.rest.mapper.PropertyRestMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Standalone MockMvc (sem contexto Spring): o spring-boot-test-autoconfigure do
// Boot 4.x nao expoe mais @AutoConfigureMockMvc, entao o controller e testado
// isolado com ports mockados, validando binding de params e estrutura do JSON.
class PropertyControllerIntegrationTest {

    private MockMvc mockMvc;
    private SearchPropertiesInputPort searchPort;
    private SyncPropertiesInputPort syncPort;
    private GetPropertyInputPort getPort;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Property sampleProperty;

    @BeforeEach
    void setUp() {
        searchPort = mock(SearchPropertiesInputPort.class);
        syncPort = mock(SyncPropertiesInputPort.class);
        getPort = mock(GetPropertyInputPort.class);

        PropertyController controller = new PropertyController(
                searchPort, syncPort, getPort, new PropertyRestMapper());

        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();

        sampleProperty = Property.createFrom(
                new CollectedProperty(
                        "Apartamento Boa Viagem",
                        PropertyType.APARTAMENTO,
                        new Price(BigDecimal.valueOf(350000)),
                        new Area(80.0),
                        new Bedrooms(3),
                        new Address("PE", "Recife", "Boa Viagem", "Av Boa Viagem"),
                        PortalName.ZAP_IMOVEIS,
                        "ext-test-001",
                        "https://zap.com/001",
                        LocalDateTime.now()
                ),
                LocalDateTime.now()
        );
    }

    // ==================== GET /api/v1/properties ====================

    @Test
    void searchEndpoint_withDefaults_shouldReturnPagedResult() throws Exception {
        when(searchPort.search(any())).thenReturn(
                new PagedResult<>(List.of(sampleProperty), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.content[0].id", notNullValue()))
                .andExpect(jsonPath("$.content[0].title", is("Apartamento Boa Viagem")))
                .andExpect(jsonPath("$.content[0].type", is("APARTAMENTO")))
                .andExpect(jsonPath("$.content[0].price", is(350000)))
                .andExpect(jsonPath("$.content[0].area", is(80.0)))
                .andExpect(jsonPath("$.content[0].bedrooms", is(3)))
                .andExpect(jsonPath("$.content[0].state", is("PE")))
                .andExpect(jsonPath("$.content[0].city", is("RECIFE")))
                .andExpect(jsonPath("$.content[0].neighborhood", is("BOA VIAGEM")))
                .andExpect(jsonPath("$.content[0].sources", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sources[0].portalName", is("ZAP_IMOVEIS")));

        ArgumentCaptor<PropertySearchCriteria> captor =
                ArgumentCaptor.forClass(PropertySearchCriteria.class);
        verify(searchPort).search(captor.capture());
        assertEquals("PE", captor.getValue().state());
        assertEquals("RECIFE", captor.getValue().city());
        assertNull(captor.getValue().type());
        assertEquals(0, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void searchEndpoint_withAllFilters_shouldForwardThemToUseCase() throws Exception {
        when(searchPort.search(any())).thenReturn(
                new PagedResult<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/properties")
                        .param("state", "PE")
                        .param("city", "Recife")
                        .param("neighborhood", "Boa Viagem")
                        .param("type", "APARTAMENTO")
                        .param("minPrice", "200000")
                        .param("maxPrice", "500000")
                        .param("minArea", "60")
                        .param("maxArea", "150")
                        .param("bedrooms", "3")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        ArgumentCaptor<PropertySearchCriteria> captor =
                ArgumentCaptor.forClass(PropertySearchCriteria.class);
        verify(searchPort).search(captor.capture());
        PropertySearchCriteria criteria = captor.getValue();
        assertEquals("PE", criteria.state());
        assertEquals("RECIFE", criteria.city());
        assertEquals("BOA VIAGEM", criteria.neighborhood());
        assertEquals(PropertyType.APARTAMENTO, criteria.type());
        assertEquals(0, new BigDecimal("200000").compareTo(criteria.minPrice()));
        assertEquals(0, new BigDecimal("500000").compareTo(criteria.maxPrice()));
        assertEquals(60.0, criteria.minArea());
        assertEquals(150.0, criteria.maxArea());
        assertEquals(3, criteria.bedrooms());
    }

    @Test
    void searchEndpoint_withPagination_shouldForwardPageAndSize() throws Exception {
        when(searchPort.search(any())).thenReturn(
                new PagedResult<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/properties")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.size", is(5)));

        ArgumentCaptor<PropertySearchCriteria> captor =
                ArgumentCaptor.forClass(PropertySearchCriteria.class);
        verify(searchPort).search(captor.capture());
        assertEquals(2, captor.getValue().page());
        assertEquals(5, captor.getValue().size());
    }

    // ==================== GET /api/v1/properties/{id} ====================

    @Test
    void getByIdEndpoint_whenFound_shouldReturnProperty() throws Exception {
        when(getPort.getById(any())).thenReturn(Optional.of(sampleProperty));

        mockMvc.perform(get("/api/v1/properties/" + sampleProperty.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sampleProperty.getId().value())))
                .andExpect(jsonPath("$.title", is("Apartamento Boa Viagem")))
                .andExpect(jsonPath("$.type", is("APARTAMENTO")))
                .andExpect(jsonPath("$.price", is(350000)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].portalName", is("ZAP_IMOVEIS")))
                .andExpect(jsonPath("$.sources[0].externalId", is("ext-test-001")))
                .andExpect(jsonPath("$.sources[0].url", is("https://zap.com/001")))
                .andExpect(jsonPath("$.sources[0].price", is(350000)))
                .andExpect(jsonPath("$.sources[0].collectedAt", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void getByIdEndpoint_whenMissing_shouldReturn404() throws Exception {
        when(getPort.getById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/properties/nao-existe"))
                .andExpect(status().isNotFound());
    }

    // ==================== POST /api/v1/properties/sync ====================

    @Test
    void syncEndpoint_withNoBody_shouldEnqueueWithDefaultScope() throws Exception {
        when(syncPort.sync(any())).thenReturn(SyncStatus.ENQUEUED);

        mockMvc.perform(post("/api/v1/properties/sync")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("enqueued")))
                .andExpect(jsonPath("$.message", is("Portal synchronization batch started")));

        ArgumentCaptor<CollectionScope> captor = ArgumentCaptor.forClass(CollectionScope.class);
        verify(syncPort).sync(captor.capture());
        assertEquals("PE", captor.getValue().state());
    }

    @Test
    void syncEndpoint_withFilters_shouldMapPreStorageFilter() throws Exception {
        when(syncPort.sync(any())).thenReturn(SyncStatus.ENQUEUED);

        String body = """
                {"state":"PE","cities":["RECIFE"],"type":"APARTAMENTO",
                 "minPrice":200000,"maxPrice":500000,
                 "minArea":60,"maxArea":150,"bedrooms":3,
                 "neighborhood":"BOA VIAGEM"}""";

        mockMvc.perform(post("/api/v1/properties/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("enqueued")));

        ArgumentCaptor<CollectionScope> captor = ArgumentCaptor.forClass(CollectionScope.class);
        verify(syncPort).sync(captor.capture());
        assertNotNull(captor.getValue().filter());
        assertTrue(captor.getValue().filter().hasFilter());
        assertEquals(PropertyType.APARTAMENTO, captor.getValue().filter().type());
        assertEquals(0, new BigDecimal("200000").compareTo(captor.getValue().filter().minPrice()));
        assertEquals(0, new BigDecimal("500000").compareTo(captor.getValue().filter().maxPrice()));
        assertEquals(60.0, captor.getValue().filter().minArea());
        assertEquals(150.0, captor.getValue().filter().maxArea());
        assertEquals(3, captor.getValue().filter().bedrooms());
        assertEquals("BOA VIAGEM", captor.getValue().filter().neighborhood());
    }

    @Test
    void syncEndpoint_whenAlreadyRunning_shouldReturn409() throws Exception {
        when(syncPort.sync(any())).thenReturn(SyncStatus.REJECTED_RUNNING);

        mockMvc.perform(post("/api/v1/properties/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is("rejected")));
    }
}
