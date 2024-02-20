package com.gm.demo.repository.search;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;

import com.gm.demo.domain.Employee;
import com.gm.demo.repository.EmployeeRepository;
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
 * Spring Data Elasticsearch repository for the {@link Employee} entity.
 */
public interface EmployeeSearchRepository extends ReactiveElasticsearchRepository<Employee, Long>, EmployeeSearchRepositoryInternal {}

interface EmployeeSearchRepositoryInternal {
    Flux<Employee> search(String query, Pageable pageable);

    Flux<Employee> search(Query query);
}

class EmployeeSearchRepositoryInternalImpl implements EmployeeSearchRepositoryInternal {

    private final ReactiveElasticsearchTemplate reactiveElasticsearchTemplate;

    EmployeeSearchRepositoryInternalImpl(ReactiveElasticsearchTemplate reactiveElasticsearchTemplate) {
        this.reactiveElasticsearchTemplate = reactiveElasticsearchTemplate;
    }

    @Override
    public Flux<Employee> search(String query, Pageable pageable) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(queryStringQuery(query));
        nativeSearchQuery.setPageable(pageable);
        return search(nativeSearchQuery);
    }

    @Override
    public Flux<Employee> search(Query query) {
        return reactiveElasticsearchTemplate.search(query, Employee.class).map(SearchHit::getContent);
    }
}
