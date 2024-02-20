package com.gm.demo.service.impl;

import static org.elasticsearch.index.query.QueryBuilders.*;

import com.gm.demo.domain.Location;
import com.gm.demo.repository.LocationRepository;
import com.gm.demo.repository.search.LocationSearchRepository;
import com.gm.demo.service.LocationService;
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
 * Service Implementation for managing {@link Location}.
 */
@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);

    private final LocationRepository locationRepository;

    private final LocationSearchRepository locationSearchRepository;

    public LocationServiceImpl(LocationRepository locationRepository, LocationSearchRepository locationSearchRepository) {
        this.locationRepository = locationRepository;
        this.locationSearchRepository = locationSearchRepository;
    }

    @Override
    public Mono<Location> save(Location location) {
        log.debug("Request to save Location : {}", location);
        return locationRepository.save(location).flatMap(locationSearchRepository::save);
    }

    @Override
    public Mono<Location> update(Location location) {
        log.debug("Request to update Location : {}", location);
        return locationRepository.save(location).flatMap(locationSearchRepository::save);
    }

    @Override
    public Mono<Location> partialUpdate(Location location) {
        log.debug("Request to partially update Location : {}", location);

        return locationRepository
            .findById(location.getId())
            .map(existingLocation -> {
                if (location.getStreetAddress() != null) {
                    existingLocation.setStreetAddress(location.getStreetAddress());
                }
                if (location.getPostalCode() != null) {
                    existingLocation.setPostalCode(location.getPostalCode());
                }
                if (location.getCity() != null) {
                    existingLocation.setCity(location.getCity());
                }
                if (location.getStateProvince() != null) {
                    existingLocation.setStateProvince(location.getStateProvince());
                }

                return existingLocation;
            })
            .flatMap(locationRepository::save)
            .flatMap(savedLocation -> {
                locationSearchRepository.save(savedLocation);

                return Mono.just(savedLocation);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Location> findAll() {
        log.debug("Request to get all Locations");
        return locationRepository.findAll();
    }

    public Mono<Long> countAll() {
        return locationRepository.count();
    }

    public Mono<Long> searchCount() {
        return locationSearchRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Location> findOne(Long id) {
        log.debug("Request to get Location : {}", id);
        return locationRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Location : {}", id);
        return locationRepository.deleteById(id).then(locationSearchRepository.deleteById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Location> search(String query) {
        log.debug("Request to search Locations for query {}", query);
        return locationSearchRepository.search(query);
    }
}
