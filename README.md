# 消防训练基地器材班组归属绑定系统

## 项目简介

本系统用于消防训练基地管理训练器材、训练班组和器材归属关系，支持器材建档、班组维护、器材归属绑定和统计概览。

## 技术栈

- 前端：Vue 3 + Vite + TypeScript + Element Plus
- 后端：Spring Boot 3 + JDK 17 + Maven
- 数据库：MySQL 8.0
- 缓存：Redis 7
- 容器化：Docker + Docker Compose

## 端口说明

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8126 |
| 后端 API | http://localhost:8136 |
| MySQL | 127.0.0.1:3352 |
| Redis | 127.0.0.1:6425 |

## 启动方式

### 后端单独编译

```bash
cd backend
mvn compile -q
```

### 前端单独构建

```bash
cd frontend
npm ci
npm run build
```

### Docker 构建启动

```bash
docker compose up -d --build
```

### 停止服务

```bash
docker compose down
```

## 访问地址

- 前端页面：http://localhost:8126
- 后端 API：http://localhost:8136/api

## 常见问题

- 前端接口异常时，先确认 Docker 后端容器是否启动，并检查前端 Nginx 代理配置。
- 中文数据显示异常时，确认 MySQL 已使用 `utf8mb4` 字符集，JDBC URL 已包含 `useUnicode=true&characterEncoding=utf8mb4`。
- 端口被占用时，修改 `.env` 或 `docker-compose.yml` 中对应的宿主机端口。
