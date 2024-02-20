package com.gm.demo.service;

import com.gm.demo.domain.JobHistory;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link JobHistory}.
 */
public interface JobHistoryService {
    /**
     * Save a jobHistory.
     *
     * @param jobHistory the entity to save.
     * @return the persisted entity.
     */
    Mono<JobHistory> save(JobHistory jobHistory);

    /**
     * Updates a jobHistory.
     *
     * @param jobHistory the entity to update.
     * @return the persisted entity.
     */
    Mono<JobHistory> update(JobHistory jobHistory);

    /**
     * Partially updates a jobHistory.
     *
     * @param jobHistory the entity to update partially.
     * @return the persisted entity.
     */
    Mono<JobHistory> partialUpdate(JobHistory jobHistory);

    /**
     * Get all the jobHistories.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Flux<JobHistory> findAll(Pageable pageable);

    /**
     * Returns the number of jobHistories available.
     * @return the number of entities in the database.
     *
     */
    Mono<Long> countAll();

    /**
     * Returns the number of jobHistories available in search repository.
     *
     */
    Mono<Long> searchCount();

    /**
     * Get the "id" jobHistory.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<JobHistory> findOne(Long id);

    /**
     * Delete the "id" jobHistory.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id);

    /**
     * Search for the jobHistory corresponding to the query.
     *
     * @param query the query of the search.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Flux<JobHistory> search(String query, Pageable pageable);
}
