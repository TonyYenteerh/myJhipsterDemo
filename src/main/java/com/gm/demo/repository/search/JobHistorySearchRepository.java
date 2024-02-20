package com.gm.demo.repository.search;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;

import com.gm.demo.domain.JobHistory;
import com.gm.demo.repository.JobHistoryRepository;
import java.util.List;
import org.elasticsearch.search.sort.SortBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * Spring Data Elasticsearch repository for the {@link JobHistory} entity.
 */
public interface JobHistorySearchRepository extends ReactiveElasticsearchRepository<JobHistory, Long>, JobHistorySearchRepositoryInternal {}

interface JobHistorySearchRepositoryInternal {
    Flux<JobHistory> search(String query, Pageable pageable);

    Flux<JobHistory> search(Query query);
}

class JobHistorySearchRepositoryInternalImpl implements JobHistorySearchRepositoryInternal {

    private final ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;

    JobHistorySearchRepositoryInternalImpl(ReactiveElasticsearchTemplate reactiveElasticsearchTemplate) {
        this.reactiveElasticsearchTemplate = reactiveElasticsearchTemplate;
    }

    @Override
    public Flux<JobHistory> search(String query, Pageable pageable) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(queryStringQuery(query));
        nativeSearchQuery.setPageable(pageable);
        return search(nativeSearchQuery);
    }

    @Override
    public Flux<JobHistory> search(Query query) {
        return reactiveElasticsearchTemplate.search(query, JobHistory.class).map(SearchHit::getContent);
    }
}
