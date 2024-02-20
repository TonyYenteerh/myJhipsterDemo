package com.gm.demo.service.impl;

import static org.elasticsearch.index.query.QueryBuilders.*;

import com.gm.demo.domain.Task;
import com.gm.demo.repository.TaskRepository;
import com.gm.demo.repository.search.TaskSearchRepository;
import com.gm.demo.service.TaskService;
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
 * Service Implementation for managing {@link Task}.
 */
@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepository;

    private final TaskSearchRepository taskSearchRepository;

    public TaskServiceImpl(TaskRepository taskRepository, TaskSearchRepository taskSearchRepository) {
        this.taskRepository = taskRepository;
        this.taskSearchRepository = taskSearchRepository;
    }

    @Override
    public Mono<Task> save(Task task) {
        log.debug("Request to save Task : {}", task);
        return taskRepository.save(task).flatMap(taskSearchRepository::save);
    }

    @Override
    public Mono<Task> update(Task task) {
        log.debug("Request to update Task : {}", task);
        return taskRepository.save(task).flatMap(taskSearchRepository::save);
    }

    @Override
    public Mono<Task> partialUpdate(Task task) {
        log.debug("Request to partially update Task : {}", task);

        return taskRepository
            .findById(task.getId())
            .map(existingTask -> {
                if (task.getTitle() != null) {
                    existingTask.setTitle(task.getTitle());
                }
                if (task.getDescription() != null) {
                    existingTask.setDescription(task.getDescription());
                }

                return existingTask;
            })
            .flatMap(taskRepository::save)
            .flatMap(savedTask -> {
                taskSearchRepository.save(savedTask);

                return Mono.just(savedTask);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Task> findAll() {
        log.debug("Request to get all Tasks");
        return taskRepository.findAll();
    }

    public Mono<Long> countAll() {
        return taskRepository.count();
    }

    public Mono<Long> searchCount() {
        return taskSearchRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Task> findOne(Long id) {
        log.debug("Request to get Task : {}", id);
        return taskRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Task : {}", id);
        return taskRepository.deleteById(id).then(taskSearchRepository.deleteById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<Task> search(String query) {
        log.debug("Request to search Tasks for query {}", query);
        return taskSearchRepository.search(query);
    }
}
