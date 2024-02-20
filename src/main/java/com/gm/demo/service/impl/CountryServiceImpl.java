package com.gm.demo.service.impl;

import static org.elasticsearch.index.query.QueryBuilders.*;

import com.gm.demo.domain.Country;
import com.gm.demo.repository.CountryRepository;
import com.gm.demo.repository.search.CountrySearchRepository;
import com.gm.demo.service.CountryService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Implementation for managing {@link Country}.
 */
@Service
@Transactional
public class CountryServiceImpl implements CountryService {

    private final Logger log = LoggerFactory.getLogger(CountryServiceImpl.class);

    private final CountryRepository countryRepository;

    private final CountrySearchRepository countrySearchRepository;

    public CountryServiceImpl(CountryRepository countryRepository, CountrySearchRepository countrySearchRepository) {
        this.countryRepository = countryRepository;
        this.countrySearchRepository = countrySearchRepository;
    }

    @Override
    public Mono<Country> save(Country country) {
        log.debug("Request to save Country : {}", country);
        return countryRepository.save(country).flatMap(countrySearchRepository::save);
    }

    @Override
    public Mono<Country> update(Country country) {
        log.debug("Request to update Country : {}", country);
        return countryRepository.save(country).flatMap(countrySearchRepository::save);
    }

    @Override
    public Mono<Country> partialUpdate(Country country) {
        log.debug("Request to partially update Country : {}", country);

        return countryRepository
            .findById(country.getId())
            .map(existingCountry -> {
                if (country.getCountryName() != null) {
                    existingCountry.setCountryName(country.getCountryName());
                }

                return existingCountry;
            })
            .flatMap(countryRepository::save)
            .flatMap(savedCountry -> {
                countrySearchRepository.save(savedCountry);

                return Mono.just(savedCountry);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Country> findAll() {
        log.debug("Request to get all Countries");
        return countryRepository.findAll();
    }

    public Mono<Long> countAll() {
        return countryRepository.count();
    }

    public Mono<Long> searchCount() {
        return countrySearchRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Country> findOne(Long id) {
        log.debug("Request to get Country : {}", id);
        return countryRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Country : {}", id);
        return countryRepository.deleteById(id).then(countrySearchRepository.deleteById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Country> search(String query) {
        log.debug("Request to search Countries for query {}", query);
        return countrySearchRepository.search(query);
    }
}
