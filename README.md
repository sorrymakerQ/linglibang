# 🏘️ 邻里帮 - 社区互助平台

> 邻里互助，温暖社区 —— 一个基于地理位置的社区互助平台

## 📖 项目简介

「邻里帮」是一个社区居民互助平台。用户可以在平台上发布求助（修水管、取快递、借工具等），附近的邻居看到后接单帮忙。

### 核心功能

- 🙋 **接单帮忙** — 邻居接单，线下互助
- ⏱️ **超时自动取消** — RabbitMQ 延迟队列，30 分钟未处理自动取消
- 🔔 **实时通知** — WebSocket 推送接单、评价等通知
- 💬 **私聊沟通** — 订单双方 WebSocket 实时私信
- 🛡️ **管理后台** — 用户管理、内容审核、数据统计

## 🛠️ 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7 + Spring MVC |
| 数据层 | MyBatis + MySQL 8.0 |
| 缓存 | Redis（GEO、ZSet、String，Redisson 分布式锁） |
| 消息队列 | RabbitMQ（死信延迟队列、异步通知） |
| 实时推送 | WebSocket + STOMP |
| 认证 | Sa-Token（登录认证 + `StpInterface` 动态权限） |
| 对象存储 | 阿里云 OSS |
| 前端框架 | Vue 3 + TypeScript + Pinia + Vite |
| UI 组件 | Element Plus（按需引入） |
| 工具库 | Hutool、Lombok、Axios |

## 📁 项目结构

```
linlibang/
├── backend/                          # 后端 Spring Boot 项目
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/linlibang/
│       │   ├── LinLiBangApplication.java   # 启动类
│       │   ├── config/                     # 配置类
│       │   │   ├── RedissonConfig.java     # Redisson 分布式锁
│       │   │   ├── SaTokenConfig.java      # Sa-Token 路由鉴权
│       │   │   ├── StpInterfaceImpl.java   # Sa-Token 角色/权限
│       │   │   ├── WebSocketConfig.java    # WebSocket STOMP
│       │   │   ├── RabbitMQConfig.java     # 延迟队列 + 死信队列
│       │   │   └── OssConfig.java          # 阿里云 OSS
│       │   ├── entity/                     # 数据库实体
│       │   ├── dto/                        # 数据传输对象
│       │   ├── mapper/                     # MyBatis Mapper
│       │   ├── service/                    # 业务接口 + impl 实现
│       │   ├── controller/                 # REST 控制器
│       │   ├── handler/                    # 全局异常/JSON 序列化
│       │   ├── listener/                   # MQ 消息监听
│       │   └── utils/                      # RedisUtils / OssUtils
│       └── resources/
│           ├── application.yml             # 配置文件（敏感值走 env）
│           └── db/schema.sql               # 建表脚本
│
├── frontend/                         # 前端 Vue 3 项目
│   ├── package.json  vite.config.ts  tsconfig.json  index.html
│   ├── .env.example                  # 环境变量模板（复制为 .env 使用）
│   └── src/
│       ├── main.ts  App.vue  router/index.ts
│       ├── stores/user.ts                  # Pinia 用户状态
│       ├── types/index.ts                  # TypeScript 类型定义
│       ├── api/                            # Axios 接口封装（user/help/order/chat/upload）
│       ├── utils/                          # request / toast / confirm / permission
│       ├── components/                     # NavBar / ChatBox 等
│       ├── views/                          # Home / Login / HelpDetail / MyOrders / Admin 等 12 个页面
│       └── assets/style.css
│
├── docs/
│   └── seed_data.sql                       # 演示种子数据
├── deploy.sh                               # Ubuntu 22.04 一键部署脚本
└── README.md
```

## 🚀 快速启动

### 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+
- Node.js 16+

### 1. 创建数据库

```bash
mysql -uroot -p < backend/src/main/resources/db/schema.sql
# 可选：导入演示数据
mysql -uroot -p linlibang < docs/seed_data.sql
```

### 2. 配置环境变量（必须）

后端所有敏感值都走环境变量，未配置会 fail-fast。示例（macOS/Linux）：

```bash
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your_mysql_password
export SA_TOKEN_JWT_SECRET_KEY=$(openssl rand -hex 32)   # 32 位随机串
export ALIYUN_OSS_ACCESS_KEY_ID=your_oss_ak
export ALIYUN_OSS_ACCESS_KEY_SECRET=your_oss_sk
export ALIYUN_OSS_BUCKET=your_bucket_name
export ALIYUN_OSS_BASE_URL=https://your_bucket.oss-cn-beijing.aliyuncs.com
# RabbitMQ 若使用默认账号可留空
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
```

