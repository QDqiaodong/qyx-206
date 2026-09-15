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

## 课目占用

班长可把**本班组名下已归属**的器材挂到某个训练日的课目时段，解决多班组同时段抢同一件器材的问题。

业务规则：

- 开始/结束必须成对，且结束必须晚于开始；首尾相接（如 09:00-10:00 与 10:00-11:00）不算交叉。
- 同一件器材同一天时间交叉的有效占用不能并存；两个班长几乎同时挂交叉时段时，靠**器材行锁（SELECT … FOR UPDATE）串行化**，先落地者生效，后落地者返回冲突（400），不会挤掉先落地的，也不会两条都有效。
- 器材改归属班组时调整**不拦截**，点验流程另行处理；但该件“尚未结束”的有效占用会在同一事务内当场作废，记录作废时间、作废操作人和作废原因。
- 作废记录保留留痕（列表勾选“显示已作废记录”可查），但不计入班组当天有效占用条数，也不再挡住新班组挂同时段。

接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/occupancy | 挂课目占用（交叉/越权返回 400） |
| GET | /api/occupancy?trainingDate=&teamId=&includeCancelled= | 某日占用列表 |
| GET | /api/occupancy/equipment/{equipmentId}?trainingDate= | 某器材某日有效占用 |
| GET | /api/occupancy/team/{teamId}/active-count?trainingDate= | 班组当天有效占用条数 |
| GET | /api/occupancy/active-counts?trainingDate= | 全部班组当天有效占用条数 |

## 器材外借离场

班长可把**本班组名下已归属**的器材（如水带、空气呼吸器）拉出营区赴外场演练时登记离场，解决“人走了器材还挂在班组名下、对不上账”的问题。

业务规则：

- 出门必须写齐**预计归还时刻、同行人、离场事由**，缺一项不能出门（注解校验 + 服务内双重兜底）；预计归还时刻必须晚于当前时刻。
- 只能登记**自己班组名下、已经归属**的器材；未归属或不在本班组名下的一律拒绝。
- 同一件器材**在外（含超期）未还时不能再开第二条离场**；两个班长几乎同时给同一件器材点出门时，靠**器材行锁（SELECT … FOR UPDATE）串行化**，先落地者生效，后落地者返回 400，不会挤掉先落地的，也不会两条都算出门在外。
- 器材已挂**尚未结束的有效课目占用**时，本次外借**直接拒绝**；离场只读取占用账做前置判定，**不改占用、不作废时段**；归还/超期同样不触碰占用表——占用归占用，出门归出门，两边账互不回写。
- 到期未还自动进入**超期**（定时任务每分钟扫描置 `OVERDUE` 并留超期时刻；列表/统计另有读时口径，扫描未跑到时也如实呈现超期）；**超期件归还前不能再被外借**，归还闭环后同一件器材才能再开新离场。

接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/loan/checkout | 登记离场（缺项/越权/在途未还/超期未还/有未结束占用均返回 400） |
| PUT | /api/loan/{id}/return | 器材回场归还，离场单闭环 |
| GET | /api/loan?tab=&teamId=&equipmentId= | 离场列表，tab=OPEN（默认，在外含超期）/ OVERDUE / RETURNED / ALL |
| GET | /api/loan/summary | 对账统计：在外、超期、已归还件数 |
| POST | /api/loan/sweep-overdue | 手动触发一次超期扫描（正常由定时任务每分钟执行） |

## 常见问题

- 前端接口异常时，先确认 Docker 后端容器是否启动，并检查前端 Nginx 代理配置。
- 中文数据显示异常时，确认 MySQL 已使用 `utf8mb4` 字符集，JDBC URL 已包含 `useUnicode=true&characterEncoding=utf8mb4`。
- 端口被占用时，修改 `.env` 或 `docker-compose.yml` 中对应的宿主机端口。
