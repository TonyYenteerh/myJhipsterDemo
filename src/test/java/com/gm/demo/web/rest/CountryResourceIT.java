package com.gm.demo.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.gm.demo.IntegrationTest;
import com.gm.demo.domain.Country;
import com.gm.demo.repository.CountryRepository;
import com.gm.demo.repository.EntityManager;
import com.gm.demo.repository.search.CountrySearchRepository;
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
 * Integration tests for the {@link CountryResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class CountryResourceIT {

    private static final String DEFAULT_COUNTRY_NAME = "AAAAAAAAAA";
    private static final String UPDATED_COUNTRY_NAME = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/countries";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/_search/countries";

    private static Random random = new Random();
    private static AtomicLong count = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CountrySearchRepository countrySearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Country country;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Country createEntity(EntityManager em) {
        Country country = new Country().countryName(DEFAULT_COUNTRY_NAME);
        return country;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Country createUpdatedEntity(EntityManager em) {
        Country country = new Country().countryName(UPDATED_COUNTRY_NAME);
        return country;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Country.class).block();
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
        countrySearchRepository.deleteAll().block();
        assertThat(countrySearchRepository.count().block()).isEqualTo(0);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        deleteEntities(em);
        country = createEntity(em);
    }

    @Test
    void createCountry() throws Exception {
        int databaseSizeBeforeCreate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        // Create the Country
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isCreated();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeCreate + 1);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });
        Country testCountry = countryList.get(countryList.size() - 1);
        assertThat(testCountry.getCountryName()).isEqualTo(DEFAULT_COUNTRY_NAME);
    }

    @Test
    void createCountryWithExistingId() throws Exception {
        // Create the Country with an existing ID
        country.setId(1L);

        int databaseSizeBeforeCreate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllCountriesAsStream() {
        // Initialize the database
        countryRepository.save(country).block();

        List<Country> countryList = webTestClient
            .get()
            .uri(ENTITY_API_URL)
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .returnResult(Country.class)
            .getResponseBody()
            .filter(country::equals)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertThat(countryList).isNotNull();
        assertThat(countryList).hasSize(1);
        Country testCountry = countryList.get(0);
        assertThat(testCountry.getCountryName()).isEqualTo(DEFAULT_COUNTRY_NAME);
    }

    @Test
    void getAllCountries() {
        // Initialize the database
        countryRepository.save(country).block();

        // Get all the countryList
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
            .value(hasItem(country.getId().intValue()))
            .jsonPath("$.[*].countryName")
            .value(hasItem(DEFAULT_COUNTRY_NAME));
    }

    @Test
    void getCountry() {
        // Initialize the database
        countryRepository.save(country).block();

        // Get the country
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, country.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(country.getId().intValue()))
            .jsonPath("$.countryName")
            .value(is(DEFAULT_COUNTRY_NAME));
    }

    @Test
    void getNonExistingCountry() {
        // Get the country
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingCountry() throws Exception {
        // Initialize the database
        countryRepository.save(country).block();

        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        countrySearchRepository.save(country).block();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());

        // Update the country
        Country updatedCountry = countryRepository.findById(country.getId()).block();
        updatedCountry.countryName(UPDATED_COUNTRY_NAME);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, updatedCountry.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(updatedCountry))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        Country testCountry = countryList.get(countryList.size() - 1);
        assertThat(testCountry.getCountryName()).isEqualTo(UPDATED_COUNTRY_NAME);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Country> countrySearchList = IterableUtils.toList(countrySearchRepository.findAll().collectList().block());
                Country testCountrySearch = countrySearchList.get(searchDatabaseSizeAfter - 1);
                assertThat(testCountrySearch.getCountryName()).isEqualTo(UPDATED_COUNTRY_NAME);
            });
    }

    @Test
    void putNonExistingCountry() throws Exception {
        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        country.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, country.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchCountry() throws Exception {
        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        country.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamCountry() throws Exception {
        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        country.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateCountryWithPatch() throws Exception {
        // Initialize the database
        countryRepository.save(country).block();

        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();

        // Update the country using partial update
        Country partialUpdatedCountry = new Country();
        partialUpdatedCountry.setId(country.getId());

        partialUpdatedCountry.countryName(UPDATED_COUNTRY_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedCountry.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedCountry))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        Country testCountry = countryList.get(countryList.size() - 1);
        assertThat(testCountry.getCountryName()).isEqualTo(UPDATED_COUNTRY_NAME);
    }

    @Test
    void fullUpdateCountryWithPatch() throws Exception {
        // Initialize the database
        countryRepository.save(country).block();

        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();

        // Update the country using partial update
        Country partialUpdatedCountry = new Country();
        partialUpdatedCountry.setId(country.getId());

        partialUpdatedCountry.countryName(UPDATED_COUNTRY_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedCountry.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedCountry))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        Country testCountry = countryList.get(countryList.size() - 1);
        assertThat(testCountry.getCountryName()).isEqualTo(UPDATED_COUNTRY_NAME);
    }

    @Test
    void patchNonExistingCountry() throws Exception {
        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        country.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, country.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchCountry() throws Exception {
        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        country.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamCountry() throws Exception {
        int databaseSizeBeforeUpdate = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        country.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(country))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Country in the database
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteCountry() {
        // Initialize the database
        countryRepository.save(country).block();
        countryRepository.save(country).block();
        countrySearchRepository.save(country).block();

        int databaseSizeBeforeDelete = countryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the country
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, country.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        List<Country> countryList = countryRepository.findAll().collectList().block();
        assertThat(countryList).hasSize(databaseSizeBeforeDelete - 1);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(countrySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchCountry() {
        // Initialize the database
        country = countryRepository.save(country).block();
        countrySearchRepository.save(country).block();

        // Search the country
        webTestClient
            .get()
            .uri(ENTITY_SEARCH_API_URL + "?query=id:" + country.getId())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(country.getId().intValue()))
            .jsonPath("$.[*].countryName")
            .value(hasItem(DEFAULT_COUNTRY_NAME));
    }
}
