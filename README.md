# AI 跨境电商运营 Agent

`ai-ecommerce-ops-agent` 是一个独立的跨境电商运营基础系统，为运营 Agent 提供可隔离、多用户的电商数据、业务 Service 和只读 Tool 能力。

当前范围：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Flyway + JWT，以及 Vue 3 + TypeScript + Vite + Element Plus 基础前端。

当前已完成 Stage 1 基础业务系统、Stage 2 只读 Tool System、Stage 3 Qwen Tool Calling + Agent Loop、Stage 4 多工具运营分析和 Stage 5 多轮 Conversation Memory。启动 Agent 前需设置进程环境变量 `DASHSCOPE_API_KEY`；模型默认 `qwen-plus`，最多调用 6 次，会话历史默认最多加载 20 条。详见 [docs/stage1.md](docs/stage1.md)、[docs/stage2.md](docs/stage2.md)、[docs/stage3.md](docs/stage3.md)、[docs/stage4.md](docs/stage4.md) 和 [docs/stage5.md](docs/stage5.md)。

## 快速启动

1. 复制 `.env.example` 为 `.env` 并按需修改。
2. 在项目根目录执行 `./scripts/dev-start.ps1`，或先执行 `docker compose --env-file .env -f deploy/docker-compose.yml up -d mysql`。
3. 后端：`cd backend; mvn spring-boot:run`。
4. 前端：`cd frontend; npm install; npm run dev`。

默认 Demo 用户：`demo@example.com` / `password`。

## 验证

```text
git diff --check
cd backend && mvn test
cd frontend && npm install && npm run build
```

本项目不在 Stage 1 自动执行 git commit 或 git push。
