package com.gm.demo.service;

import com.gm.demo.domain.Region;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Region}.
 */
public interface RegionService {
    /**
     * Save a region.
     *
     * @param region the entity to save.
     * @return the persisted entity.
     */
    Mono<Region> save(Region region);

    /**
     * Updates a region.
     *
     * @param region the entity to update.
     * @return the persisted entity.
     */
    Mono<Region> update(Region region);

    /**
     * Partially updates a region.
     *
     * @param region the entity to update partially.
     * @return the persisted entity.
     */
    Mono<Region> partialUpdate(Region region);

    /**
     * Get all the regions.
     *
     * @return the list of entities.
     */
    Flux<Region> findAll();

    /**
     * Returns the number of regions available.
     * @return the number of entities in the database.
     *
     */
    Mono<Long> countAll();

    /**
     * Returns the number of regions available in search repository.
     *
     */
    Mono<Long> searchCount();

    /**
     * Get the "id" region.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<Region> findOne(Long id);

    /**
     * Delete the "id" region.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id);

    /**
     * Search for the region corresponding to the query.
     *
     * @param query the query of the search.
     * @return the list of entities.
     */
    Flux<Region> search(String query);
}
