package com.gm.demo.service;

import com.gm.demo.domain.Task;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Task}.
 */
public interface TaskService {
    /**
     * Save a task.
     *
     * @param task the entity to save.
     * @return the persisted entity.
     */
    Mono<Task> save(Task task);

    /**
     * Updates a task.
     *
     * @param task the entity to update.
     * @return the persisted entity.
     */
    Mono<Task> update(Task task);

    /**
     * Partially updates a task.
     *
     * @param task the entity to update partially.
     * @return the persisted entity.
     */
    Mono<Task> partialUpdate(Task task);

    /**
     * Get all the tasks.
     *
     * @return the list of entities.
     */
    Flux<Task> findAll();

    /**
     * Returns the number of tasks available.
     * @return the number of entities in the database.
     *
     */
    Mono<Long> countAll();

    /**
     * Returns the number of tasks available in search repository.
     *
     */
    Mono<Long> searchCount();

    /**
     * Get the "id" task.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<Task> findOne(Long id);

    /**
     * Delete the "id" task.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id);

    /**
     * Search for the task corresponding to the query.
     *
     * @param query the query of the search.
     * @return the list of entities.
     */
    Flux<Task> search(String query);
}
