package com.gm.demo.service.impl;

import static org.elasticsearch.index.query.QueryBuilders.*;

import com.gm.demo.domain.Region;
import com.gm.demo.repository.RegionRepository;
import com.gm.demo.repository.search.RegionSearchRepository;
import com.gm.demo.service.RegionService;
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
 * Service Implementation for managing {@link Region}.
 */
@Service
@Transactional
public class RegionServiceImpl implements RegionService {

    private final Logger log = LoggerFactory.getLogger(RegionServiceImpl.class);

    private final RegionRepository regionRepository;

    private final RegionSearchRepository regionSearchRepository;

    public RegionServiceImpl(RegionRepository regionRepository, RegionSearchRepository regionSearchRepository) {
        this.regionRepository = regionRepository;
        this.regionSearchRepository = regionSearchRepository;
    }

    @Override
    public Mono<Region> save(Region region) {
        log.debug("Request to save Region : {}", region);
        return regionRepository.save(region).flatMap(regionSearchRepository::save);
    }

    @Override
    public Mono<Region> update(Region region) {
        log.debug("Request to update Region : {}", region);
        return regionRepository.save(region).flatMap(regionSearchRepository::save);
    }

    @Override
    public Mono<Region> partialUpdate(Region region) {
        log.debug("Request to partially update Region : {}", region);

        return regionRepository
            .findById(region.getId())
            .map(existingRegion -> {
                if (region.getRegionName() != null) {
                    existingRegion.setRegionName(region.getRegionName());
                }

                return existingRegion;
            })
            .flatMap(regionRepository::save)
            .flatMap(savedRegion -> {
                regionSearchRepository.save(savedRegion);

                return Mono.just(savedRegion);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Region> findAll() {
        log.debug("Request to get all Regions");
        return regionRepository.findAll();
    }

    public Mono<Long> countAll() {
        return regionRepository.count();
    }

    public Mono<Long> searchCount() {
        return regionSearchRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Region> findOne(Long id) {
        log.debug("Request to get Region : {}", id);
        return regionRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Region : {}", id);
        return regionRepository.deleteById(id).then(regionSearchRepository.deleteById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Region> search(String query) {
        log.debug("Request to search Regions for query {}", query);
        return regionSearchRepository.search(query);
    }
}
