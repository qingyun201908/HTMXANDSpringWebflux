-- schema.sql: 在 H2（或其他 R2DBC 支持的数据库）中创建 todo 表
CREATE TABLE IF NOT EXISTS todo (
    id IDENTITY PRIMARY KEY,
    content VARCHAR(255) NOT NULL
);
