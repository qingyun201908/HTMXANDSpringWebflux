package org.example.web;

import org.example.model.Todo;
import org.example.repository.TodoRepo;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/todo")
public class TodoController {

    private final TodoRepo repo;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ChannelTopic todoTopic = new ChannelTopic("todo_events");
    private final Sinks.Many<String> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    public static class Form {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public TodoController(TodoRepo repo, ReactiveRedisTemplate<String, String> redisTemplate) {
        this.repo = repo;
        this.redisTemplate = redisTemplate;

        // Redis订阅转发
        redisTemplate.listenTo(todoTopic)
                .map(msg -> msg.getMessage())
                .filter(message -> !StringUtils.isEmpty(message))
                .subscribe(eventSink::tryEmitNext);
    }

    private void broadcastEvent(String eventType, Todo todo) {
        String messageId = "global_" + todo.getId() + "_" + System.currentTimeMillis();
        String payload = "";

        switch (eventType) {
            case "create":
            case "update":
                payload = String.format("id:%s\nevent:%s\ndata:%s\n\n",
                        messageId, eventType, renderItem(todo));
                break;
            case "delete":
                payload = String.format("id:%s\nevent:delete\ndata:%d\n\n",
                        messageId, todo.getId());
                break;
        }

        if (StringUtils.hasLength(payload)) {
            redisTemplate.convertAndSend(todoTopic.getTopic(), payload).subscribe();
        }
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> list() {
        Flux<String> initialFlux = repo.findAllByOrderByIdDesc()
                .map(todo ->
                        String.format("id:init_%d\nevent:create\ndata:%s\n\n",
                                todo.getId(), renderItem(todo))
                );

        return Flux.merge(initialFlux, eventSink.asFlux())
                .filter(payload -> !StringUtils.isEmpty(payload));
    }

    @PostMapping
    public Mono<String> create(@ModelAttribute Form form) {
        return repo.save(new Todo(null, HtmlUtils.htmlEscape(form.getContent())))
                .doOnSuccess(todo -> broadcastEvent("create", todo))
                .thenReturn("");
    }


    @PostMapping(path = "/{id}/delete", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> delete(@PathVariable Long id) {
        return repo.findById(id)
                .doOnSuccess(todo -> broadcastEvent("delete", todo))
                .then(repo.deleteById(id))
                .thenReturn("");
    }


    private String renderItem(Todo t) {
        return """
                <li id="todo-%d" class="message-item">
                  <div class="message-content">
                    %s
                    <div class="message-extra">
                      <div class="message-time"><i class="far fa-clock"></i> %s</div>
                    </div>
                  </div>
                  <div class="message-actions">
                    <button class="delete-btn" onclick="handleDelete(%d)">
                      <i class="fas fa-trash-alt"></i>
                    </button>
                  </div>
                </li>
                """
                .formatted(
                        t.getId(),
                        escapeHtml(t.getContent()),
                        // 改为获取当前系统时间并格式化显示
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        t.getId()
                );
    }

    private String renderItemEditing(Todo t) {
        return """
                <li id="todo-%d" class="message-item editing">
                  <form hx-post="/api/todo/%d" hx-target="#todo-%d" hx-swap="outerHTML" class="edit-form">
                    <input class="edit-input" name="content" value="%s" required autofocus>
                    <div class="form-actions">
                      <button type="submit" class="btn btn-save">
                        <i class="fas fa-check"></i> 保存
                      </button>
                      <button type="button" class="btn btn-cancel" 
                              hx-get="/api/todo/%d"
                              hx-target="#todo-%d"
                              hx-swap="outerHTML">
                        <i class="fas fa-times"></i> 取消
                      </button>
                    </div>
                  </form>
                </li>
                """
                .formatted(
                        t.getId(), t.getId(), t.getId(),
                        escapeHtml(t.getContent()),
                        t.getId(), t.getId()
                );
    }

    private String escapeHtml(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}