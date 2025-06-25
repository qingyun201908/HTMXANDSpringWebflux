package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

// model/Todo.java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("todo")
public class Todo {
    @Id
    private Long id;
    private String content;
}
