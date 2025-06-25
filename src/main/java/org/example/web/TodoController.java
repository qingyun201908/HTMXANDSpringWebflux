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

    // 注入Todo数据仓库接口
    private final TodoRepo repo;
    // 注入Redis响应式模板，用于发布/订阅操作
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    // 定义Redis的频道主题(topic)
    private final ChannelTopic todoTopic = new ChannelTopic("todo_events");
    // 创建多播的Sink用于服务器发送事件(SSE)
    private final Sinks.Many<String> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    // 静态内部类用于表单数据绑定
    public static class Form {
        private String content; // 待办事项内容

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    // 控制器构造函数（依赖注入）
    public TodoController(TodoRepo repo, ReactiveRedisTemplate<String, String> redisTemplate) {
        this.repo = repo;
        this.redisTemplate = redisTemplate;

        // Redis订阅设置：将接收到的消息转发给eventSink
        redisTemplate.listenTo(todoTopic)
                .map(msg -> msg.getMessage()) // 提取消息内容
                .filter(message -> !StringUtils.isEmpty(message)) // 过滤空消息
                .subscribe(eventSink::tryEmitNext); // 将消息推送到sink
    }

    /**
     * 广播Todo事件到Redis频道
     *
     * @param eventType 事件类型（create/update/delete）
     * @param todo 相关的Todo对象
     */
    private void broadcastEvent(String eventType, Todo todo) {
        String messageId = "global_" + todo.getId() + "_" + System.currentTimeMillis();
        String payload = "";

        // 根据事件类型构建不同格式的消息体
        switch (eventType) {
            case "create":
            case "update":
                // 创建/更新事件的SSE格式
                payload = String.format("id:%s\nevent:%s\ndata:%s\n\n",
                        messageId, eventType, renderItem(todo));
                break;
            case "delete":
                // 删除事件的SSE格式
                payload = String.format("id:%s\nevent:delete\ndata:%d\n\n",
                        messageId, todo.getId());
                break;
        }

        // 如果消息非空，则通过Redis发布
        if (StringUtils.hasLength(payload)) {
            redisTemplate.convertAndSend(todoTopic.getTopic(), payload).subscribe();
        }
    }

    /**
     * 获取待办事项列表（服务器发送事件SSE格式）
     *
     * @return SSE数据流
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> list() {
        // 初始化时加载所有待办事项并转为SSE格式
        Flux<String> initialFlux = repo.findAllByOrderByIdDesc()
                .map(todo ->
                        String.format("id:init_%d\nevent:create\ndata:%s\n\n",
                                todo.getId(), renderItem(todo))
                );

        // 合并初始化数据流和实时事件流
        return Flux.merge(initialFlux, eventSink.asFlux())
                .filter(payload -> !StringUtils.isEmpty(payload)); // 过滤空消息
    }

    /**
     * 创建新的待办事项
     *
     * @param form 包含content的表单数据
     * @return 空字符串（遵循POST-Redirect-GET模式）
     */
    @PostMapping
    public Mono<String> create(@ModelAttribute Form form) {
        // 保存新待办事项（HTML转义防止XSS攻击）
        return repo.save(new Todo(null, HtmlUtils.htmlEscape(form.getContent())))
                .doOnSuccess(todo -> broadcastEvent("create", todo)) // 保存成功后广播事件
                .thenReturn(""); // 返回空响应
    }

    /**
     * 删除待办事项
     *
     * @param id 待删除事项的ID
     * @return 空字符串（遵循POST-Redirect-GET模式）
     */
    @PostMapping(path = "/{id}/delete", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> delete(@PathVariable Long id) {
        // 先查询再删除，确保广播正确内容
        return repo.findById(id)
                .doOnSuccess(todo -> broadcastEvent("delete", todo)) // 广播删除事件
                .then(repo.deleteById(id)) // 执行删除操作
                .thenReturn(""); // 返回空响应
    }

    /**
     * 渲染单个待办事项为HTML片段（查看模式）
     *
     * @param t 待渲染的Todo对象
     * @return HTML字符串
     */
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
                        escapeHtml(t.getContent()), // 内容安全处理
                        // 使用当前时间（注意：这显示的是服务器当前时间）
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        t.getId()
                );
    }

    /**
     * 渲染单个待办事项为HTML片段（编辑模式） - 注意：此方法在现有代码中未被调用
     *
     * @param t 待渲染的Todo对象
     * @return HTML字符串（编辑表单）
     */
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

    /**
     * HTML转义工具方法（防止XSS攻击）
     *
     * @param s 原始字符串
     * @return 转义后的安全字符串
     */
    private String escapeHtml(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}