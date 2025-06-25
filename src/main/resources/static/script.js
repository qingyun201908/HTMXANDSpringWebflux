// script.js
// 添加此函数处理删除操作
function handleDelete(todoId) {
    if (!confirm("确定删除这条留言吗？")) return;

    // 添加处理中样式
    const element = document.getElementById(`todo-${todoId}`);
    if (element) element.classList.add("deleting");

    // HTMX执行删除
    htmx.ajax("POST", `/api/todo/${todoId}/delete`, {
        target: `#todo-${todoId}`,
        swap: "outerHTML",
        headers: { "HX-Request": "true" }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    const eventSource = new EventSource('/api/todo');

    // 统一事件处理
    eventSource.onmessage = event => {
        const data = event.data;
        const eventType = event.type || "message";

        console.log(`Received ${eventType} event:`, data);

        try {
            if (eventType === "delete") {
                const todoId = parseInt(data);
                handleDeleteEvent(todoId);
            } else {
                handleDomUpdate(eventType, data);
            }
        } catch (e) {
            console.error("Error processing event:", e);
        }
    };

    eventSource.onerror = e => {
        console.error("SSE error:", e);
        // 简单重连机制
        setTimeout(() => {
            eventSource.close();
            new EventSource('/api/todo');
        }, 3000);
    };
});

function handleDomUpdate(eventType, html) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    const newItem = doc.body.firstElementChild;

    if (!newItem) return;

    const existingItem = document.getElementById(newItem.id);
    const todoList = document.getElementById('todo-list');

    if (existingItem) {
        // 更新现有项目
        existingItem.replaceWith(newItem);
        newItem.classList.add('updated-item');
        setTimeout(() => newItem.classList.remove('updated-item'), 1000);
    } else {
        // 添加新项目
        todoList.insertBefore(newItem, todoList.firstChild);
        newItem.classList.add('new-item');
        setTimeout(() => newItem.classList.remove('new-item'), 500);
        // 添加时间戳
        const now = new Date();
        const timeString = now.toLocaleTimeString();
        const timeElement = document.createElement('div');
        timeElement.className = 'message-time';
        timeElement.innerHTML = `<i class="far fa-clock"></i> ${timeString}`;
        newItem.querySelector('.message-extra').appendChild(timeElement);
    }
}

function handleDeleteEvent(todoId) {
    const element = document.getElementById(`todo-${todoId}`);
    if (element) {
        element.classList.add('deleting');
        // 确保元素最终被移除
        setTimeout(() => element.remove(), 500);
    }
}

// 添加当前时间戳功能
function getCurrentTime() {
    const now = new Date();
    return now.toLocaleTimeString();
}