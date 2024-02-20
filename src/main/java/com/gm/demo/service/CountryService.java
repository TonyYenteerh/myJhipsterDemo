package com.gm.demo.service;

import com.gm.demo.domain.Country;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Country}.
 */
public interface CountryService {
    /**
     * Save a country.
     *
     * @param country the entity to save.
     * @return the persisted entity.
     */
    Mono<Country> save(Country country);

    /**
     * Updates a country.
     *
     * @param country the entity to update.
     * @return the persisted entity.
     */
    Mono<Country> update(Country country);

    /**
     * Partially updates a country.
     *
     * @param country the entity to update partially.
     * @return the persisted entity.
     */
    Mono<Country> partialUpdate(Country country);

    /**
     * Get all the countries.
     *
     * @return the list of entities.
     */
    Flux<Country> findAll();

    /**
     * Returns the number of countries available.
     * @return the number of entities in the database.
     *
     */
    Mono<Long> countAll();

    /**
     * Returns the number of countries available in search repository.
     *
     */
    Mono<Long> searchCount();

    /**
     * Get the "id" country.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<Country> findOne(Long id);

    /**
     * Delete the "id" country.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id);

    /**
     * Search for the country corresponding to the query.
     *
     * @param query the query of the search.
     * @return the list of entities.
     */
    Flux<Country> search(String query);
}
