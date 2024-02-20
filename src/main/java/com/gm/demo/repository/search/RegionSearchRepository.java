package com.gm.demo.repository.search;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;

import com.gm.demo.domain.Region;
import com.gm.demo.repository.RegionRepository;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * Spring Data Elasticsearch repository for the {@link Region} entity.
 */
public interface RegionSearchRepository extends ReactiveElasticsearchRepository<Region, Long>, RegionSearchRepositoryInternal {}

interface RegionSearchRepositoryInternal {
    Flux<Region> search(String query);

    Flux<Region> search(Query query);
}

class RegionSearchRepositoryInternalImpl implements RegionSearchRepositoryInternal {

    private final ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;

    RegionSearchRepositoryInternalImpl(ReactiveElasticsearchTemplate reactiveElasticsearchTemplate) {
        this.reactiveElasticsearchTemplate = reactiveElasticsearchTemplate;
    }

    @Override
    public Flux<Region> search(String query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(queryStringQuery(query));
        return search(nativeSearchQuery);
    }

    @Override
    public Flux<Region> search(Query query) {
        return reactiveElasticsearchTemplate.search(query, Region.class).map(SearchHit::getContent);
    }
}