Windows PowerShell 用 `$env:MYSQL_PASSWORD="..."`，或在 IDE 的启动配置里加环境变量。

**环境变量清单**：

| 变量 | 是否必填 | 说明 |
|------|:---:|------|
| `MYSQL_USERNAME` | 否（默认 `root`） | MySQL 用户名 |
| `MYSQL_PASSWORD` | ✅ | MySQL 密码 |
| `SA_TOKEN_JWT_SECRET_KEY` | ✅ | Sa-Token JWT 签名密钥，建议 32 位以上随机串 |
| `ALIYUN_OSS_ACCESS_KEY_ID` | ✅ | 阿里云 OSS AccessKey ID |
| `ALIYUN_OSS_ACCESS_KEY_SECRET` | ✅ | 阿里云 OSS AccessKey Secret |
| `ALIYUN_OSS_BUCKET` | ✅ | OSS Bucket 名称 |
| `ALIYUN_OSS_BASE_URL` | ✅ | OSS 访问域名 |
| `RABBITMQ_USERNAME` | 否 | RabbitMQ 账号 |
| `RABBITMQ_PASSWORD` | 否 | RabbitMQ 密码 |

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动后访问 `http://localhost:8080/api`。

### 4. 配置并启动前端

```bash
cd frontend
cp .env.example .env
# 编辑 .env，填入必要配置
npm install
npm run dev
```

前端启动后访问 `http://localhost:3000`。

> **端口冲突提示**：macOS 系统占用 5000/7000 端口，若你的 3000 端口被 AirPlay/其他进程占用，可在 `vite.config.ts` 里改 `server.port`。

### 5. 生产部署（可选）

`deploy.sh` 是一份 Ubuntu 22.04 的一键部署脚本，覆盖 Java 8、MySQL 8、Redis、RabbitMQ、Nginx、systemd 单元。生产上跑前，先阅读脚本注释并把 systemd 单元里的环境变量替换为你自己的值。

## 🔌 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/login | 用户登录 |
| POST | /api/user/register | 用户注册 |
| GET | /api/user/me | 获取当前用户信息 |
| PUT | /api/user/location | 更新位置 |
| POST | /api/help/publish | 发布求助 |
| GET | /api/help/nearby | 附近求助 |
| GET | /api/help/search | 搜索求助 |
| GET | /api/help/{id} | 求助详情 |
| POST | /api/order/accept/{helpId} | 接单 |
| PUT | /api/order/{id}/finish | 完成订单 |
| PUT | /api/order/{id}/review | 评价订单 |
| GET | /api/chat/messages | 拉取聊天历史 |
| POST | /api/upload | 图片上传（OSS） |
| GET | /api/category/list | 分类列表 |
| GET | /api/admin/stats | 管理统计 |

## 📝 技术亮点

**缓存与并发**

1. **Redisson 双重检查锁 + 逻辑过期** — 防缓存击穿，分页缓存 TTL `5min + 随机 0~60s` 打散防雪崩
2. **`TransactionSynchronization.afterCommit`** — 事务回滚时缓存不残留脏数据，解决「先删缓存 → DB 回滚」经典问题
3. **启动异步缓存预热 + 定时双向对账** — 长期运行下 Redis 与 MySQL 数据零漂移

**消息与实时通信**

4. **RabbitMQ 死信延迟队列** — 订单 30 分钟未接单自动取消
5. **手动 ACK + Redis 幂等键** — 消费端防消息重投导致的业务重复执行
6. **WebSocket + STOMP** — CONNECT 帧携带 SaToken 鉴权，按用户 uid 定向推送

**鉴权与工程化**

7. **Sa-Token 路由鉴权 + `StpInterface`** — 动态角色/权限，接口层统一 `Result` + `@Valid` 参数校验
8. **前端 Vue 3 + TypeScript + Element Plus** — 按需引入 + 全局错误拦截 + 请求/响应类型化

## ⚠️ 注意事项

- **敏感信息全部走环境变量**：`application.yml` 中所有敏感字段（`MYSQL_PASSWORD`、`SA_TOKEN_JWT_SECRET_KEY`、`ALIYUN_OSS_*`）都以 `${ENV:}` 形式引用，未设置将无法启动
- **前端 `.env` 已在 `.gitignore` 中**：请从 `.env.example` 复制并填入本地值
- **对象存储**：需要在 [阿里云 OSS 控制台](https://oss.console.aliyun.com/) 创建 Bucket，并使用 RAM 子账号密钥（不要使用主账号 AK/SK）

## 
