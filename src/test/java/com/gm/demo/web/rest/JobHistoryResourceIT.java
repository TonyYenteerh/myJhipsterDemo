package com.gm.demo.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.gm.demo.IntegrationTest;
import com.gm.demo.domain.JobHistory;
import com.gm.demo.domain.enumeration.Language;
import com.gm.demo.repository.EntityManager;
import com.gm.demo.repository.JobHistoryRepository;
import com.gm.demo.repository.search.JobHistorySearchRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration tests for the {@link JobHistoryResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class JobHistoryResourceIT {

    private static final Instant DEFAULT_START_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_START_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_END_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_END_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Language DEFAULT_LANGUAGE = Language.FRENCH;
    private static final Language UPDATED_LANGUAGE = Language.ENGLISH;

    private static final String ENTITY_API_URL = "/api/job-histories";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/_search/job-histories";

    private static Random random = new Random();
    private static AtomicLong count = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private JobHistoryRepository jobHistoryRepository;

    @Autowired
    private JobHistorySearchRepository jobHistorySearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private JobHistory jobHistory;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static JobHistory createEntity(EntityManager em) {
        JobHistory jobHistory = new JobHistory().startDate(DEFAULT_START_DATE).endDate(DEFAULT_END_DATE).language(DEFAULT_LANGUAGE);
        return jobHistory;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static JobHistory createUpdatedEntity(EntityManager em) {
        JobHistory jobHistory = new JobHistory().startDate(UPDATED_START_DATE).endDate(UPDATED_END_DATE).language(UPDATED_LANGUAGE);
        return jobHistory;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(JobHistory.class).block();
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
        jobHistorySearchRepository.deleteAll().block();
        assertThat(jobHistorySearchRepository.count().block()).isEqualTo(0);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        deleteEntities(em);
        jobHistory = createEntity(em);
    }

    @Test
    void createJobHistory() throws Exception {
        int databaseSizeBeforeCreate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        // Create the JobHistory
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isCreated();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeCreate + 1);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });
        JobHistory testJobHistory = jobHistoryList.get(jobHistoryList.size() - 1);
        assertThat(testJobHistory.getStartDate()).isEqualTo(DEFAULT_START_DATE);
        assertThat(testJobHistory.getEndDate()).isEqualTo(DEFAULT_END_DATE);
        assertThat(testJobHistory.getLanguage()).isEqualTo(DEFAULT_LANGUAGE);
    }

    @Test
    void createJobHistoryWithExistingId() throws Exception {
        // Create the JobHistory with an existing ID
        jobHistory.setId(1L);

        int databaseSizeBeforeCreate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllJobHistories() {
        // Initialize the database
        jobHistoryRepository.save(jobHistory).block();

        // Get all the jobHistoryList
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
            .value(hasItem(jobHistory.getId().intValue()))
            .jsonPath("$.[*].startDate")
            .value(hasItem(DEFAULT_START_DATE.toString()))
            .jsonPath("$.[*].endDate")
            .value(hasItem(DEFAULT_END_DATE.toString()))
            .jsonPath("$.[*].language")
            .value(hasItem(DEFAULT_LANGUAGE.toString()));
    }

    @Test
    void getJobHistory() {
        // Initialize the database
        jobHistoryRepository.save(jobHistory).block();

        // Get the jobHistory
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, jobHistory.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(jobHistory.getId().intValue()))
            .jsonPath("$.startDate")
            .value(is(DEFAULT_START_DATE.toString()))
            .jsonPath("$.endDate")
            .value(is(DEFAULT_END_DATE.toString()))
            .jsonPath("$.language")
            .value(is(DEFAULT_LANGUAGE.toString()));
    }

    @Test
    void getNonExistingJobHistory() {
        // Get the jobHistory
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingJobHistory() throws Exception {
        // Initialize the database
        jobHistoryRepository.save(jobHistory).block();

        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        jobHistorySearchRepository.save(jobHistory).block();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());

        // Update the jobHistory
        JobHistory updatedJobHistory = jobHistoryRepository.findById(jobHistory.getId()).block();
        updatedJobHistory.startDate(UPDATED_START_DATE).endDate(UPDATED_END_DATE).language(UPDATED_LANGUAGE);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, updatedJobHistory.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(updatedJobHistory))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        JobHistory testJobHistory = jobHistoryList.get(jobHistoryList.size() - 1);
        assertThat(testJobHistory.getStartDate()).isEqualTo(UPDATED_START_DATE);
        assertThat(testJobHistory.getEndDate()).isEqualTo(UPDATED_END_DATE);
        assertThat(testJobHistory.getLanguage()).isEqualTo(UPDATED_LANGUAGE);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<JobHistory> jobHistorySearchList = IterableUtils.toList(jobHistorySearchRepository.findAll().collectList().block());
                JobHistory testJobHistorySearch = jobHistorySearchList.get(searchDatabaseSizeAfter - 1);
                assertThat(testJobHistorySearch.getStartDate()).isEqualTo(UPDATED_START_DATE);
                assertThat(testJobHistorySearch.getEndDate()).isEqualTo(UPDATED_END_DATE);
                assertThat(testJobHistorySearch.getLanguage()).isEqualTo(UPDATED_LANGUAGE);
            });
    }

    @Test
    void putNonExistingJobHistory() throws Exception {
        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        jobHistory.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, jobHistory.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchJobHistory() throws Exception {
        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        jobHistory.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamJobHistory() throws Exception {
        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        jobHistory.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateJobHistoryWithPatch() throws Exception {
        // Initialize the database
        jobHistoryRepository.save(jobHistory).block();

        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();

        // Update the jobHistory using partial update
        JobHistory partialUpdatedJobHistory = new JobHistory();
        partialUpdatedJobHistory.setId(jobHistory.getId());

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedJobHistory.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedJobHistory))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        JobHistory testJobHistory = jobHistoryList.get(jobHistoryList.size() - 1);
        assertThat(testJobHistory.getStartDate()).isEqualTo(DEFAULT_START_DATE);
        assertThat(testJobHistory.getEndDate()).isEqualTo(DEFAULT_END_DATE);
        assertThat(testJobHistory.getLanguage()).isEqualTo(DEFAULT_LANGUAGE);
    }

    @Test
    void fullUpdateJobHistoryWithPatch() throws Exception {
        // Initialize the database
        jobHistoryRepository.save(jobHistory).block();

        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();

        // Update the jobHistory using partial update
        JobHistory partialUpdatedJobHistory = new JobHistory();
        partialUpdatedJobHistory.setId(jobHistory.getId());

        partialUpdatedJobHistory.startDate(UPDATED_START_DATE).endDate(UPDATED_END_DATE).language(UPDATED_LANGUAGE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedJobHistory.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedJobHistory))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        JobHistory testJobHistory = jobHistoryList.get(jobHistoryList.size() - 1);
        assertThat(testJobHistory.getStartDate()).isEqualTo(UPDATED_START_DATE);
        assertThat(testJobHistory.getEndDate()).isEqualTo(UPDATED_END_DATE);
        assertThat(testJobHistory.getLanguage()).isEqualTo(UPDATED_LANGUAGE);
    }

    @Test
    void patchNonExistingJobHistory() throws Exception {
        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        jobHistory.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, jobHistory.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchJobHistory() throws Exception {
        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        jobHistory.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamJobHistory() throws Exception {
        int databaseSizeBeforeUpdate = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        jobHistory.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(jobHistory))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the JobHistory in the database
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteJobHistory() {
        // Initialize the database
        jobHistoryRepository.save(jobHistory).block();
        jobHistoryRepository.save(jobHistory).block();
        jobHistorySearchRepository.save(jobHistory).block();

        int databaseSizeBeforeDelete = jobHistoryRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the jobHistory
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, jobHistory.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        List<JobHistory> jobHistoryList = jobHistoryRepository.findAll().collectList().block();
        assertThat(jobHistoryList).hasSize(databaseSizeBeforeDelete - 1);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(jobHistorySearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchJobHistory() {
        // Initialize the database
        jobHistory = jobHistoryRepository.save(jobHistory).block();
        jobHistorySearchRepository.save(jobHistory).block();

        // Search the jobHistory
        webTestClient
            .get()
            .uri(ENTITY_SEARCH_API_URL + "?query=id:" + jobHistory.getId())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(jobHistory.getId().intValue()))
            .jsonPath("$.[*].startDate")
            .value(hasItem(DEFAULT_START_DATE.toString()))
            .jsonPath("$.[*].endDate")
            .value(hasItem(DEFAULT_END_DATE.toString()))
            .jsonPath("$.[*].language")
            .value(hasItem(DEFAULT_LANGUAGE.toString()));
    }
}
