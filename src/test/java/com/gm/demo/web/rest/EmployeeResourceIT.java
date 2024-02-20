package com.gm.demo.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.gm.demo.IntegrationTest;
import com.gm.demo.domain.Employee;
import com.gm.demo.repository.EmployeeRepository;
import com.gm.demo.repository.EntityManager;
import com.gm.demo.repository.search.EmployeeSearchRepository;
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
 * Integration tests for the {@link EmployeeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class EmployeeResourceIT {

    private static final String DEFAULT_FIRST_NAME = "AAAAAAAAAA";
    private static final String UPDATED_FIRST_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_LAST_NAME = "AAAAAAAAAA";
    private static final String UPDATED_LAST_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_PHONE_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_PHONE_NUMBER = "BBBBBBBBBB";

    private static final Instant DEFAULT_HIRE_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_HIRE_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Long DEFAULT_SALARY = 1L;
    private static final Long UPDATED_SALARY = 2L;

    private static final Long DEFAULT_COMMISSION_PCT = 1L;
    private static final Long UPDATED_COMMISSION_PCT = 2L;

    private static final String ENTITY_API_URL = "/api/employees";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/_search/employees";

    private static Random random = new Random();
    private static AtomicLong count = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeSearchRepository employeeSearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Employee employee;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Employee createEntity(EntityManager em) {
        Employee employee = new Employee()
            .firstName(DEFAULT_FIRST_NAME)
            .lastName(DEFAULT_LAST_NAME)
            .email(DEFAULT_EMAIL)
            .phoneNumber(DEFAULT_PHONE_NUMBER)
            .hireDate(DEFAULT_HIRE_DATE)
            .salary(DEFAULT_SALARY)
            .commissionPct(DEFAULT_COMMISSION_PCT);
        return employee;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Employee createUpdatedEntity(EntityManager em) {
        Employee employee = new Employee()
            .firstName(UPDATED_FIRST_NAME)
            .lastName(UPDATED_LAST_NAME)
            .email(UPDATED_EMAIL)
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .hireDate(UPDATED_HIRE_DATE)
            .salary(UPDATED_SALARY)
            .commissionPct(UPDATED_COMMISSION_PCT);
        return employee;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Employee.class).block();
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
        employeeSearchRepository.deleteAll().block();
        assertThat(employeeSearchRepository.count().block()).isEqualTo(0);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        deleteEntities(em);
        employee = createEntity(em);
    }

    @Test
    void createEmployee() throws Exception {
        int databaseSizeBeforeCreate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        // Create the Employee
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isCreated();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeCreate + 1);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });
        Employee testEmployee = employeeList.get(employeeList.size() - 1);
        assertThat(testEmployee.getFirstName()).isEqualTo(DEFAULT_FIRST_NAME);
        assertThat(testEmployee.getLastName()).isEqualTo(DEFAULT_LAST_NAME);
        assertThat(testEmployee.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(testEmployee.getPhoneNumber()).isEqualTo(DEFAULT_PHONE_NUMBER);
        assertThat(testEmployee.getHireDate()).isEqualTo(DEFAULT_HIRE_DATE);
        assertThat(testEmployee.getSalary()).isEqualTo(DEFAULT_SALARY);
        assertThat(testEmployee.getCommissionPct()).isEqualTo(DEFAULT_COMMISSION_PCT);
    }

    @Test
    void createEmployeeWithExistingId() throws Exception {
        // Create the Employee with an existing ID
        employee.setId(1L);

        int databaseSizeBeforeCreate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllEmployees() {
        // Initialize the database
        employeeRepository.save(employee).block();

        // Get all the employeeList
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
            .value(hasItem(employee.getId().intValue()))
            .jsonPath("$.[*].firstName")
            .value(hasItem(DEFAULT_FIRST_NAME))
            .jsonPath("$.[*].lastName")
            .value(hasItem(DEFAULT_LAST_NAME))
            .jsonPath("$.[*].email")
            .value(hasItem(DEFAULT_EMAIL))
            .jsonPath("$.[*].phoneNumber")
            .value(hasItem(DEFAULT_PHONE_NUMBER))
            .jsonPath("$.[*].hireDate")
            .value(hasItem(DEFAULT_HIRE_DATE.toString()))
            .jsonPath("$.[*].salary")
            .value(hasItem(DEFAULT_SALARY.intValue()))
            .jsonPath("$.[*].commissionPct")
            .value(hasItem(DEFAULT_COMMISSION_PCT.intValue()));
    }

    @Test
    void getEmployee() {
        // Initialize the database
        employeeRepository.save(employee).block();

        // Get the employee
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, employee.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(employee.getId().intValue()))
            .jsonPath("$.firstName")
            .value(is(DEFAULT_FIRST_NAME))
            .jsonPath("$.lastName")
            .value(is(DEFAULT_LAST_NAME))
            .jsonPath("$.email")
            .value(is(DEFAULT_EMAIL))
            .jsonPath("$.phoneNumber")
            .value(is(DEFAULT_PHONE_NUMBER))
            .jsonPath("$.hireDate")
            .value(is(DEFAULT_HIRE_DATE.toString()))
            .jsonPath("$.salary")
            .value(is(DEFAULT_SALARY.intValue()))
            .jsonPath("$.commissionPct")
            .value(is(DEFAULT_COMMISSION_PCT.intValue()));
    }

    @Test
    void getNonExistingEmployee() {
        // Get the employee
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingEmployee() throws Exception {
        // Initialize the database
        employeeRepository.save(employee).block();

        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        employeeSearchRepository.save(employee).block();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());

        // Update the employee
        Employee updatedEmployee = employeeRepository.findById(employee.getId()).block();
        updatedEmployee
            .firstName(UPDATED_FIRST_NAME)
            .lastName(UPDATED_LAST_NAME)
            .email(UPDATED_EMAIL)
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .hireDate(UPDATED_HIRE_DATE)
            .salary(UPDATED_SALARY)
            .commissionPct(UPDATED_COMMISSION_PCT);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, updatedEmployee.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(updatedEmployee))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        Employee testEmployee = employeeList.get(employeeList.size() - 1);
        assertThat(testEmployee.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(testEmployee.getLastName()).isEqualTo(UPDATED_LAST_NAME);
        assertThat(testEmployee.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testEmployee.getPhoneNumber()).isEqualTo(UPDATED_PHONE_NUMBER);
        assertThat(testEmployee.getHireDate()).isEqualTo(UPDATED_HIRE_DATE);
        assertThat(testEmployee.getSalary()).isEqualTo(UPDATED_SALARY);
        assertThat(testEmployee.getCommissionPct()).isEqualTo(UPDATED_COMMISSION_PCT);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Employee> employeeSearchList = IterableUtils.toList(employeeSearchRepository.findAll().collectList().block());
                Employee testEmployeeSearch = employeeSearchList.get(searchDatabaseSizeAfter - 1);
                assertThat(testEmployeeSearch.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
                assertThat(testEmployeeSearch.getLastName()).isEqualTo(UPDATED_LAST_NAME);
                assertThat(testEmployeeSearch.getEmail()).isEqualTo(UPDATED_EMAIL);
                assertThat(testEmployeeSearch.getPhoneNumber()).isEqualTo(UPDATED_PHONE_NUMBER);
                assertThat(testEmployeeSearch.getHireDate()).isEqualTo(UPDATED_HIRE_DATE);
                assertThat(testEmployeeSearch.getSalary()).isEqualTo(UPDATED_SALARY);
                assertThat(testEmployeeSearch.getCommissionPct()).isEqualTo(UPDATED_COMMISSION_PCT);
            });
    }

    @Test
    void putNonExistingEmployee() throws Exception {
        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        employee.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, employee.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchEmployee() throws Exception {
        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        employee.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamEmployee() throws Exception {
        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        employee.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateEmployeeWithPatch() throws Exception {
        // Initialize the database
        employeeRepository.save(employee).block();

        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();

        // Update the employee using partial update
        Employee partialUpdatedEmployee = new Employee();
        partialUpdatedEmployee.setId(employee.getId());

        partialUpdatedEmployee.lastName(UPDATED_LAST_NAME).email(UPDATED_EMAIL).commissionPct(UPDATED_COMMISSION_PCT);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedEmployee.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedEmployee))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        Employee testEmployee = employeeList.get(employeeList.size() - 1);
        assertThat(testEmployee.getFirstName()).isEqualTo(DEFAULT_FIRST_NAME);
        assertThat(testEmployee.getLastName()).isEqualTo(UPDATED_LAST_NAME);
        assertThat(testEmployee.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testEmployee.getPhoneNumber()).isEqualTo(DEFAULT_PHONE_NUMBER);
        assertThat(testEmployee.getHireDate()).isEqualTo(DEFAULT_HIRE_DATE);
        assertThat(testEmployee.getSalary()).isEqualTo(DEFAULT_SALARY);
        assertThat(testEmployee.getCommissionPct()).isEqualTo(UPDATED_COMMISSION_PCT);
    }

    @Test
    void fullUpdateEmployeeWithPatch() throws Exception {
        // Initialize the database
        employeeRepository.save(employee).block();

        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();

        // Update the employee using partial update
        Employee partialUpdatedEmployee = new Employee();
        partialUpdatedEmployee.setId(employee.getId());

        partialUpdatedEmployee
            .firstName(UPDATED_FIRST_NAME)
            .lastName(UPDATED_LAST_NAME)
            .email(UPDATED_EMAIL)
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .hireDate(UPDATED_HIRE_DATE)
            .salary(UPDATED_SALARY)
            .commissionPct(UPDATED_COMMISSION_PCT);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedEmployee.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(partialUpdatedEmployee))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        Employee testEmployee = employeeList.get(employeeList.size() - 1);
        assertThat(testEmployee.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(testEmployee.getLastName()).isEqualTo(UPDATED_LAST_NAME);
        assertThat(testEmployee.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testEmployee.getPhoneNumber()).isEqualTo(UPDATED_PHONE_NUMBER);
        assertThat(testEmployee.getHireDate()).isEqualTo(UPDATED_HIRE_DATE);
        assertThat(testEmployee.getSalary()).isEqualTo(UPDATED_SALARY);
        assertThat(testEmployee.getCommissionPct()).isEqualTo(UPDATED_COMMISSION_PCT);
    }

    @Test
    void patchNonExistingEmployee() throws Exception {
        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        employee.setId(count.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, employee.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchEmployee() throws Exception {
        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        employee.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, count.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamEmployee() throws Exception {
        int databaseSizeBeforeUpdate = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        employee.setId(count.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(TestUtil.convertObjectToJsonBytes(employee))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Employee in the database
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteEmployee() {
        // Initialize the database
        employeeRepository.save(employee).block();
        employeeRepository.save(employee).block();
        employeeSearchRepository.save(employee).block();

        int databaseSizeBeforeDelete = employeeRepository.findAll().collectList().block().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the employee
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, employee.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        List<Employee> employeeList = employeeRepository.findAll().collectList().block();
        assertThat(employeeList).hasSize(databaseSizeBeforeDelete - 1);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(employeeSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchEmployee() {
        // Initialize the database
        employee = employeeRepository.save(employee).block();
        employeeSearchRepository.save(employee).block();

        // Search the employee
        webTestClient
            .get()
            .uri(ENTITY_SEARCH_API_URL + "?query=id:" + employee.getId())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(employee.getId().intValue()))
            .jsonPath("$.[*].firstName")
            .value(hasItem(DEFAULT_FIRST_NAME))
            .jsonPath("$.[*].lastName")
            .value(hasItem(DEFAULT_LAST_NAME))
            .jsonPath("$.[*].email")
            .value(hasItem(DEFAULT_EMAIL))
            .jsonPath("$.[*].phoneNumber")
            .value(hasItem(DEFAULT_PHONE_NUMBER))
            .jsonPath("$.[*].hireDate")
            .value(hasItem(DEFAULT_HIRE_DATE.toString()))
            .jsonPath("$.[*].salary")
            .value(hasItem(DEFAULT_SALARY.intValue()))
            .jsonPath("$.[*].commissionPct")
            .value(hasItem(DEFAULT_COMMISSION_PCT.intValue()));
    }
}
