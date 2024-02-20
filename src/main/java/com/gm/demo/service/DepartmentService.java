package com.gm.demo.service;

import com.gm.demo.domain.Department;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Department}.
 */
public interface DepartmentService {
    /**
     * Save a department.
     *
     * @param department the entity to save.
     * @return the persisted entity.
     */
    Mono<Department> save(Department department);

    /**
     * Updates a department.
     *
     * @param department the entity to update.
     * @return the persisted entity.
     */
    Mono<Department> update(Department department);

    /**
     * Partially updates a department.
     *
     * @param department the entity to update partially.
     * @return the persisted entity.
     */
    Mono<Department> partialUpdate(Department department);

    /**
     * Get all the departments.
     *
     * @return the list of entities.
     */
    Flux<Department> findAll();

    /**
     * Returns the number of departments available.
     * @return the number of entities in the database.
     *
     */
    Mono<Long> countAll();

    /**
     * Returns the number of departments available in search repository.
     *
     */
    Mono<Long> searchCount();

    /**
     * Get the "id" department.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<Department> findOne(Long id);

    /**
     * Delete the "id" department.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id);

    /**
     * Search for the department corresponding to the query.
     *
     * @param query the query of the search.
     * @return the list of entities.
     */
    Flux<Department> search(String query);
}
