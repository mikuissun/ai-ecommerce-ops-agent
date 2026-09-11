# Stage 1：基础架构与电商业务数据

## 项目目标

本阶段提供一个可被后续 Agent Service/Tool 层复用的跨境电商业务基础系统，包含用户认证、商品、库存、订单、运营汇总和可重复初始化的 Demo 数据。

本阶段明确不包含 LLM、Agent、Tool Calling、Function Calling、Planning、Memory、MCP、Multi-Agent、Qdrant、Embedding 或 RAG。

## 技术栈与结构

- Backend：Java 17、Spring Boot 3、Maven、MyBatis-Plus、MySQL 8、Flyway、JWT、Bean Validation
- Frontend：Vue 3、TypeScript、Vite、Element Plus（仅基础壳）
- Infrastructure：Docker Compose + MySQL 8 命名 volume

```text
backend/   Spring Boot API、Service、Mapper、Entity、JWT、Flyway
frontend/  Vue 3 + TypeScript + Vite + Element Plus
deploy/    MySQL Docker Compose
docs/      阶段文档
scripts/   本地启动脚本
```

## 数据模型

| 表 | 关系与用途 |
|---|---|
| `users` | 登录用户；email 唯一 |
| `products` | 用户的多 marketplace 商品；`user_id + sku` 唯一 |
| `inventory` | 用户商品库存；`user_id + product_id` 唯一 |
| `orders` | 用户订单；`user_id + order_no` 唯一 |
| `order_items` | 订单明细，关联订单和商品 |

商品、库存和订单均直接带 `user_id`。订单明细通过 `order_id` 归属订单，商品通过 `product_id` 关联商品。

## JWT 与用户隔离

`POST /api/auth/register` 使用 BCrypt 保存密码；`POST /api/auth/login` 返回 Bearer JWT。JWT 的 subject 是服务端签发的用户 ID。`JwtAuthenticationFilter` 验证 Token 后写入 `CurrentUserContext`，业务 Controller 只从该上下文取当前用户，不信任前端传入的 userId。

所有业务查询都在 Service 层组合 `currentUserId + resource condition`，例如商品详情使用 `id + userId`，跨用户访问返回 404，不会泄露其他用户资源是否存在。

## Demo 数据

Flyway `V3__insert_demo_data.sql` 创建 1 个 Demo 用户、12 个商品、12 条库存、30 个订单和 30 条订单明细。数据覆盖 Amazon US、Amazon UK、Shopify、正常/低库存/缺货、高低价格与不同订单状态，并分布在最近约 30 天的固定演示日期内。

Demo 登录：`demo@example.com` / `password`。

## API

认证：

- `POST /api/auth/register`
- `POST /api/auth/login`

业务接口均需 `Authorization: Bearer <token>`：

- `GET /api/products?keyword=&marketplace=&status=`
- `GET /api/products/{id}`
- `GET /api/inventory?lowStock=true`
- `GET /api/inventory/{productId}`
- `GET /api/orders?status=&marketplace=&startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
- `GET /api/orders/{id}`
- `GET /api/dashboard/summary`

Dashboard 当前返回：`productCount`、`lowStockCount`、`orderCount`、`recent7DaysOrderCount`、`recent7DaysRevenue`、`refundCount`、`completedOrderCount`。

## Flyway

- `V1__create_users.sql`：用户表
- `V2__create_ecommerce_tables.sql`：商品、库存、订单、订单明细和索引/外键
- `V3__insert_demo_data.sql`：Demo 用户和业务数据

应用首次连接数据库时自动执行迁移，不依赖手工 Navicat SQL。

## 启动与测试

复制 `.env.example` 为 `.env`，执行：

```powershell
./scripts/dev-start.ps1
```

或手动启动 MySQL：

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml up -d mysql
cd backend
mvn spring-boot:run
```

后端测试使用 H2 MySQL 兼容模式和相同 Flyway migration，不依赖真实外部服务：

```powershell
cd backend
mvn test
```

前端仅验证安装与构建：

```powershell
cd frontend
npm install
npm run build
```

## 当前边界

Stage 1 没有写入 Agent、LLM 或 Tool Calling 逻辑。后续 Agent 应优先直接复用 `ProductService`、`InventoryService`、`OrderService` 和 `DashboardService`，而不是通过 HTTP 调用自身 API。
