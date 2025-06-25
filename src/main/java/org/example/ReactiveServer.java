//package org.example;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.annotation.Bean;
//import org.springframework.web.reactive.function.server.*;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//import java.util.Arrays;
//import java.util.Set;
//import java.util.concurrent.ThreadLocalRandom;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//@SpringBootApplication
//public class ReactiveServer {
//
//    // JSON处理器
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    // 响应式路由配置
//    @Bean
//    public RouterFunction<ServerResponse> routes() {
//        return RouterFunctions.route()
//                .GET("/numbers", this::streamNumbers)          // 流式数据接口
//                .GET("/user/{id}", this::fetchUser)           // 按需返回用户数据
//                .build();
//    }
//
//    // 案例1：流式数字接口（背压控制演示）
//    Mono<ServerResponse> streamNumbers(ServerRequest request) {
//        // 从请求参数获取数量限制（按需传输）
//        int limit = Integer.parseInt(request.queryParam("limit").orElse("20"));
//        // 创建每秒生成随机数的流（带背压控制）
//        Flux<String> dataStream = Flux.fromStream(Stream.generate(() ->
//                        "Data:" + ThreadLocalRandom.current().nextInt(100))
//                )
//                .delayElements(Duration.ofMillis(300)) // 控制生成速度
//                .take(limit);                          // 根据limit参数限制数量
//
//        return ServerResponse.ok()
//                .contentType(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
//                .body(dataStream, String.class);
//    }
//
//    // 案例2：动态用户数据接口（按需返回字段）
//    Mono<ServerResponse> fetchUser(ServerRequest request) {
//        String userId = request.pathVariable("id");
//        String fields = request.queryParam("fields").orElse("name,email"); // 默认字段
//
//        // 解析请求字段
//        Set<String> requiredFields = parseFields(fields);
//
//        // 模拟非阻塞数据库查询
//        Mono<String> userMono = Mono.fromCallable(() -> {
//            // 实际项目中替换为Reactive MongoDB/Cassandra等
//            Thread.sleep(50); // 模拟I/O延迟
//            return fetchFromDatabase(userId);
//        }).map(json -> filterFields(json, requiredFields)); // 按需过滤字段
//
//        return ServerResponse.ok()
//                .body(userMono, String.class);
//    }
//
//    // 解析请求字段为集合
//    private Set<String> parseFields(String fields) {
//        return Arrays.stream(fields.split(","))
//                .map(String::trim)
//                .collect(Collectors.toSet());
//    }
//
//    // 按需过滤JSON字段（健壮实现）
//    private String filterFields(String json, Set<String> requiredFields) {
//        try {
//            JsonNode rootNode = objectMapper.readTree(json);
//            ObjectNode filteredNode = objectMapper.createObjectNode();
//
//            // 保留ID字段（通常应始终返回）
//            if (rootNode.has("id")) {
//                filteredNode.set("id", rootNode.get("id"));
//            }
//
//            // 保留请求的字段
//            for (String field : requiredFields) {
//                if (rootNode.has(field)) {
//                    filteredNode.set(field, rootNode.get(field));
//                }
//            }
//
//            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredNode);
//        } catch (JsonProcessingException e) {
//            // 错误处理：返回原始JSON
//            return json;
//        }
//    }
//
//    // 模拟数据库返回（实际使用Reactive仓库）
//    private String fetchFromDatabase(String id) {
//        return String.format("""
//            {
//              "id": "%s",
//              "name": "用户%s",
//              "email": "user%s@example.com",
//              "address": "北京市海淀区",
//              "registrationDate": "2023-01-15"
//            }""", id, id, id);
//    }
//    public static void main(String[] args) {
//        SpringApplication.run(ReactiveServer.class, args);
//    }
//}