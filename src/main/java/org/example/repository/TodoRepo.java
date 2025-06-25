package org.example.repository;

import org.example.model.Todo;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

// repository/TodoRepo.java
public interface TodoRepo extends ReactiveCrudRepository<Todo, Long> {
    Flux<Todo> findAllByOrderByIdDesc();
}
