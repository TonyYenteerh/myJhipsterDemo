package com.gm.demo.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.gm.demo.IntegrationTest;
import com.gm.demo.domain.Region;
import com.gm.demo.repository.EntityManager;
import com.gm.demo.repository.RegionRepository;
import com.gm.demo.repository.search.RegionSearchRepository;
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
 * Integration tests for the {@link RegionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class RegionResourceIT {

    private static final String DEFAULT_REGION_NAME = "AAAAAAAAAA";
    private static final String UPDATED_REGION_NAME = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/regions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/_search/regions";

    private static Random random = new Random();
    private static AtomicLong count = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RegionSearchRepository regionSearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Region region;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Region createEntity(EntityManager em) {
        Region region = new Region().regionName(DEFAULT_REGION_NAME);
        return region;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Region createUpdatedEntity(EntityManager em) {
        Region region = new Region().regionName(UPDATED_REGION_NAME);
        return region;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Region.class).block();
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
        regionSearchRepository.deleteAll().block();
        assertThat(regionSearchRepository.count().block()).isEqualTo(0);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        deleteEntities(em);
        region = createEntity(em);
    }

    @Test
    void createRegion() throws Exception {
        int databaseSizeBeforeCreate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        // Create the Region
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isCreated();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeCreate + 1);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });
        Region testRegion = regionList.get(regionList.size() - 1);
        assertThat(testRegion.getRegionName()).isEqualTo(DEFAULT_REGION_NAME);
    }

    @Test
    void createRegionWithExistingId() throws Exception {
        // Create the Region with an existing ID
        region.setId(1L);

        int databaseSizeBeforeCreate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllRegionsAsStream() {
        // Initialize the database
        regionRepository.save(region).block();

        List<Region> regionList = webTestClient
            .get()
            .uri(ENTITY_API_URL)
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .returnResult(Region.class)
            .getResponseBody()
            .filter(region::equals)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertThat(regionList).isNotNull();
        assertThat(regionList).hasSize(1);
        Region testRegion = regionList.get(0);
        assertThat(testRegion.getRegionName()).isEqualTo(DEFAULT_REGION_NAME);
    }

    @Test
    void getAllRegions() {
        // Initialize the database
        regionRepository.save(region).block();

        // Get all the regionList
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
            .value(hasItem(region.getId().intValue()))
            .jsonPath("$.[*].regionName")
            .value(hasItem(DEFAULT_REGION_NAME));
    }

    @Test
    void getRegion() {
        // Initialize the database
        regionRepository.save(region).block();

        // Get the region
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, region.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(region.getId().intValue()))
            .jsonPath("$.regionName")
            .value(is(DEFAULT_REGION_NAME));
    }

    @Test
    void getNonExistingRegion() {
        // Get the region
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingRegion() throws Exception {
        // Initialize the database
        regionRepository.save(region).block();

        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        regionSearchRepository.save(region).block();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());

        // Update the region
        Region updatedRegion = regionRepository.findById(region.getId()).block();
        updatedRegion.regionName(UPDATED_REGION_NAME);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, updatedRegion.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(updatedRegion))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        Region testRegion = regionList.get(regionList.size() - 1);
        assertThat(testRegion.getRegionName()).isEqualTo(UPDATED_REGION_NAME);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Region> regionSearchList = IterableUtils.toList(regionSearchRepository.findAll().collectList().block());
                Region testRegionSearch = regionSearchList.get(searchDatabaseSizeAfter - 1);
                assertThat(testRegionSearch.getRegionName()).isEqualTo(UPDATED_REGION_NAME);
            });
    }

    @Test
    void putNonExistingRegion() throws Exception {
        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        region.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, region.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchRegion() throws Exception {
        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        region.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamRegion() throws Exception {
        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        region.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateRegionWithPatch() throws Exception {
        // Initialize the database
        regionRepository.save(region).block();

        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();

        // Update the region using partial update
        Region partialUpdatedRegion = new Region();
        partialUpdatedRegion.setId(region.getId());

        partialUpdatedRegion.regionName(UPDATED_REGION_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedRegion.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedRegion))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        Region testRegion = regionList.get(regionList.size() - 1);
        assertThat(testRegion.getRegionName()).isEqualTo(UPDATED_REGION_NAME);
    }

    @Test
    void fullUpdateRegionWithPatch() throws Exception {
        // Initialize the database
        regionRepository.save(region).block();

        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();

        // Update the region using partial update
        Region partialUpdatedRegion = new Region();
        partialUpdatedRegion.setId(region.getId());

        partialUpdatedRegion.regionName(UPDATED_REGION_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedRegion.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedRegion))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        Region testRegion = regionList.get(regionList.size() - 1);
        assertThat(testRegion.getRegionName()).isEqualTo(UPDATED_REGION_NAME);
    }

    @Test
    void patchNonExistingRegion() throws Exception {
        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        region.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, region.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchRegion() throws Exception {
        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        region.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamRegion() throws Exception {
        int databaseSizeBeforeUpdate = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        region.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(region))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Region in the database
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteRegion() {
        // Initialize the database
        regionRepository.save(region).block();
        regionRepository.save(region).block();
        regionSearchRepository.save(region).block();

        int databaseSizeBeforeDelete = regionRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the region
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, region.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        List<Region> regionList = regionRepository.findAll().collectList().block();
        assertThat(regionList).hasSize(databaseSizeBeforeDelete - 1);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(regionSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchRegion() {
        // Initialize the database
        region = regionRepository.save(region).block();
        regionSearchRepository.save(region).block();

        // Search the region
        webTestClient
            .get()
            .uri(ENTITY_SEARCH_API_URL + "?query=id:" + region.getId())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(region.getId().intValue()))
            .jsonPath("$.[*].regionName")
            .value(hasItem(DEFAULT_REGION_NAME));
    }
}
