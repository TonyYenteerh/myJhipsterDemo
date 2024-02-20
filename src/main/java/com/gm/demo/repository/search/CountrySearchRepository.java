package com.gm.demo.repository.search;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;

import com.gm.demo.domain.Country;
import com.gm.demo.repository.CountryRepository;
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
 * Spring Data Elasticsearch repository for the {@link Country} entity.
 */
public interface CountrySearchRepository extends ReactiveElasticsearchRepository<Country, Long>, CountrySearchRepositoryInternal {}

interface CountrySearchRepositoryInternal {
    Flux<Country> search(String query);

    Flux<Country> search(Query query);
}

class CountrySearchRepositoryInternalImpl implements CountrySearchRepositoryInternal {

    private final ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;

    CountrySearchRepositoryInternalImpl(ReactiveElasticsearchTemplate reactiveElasticsearchTemplate) {
        this.reactiveElasticsearchTemplate = reactiveElasticsearchTemplate;
    }

    @Override
    public Flux<Country> search(String query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(queryStringQuery(query));
        return search(nativeSearchQuery);
    }

    @Override
    public Flux<Country> search(Query query) {
        return reactiveElasticsearchTemplate.search(query, Country.class).map(SearchHit::getContent);
    }
}
