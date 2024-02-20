package com.gm.demo.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.gm.demo.IntegrationTest;
import com.gm.demo.domain.Location;
import com.gm.demo.repository.EntityManager;
import com.gm.demo.repository.LocationRepository;
import com.gm.demo.repository.search.LocationSearchRepository;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.collections4.IterableUtils;
import org.assertj.core.util.IterableUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration tests for the {@link LocationResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class LocationResourceIT {

    private static final String DEFAULT_STREET_ADDRESS = "AAAAAAAAAA";
    private static final String UPDATED_STREET_ADDRESS = "BBBBBBBBBB";

    private static final String DEFAULT_POSTAL_CODE = "AAAAAAAAAA";
    private static final String UPDATED_POSTAL_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_CITY = "AAAAAAAAAA";
    private static final String UPDATED_CITY = "BBBBBBBBBB";

    private static final String DEFAULT_STATE_PROVINCE = "AAAAAAAAAA";
    private static final String UPDATED_STATE_PROVINCE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/locations";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/_search/locations";

    private static Random random = new Random();
    private static AtomicLong count = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationSearchRepository locationSearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Location location;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Location createEntity(EntityManager em) {
        Location location = new Location()
            .streetAddress(DEFAULT_STREET_ADDRESS)
            .postalCode(DEFAULT_POSTAL_CODE)
            .city(DEFAULT_CITY)
            .stateProvince(DEFAULT_STATE_PROVINCE);
        return location;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Location createUpdatedEntity(EntityManager em) {
        Location location = new Location()
            .streetAddress(UPDATED_STREET_ADDRESS)
            .postalCode(UPDATED_POSTAL_CODE)
            .city(UPDATED_CITY)
            .stateProvince(UPDATED_STATE_PROVINCE);
        return location;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Location.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @AfterEach
    public void cleanup() {
        deleteEntities(em);
    }

    @AfterEach
    public void cleanupElasticSearchRepository() {
        locationSearchRepository.deleteAll().block();
        assertThat(locationSearchRepository.count().block()).isEqualTo(0);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        deleteEntities(em);
        location = createEntity(em);
    }

    @Test
    void createLocation() throws Exception {
        int databaseSizeBeforeCreate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        // Create the Location
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isCreated();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeCreate + 1);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });
        Location testLocation = locationList.get(locationList.size() - 1);
        assertThat(testLocation.getStreetAddress()).isEqualTo(DEFAULT_STREET_ADDRESS);
        assertThat(testLocation.getPostalCode()).isEqualTo(DEFAULT_POSTAL_CODE);
        assertThat(testLocation.getCity()).isEqualTo(DEFAULT_CITY);
        assertThat(testLocation.getStateProvince()).isEqualTo(DEFAULT_STATE_PROVINCE);
    }

    @Test
    void createLocationWithExistingId() throws Exception {
        // Create the Location with an existing ID
        location.setId(1L);

        int databaseSizeBeforeCreate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllLocationsAsStream() {
        // Initialize the database
        locationRepository.save(location).block();

        List<Location> locationList = webTestClient
            .get()
            .uri(ENTITY_API_URL)
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .returnResult(Location.class)
            .getResponseBody()
            .filter(location::equals)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertThat(locationList).isNotNull();
        assertThat(locationList).hasSize(1);
        Location testLocation = locationList.get(0);
        assertThat(testLocation.getStreetAddress()).isEqualTo(DEFAULT_STREET_ADDRESS);
        assertThat(testLocation.getPostalCode()).isEqualTo(DEFAULT_POSTAL_CODE);
        assertThat(testLocation.getCity()).isEqualTo(DEFAULT_CITY);
        assertThat(testLocation.getStateProvince()).isEqualTo(DEFAULT_STATE_PROVINCE);
    }

    @Test
    void getAllLocations() {
        // Initialize the database
        locationRepository.save(location).block();

        // Get all the locationList
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "?sort=id,desc")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(location.getId().intValue()))
            .jsonPath("$.[*].streetAddress")
            .value(hasItem(DEFAULT_STREET_ADDRESS))
            .jsonPath("$.[*].postalCode")
            .value(hasItem(DEFAULT_POSTAL_CODE))
            .jsonPath("$.[*].city")
            .value(hasItem(DEFAULT_CITY))
            .jsonPath("$.[*].stateProvince")
            .value(hasItem(DEFAULT_STATE_PROVINCE));
    }

    @Test
    void getLocation() {
        // Initialize the database
        locationRepository.save(location).block();

        // Get the location
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, location.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(location.getId().intValue()))
            .jsonPath("$.streetAddress")
            .value(is(DEFAULT_STREET_ADDRESS))
            .jsonPath("$.postalCode")
            .value(is(DEFAULT_POSTAL_CODE))
            .jsonPath("$.city")
            .value(is(DEFAULT_CITY))
            .jsonPath("$.stateProvince")
            .value(is(DEFAULT_STATE_PROVINCE));
    }

    @Test
    void getNonExistingLocation() {
        // Get the location
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingLocation() throws Exception {
        // Initialize the database
        locationRepository.save(location).block();

        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        locationSearchRepository.save(location).block();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());

        // Update the location
        Location updatedLocation = locationRepository.findById(location.getId()).block();
        updatedLocation
            .streetAddress(UPDATED_STREET_ADDRESS)
            .postalCode(UPDATED_POSTAL_CODE)
            .city(UPDATED_CITY)
            .stateProvince(UPDATED_STATE_PROVINCE);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, updatedLocation.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(updatedLocation))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        Location testLocation = locationList.get(locationList.size() - 1);
        assertThat(testLocation.getStreetAddress()).isEqualTo(UPDATED_STREET_ADDRESS);
        assertThat(testLocation.getPostalCode()).isEqualTo(UPDATED_POSTAL_CODE);
        assertThat(testLocation.getCity()).isEqualTo(UPDATED_CITY);
        assertThat(testLocation.getStateProvince()).isEqualTo(UPDATED_STATE_PROVINCE);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Location> locationSearchList = IterableUtils.toList(locationSearchRepository.findAll().collectList().block());
                Location testLocationSearch = locationSearchList.get(searchDatabaseSizeAfter - 1);
                assertThat(testLocationSearch.getStreetAddress()).isEqualTo(UPDATED_STREET_ADDRESS);
                assertThat(testLocationSearch.getPostalCode()).isEqualTo(UPDATED_POSTAL_CODE);
                assertThat(testLocationSearch.getCity()).isEqualTo(UPDATED_CITY);
                assertThat(testLocationSearch.getStateProvince()).isEqualTo(UPDATED_STATE_PROVINCE);
            });
    }

    @Test
    void putNonExistingLocation() throws Exception {
        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        location.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, location.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchLocation() throws Exception {
        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        location.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamLocation() throws Exception {
        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        location.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateLocationWithPatch() throws Exception {
        // Initialize the database
        locationRepository.save(location).block();

        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();

        // Update the location using partial update
        Location partialUpdatedLocation = new Location();
        partialUpdatedLocation.setId(location.getId());

        partialUpdatedLocation.streetAddress(UPDATED_STREET_ADDRESS).city(UPDATED_CITY).stateProvince(UPDATED_STATE_PROVINCE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedLocation.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedLocation))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        Location testLocation = locationList.get(locationList.size() - 1);
        assertThat(testLocation.getStreetAddress()).isEqualTo(UPDATED_STREET_ADDRESS);
        assertThat(testLocation.getPostalCode()).isEqualTo(DEFAULT_POSTAL_CODE);
        assertThat(testLocation.getCity()).isEqualTo(UPDATED_CITY);
        assertThat(testLocation.getStateProvince()).isEqualTo(UPDATED_STATE_PROVINCE);
    }

    @Test
    void fullUpdateLocationWithPatch() throws Exception {
        // Initialize the database
        locationRepository.save(location).block();

        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();

        // Update the location using partial update
        Location partialUpdatedLocation = new Location();
        partialUpdatedLocation.setId(location.getId());

        partialUpdatedLocation
            .streetAddress(UPDATED_STREET_ADDRESS)
            .postalCode(UPDATED_POSTAL_CODE)
            .city(UPDATED_CITY)
            .stateProvince(UPDATED_STATE_PROVINCE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedLocation.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedLocation))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        Location testLocation = locationList.get(locationList.size() - 1);
        assertThat(testLocation.getStreetAddress()).isEqualTo(UPDATED_STREET_ADDRESS);
        assertThat(testLocation.getPostalCode()).isEqualTo(UPDATED_POSTAL_CODE);
        assertThat(testLocation.getCity()).isEqualTo(UPDATED_CITY);
        assertThat(testLocation.getStateProvince()).isEqualTo(UPDATED_STATE_PROVINCE);
    }

    @Test
    void patchNonExistingLocation() throws Exception {
        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        location.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, location.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchLocation() throws Exception {
        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        location.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamLocation() throws Exception {
        int databaseSizeBeforeUpdate = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        location.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(location))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Location in the database
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteLocation() {
        // Initialize the database
        locationRepository.save(location).block();
        locationRepository.save(location).block();
        locationSearchRepository.save(location).block();

        int databaseSizeBeforeDelete = locationRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the location
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, location.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        List<Location> locationList = locationRepository.findAll().collectList().block();
        assertThat(locationList).hasSize(databaseSizeBeforeDelete - 1);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(locationSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchLocation() {
        // Initialize the database
        location = locationRepository.save(location).block();
        locationSearchRepository.save(location).block();

        // Search the location
        webTestClient
            .get()
            .uri(ENTITY_SEARCH_API_URL + "?query=id:" + location.getId())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(location.getId().intValue()))
            .jsonPath("$.[*].streetAddress")
            .value(hasItem(DEFAULT_STREET_ADDRESS))
            .jsonPath("$.[*].postalCode")
            .value(hasItem(DEFAULT_POSTAL_CODE))
            .jsonPath("$.[*].city")
            .value(hasItem(DEFAULT_CITY))
            .jsonPath("$.[*].stateProvince")
            .value(hasItem(DEFAULT_STATE_PROVINCE));
    }
}
