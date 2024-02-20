package com.gm.demo.repository.search;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;

import com.gm.demo.domain.Task;
import com.gm.demo.repository.TaskRepository;
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
 * Spring Data Elasticsearch repository for the {@link Task} entity.
 */
public interface TaskSearchRepository extends ReactiveElasticsearchRepository<Task, Long>, TaskSearchRepositoryInternal {}

interface TaskSearchRepositoryInternal {
    Flux<Task> search(String query);

    Flux<Task> search(Query query);
}

class TaskSearchRepositoryInternalImpl implements TaskSearchRepositoryInternal {

    private final ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;

    TaskSearchRepositoryInternalImpl(ReactiveElasticsearchTemplate reactiveElasticsearchTemplate) {
        this.reactiveElasticsearchTemplate = reactiveElasticsearchTemplate;
    }

    @Override
    public Flux<Task> search(String query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(queryStringQuery(query));
        return search(nativeSearchQuery);
    }

    @Override
    public Flux<Task> search(Query query) {
        return reactiveElasticsearchTemplate.search(query, Task.class).map(SearchHit::getContent);
    }
}
