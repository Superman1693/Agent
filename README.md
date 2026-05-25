# AI 智能旅行助手

基于 Spring AI + 阿里云 DashScope 的 AI 智能体(Agent)旅行助手应用，支持智能对话、行程规划、RAG 检索增强等功能。

## 技术栈

| 类别 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5.13、Java 21 |
| **AI 框架** | Spring AI、阿里云 DashScope |
| **Agent 框架** | Spring AI Alibaba Agent Framework |
| **向量存储** | PGVector (PostgreSQL) |
| **业务数据库** | MySQL 8.0+ |
| **缓存** | Redis 6.0+ |
| **ORM** | MyBatis-Plus 3.5.14 |
| **文件存储** | 阿里云 OSS |
| **API 文档** | Knife4j (OpenAPI 3) |
| **认证** | JWT |
| **MCP 协议** | Spring AI MCP Client |

## 环境要求

- **JDK 21+**
- **Maven 3.8+**
- **MySQL 8.0+** (存储业务数据)
- **PostgreSQL 12+** + **pgvector 扩展** (存储向量数据)
- **Redis 6.0+**

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/Superman1693/Agent.git
cd Agent
```

### 2. 初始化数据库

#### MySQL 数据库

创建数据库并执行初始化脚本：

```sql
CREATE DATABASE IF NOT EXISTS ai_agent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_agent;
```

然后执行初始化脚本（按顺序）：

```bash
mysql -u root -p ai_agent < src/main/resources/db/init.sql
mysql -u root -p ai_agent < src/main/resources/db/recommendation.sql
mysql -u root -p ai_agent < src/main/resources/db/recommendation_test_data.sql
```

#### PostgreSQL 数据库 (向量存储)

安装 pgvector 扩展后创建数据库：

```sql
CREATE DATABASE "ai-agent";
\c ai-agent
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置环境变量

创建本地配置文件 `src/main/resources/application-local.yml`：

```yaml
spring:
  datasource:
    # MySQL 配置（业务数据）
    jdbc-url: jdbc:mysql://localhost:3306/ai_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: ${DB_PASSWORD:你的MySQL密码}
    driver-class-name: com.mysql.cj.jdbc.Driver

  # Redis 配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:你的Redis密码}
      database: 0
      timeout: 5000ms

  # PostgreSQL 配置（向量存储）
  ai:
    vectorstore:
      pgvector:
        enabled: true
        datasource:
          jdbc-url: jdbc:postgresql://${PG_HOST:localhost}:${PG_PORT:5432}/ai-agent
          username: ${PG_USERNAME:postgres}
          password: ${PG_PASSWORD:你的PostgreSQL密码}
          driver-class-name: org.postgresql.Driver
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:你的DashScope API Key}
      chat:
        options:
          model: qwen-plus
      embedding:
        options:
          model: text-embedding-v2

# 搜索 API
search-api:
  api-key: ${SEARCH_API_KEY:你的搜索API Key}

# 阿里云 OSS
aliyun:
  oss:
    endpoint: ${ALIYUN_OSS_ENDPOINT:oss-cn-hangzhou.aliyuncs.com}
    access-key-id: ${ALIYUN_OSS_KEY_ID:你的AccessKey ID}
    access-key-secret: ${ALIYUN_OSS_KEY_SECRET:你的AccessKey Secret}
    bucket-name: ${ALIYUN_OSS_BUCKET:你的Bucket名称}
    domain: ${ALIYUN_OSS_DOMAIN:https://your-bucket.oss-cn-hangzhou.aliyuncs.com}

# 高德地图 API
amap:
  api-key: ${AMAP_MAPS_API_KEY:你的高德地图API Key}
```

### 4. 启动 Redis

```bash
# Windows
redis-server

# Linux/Mac
redis-server --daemonize yes
```

### 5. 编译运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行应用（使用 local 配置）
java -jar target/ai-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

或者在 IDE 中直接运行主类：`AiAgentApplication`

### 6. 访问应用

- **API 服务**: http://localhost:8123/api
- **API 文档**: http://localhost:8123/api/doc.html

## 环境变量说明

| 变量名 | 必填 | 说明 | 示例 |
|--------|------|------|------|
| `DB_PASSWORD` | ✅ | MySQL 密码 | `your_password` |
| `REDIS_PASSWORD` | ✅ | Redis 密码 | `your_password` |
| `PG_HOST` | ✅ | PostgreSQL 主机 | `localhost` |
| `PG_PORT` | ❌ | PostgreSQL 端口 | `5432` |
| `PG_USERNAME` | ✅ | PostgreSQL 用户名 | `postgres` |
| `PG_PASSWORD` | ✅ | PostgreSQL 密码 | `your_password` |
| `DASHSCOPE_API_KEY` | ✅ | 阿里云 DashScope API Key | `sk-xxx` |
| `SEARCH_API_KEY` | ❌ | 搜索 API Key | - |
| `ALIYUN_OSS_KEY_ID` | ❌ | 阿里云 OSS AccessKey ID | `LTAI5txxx` |
| `ALIYUN_OSS_KEY_SECRET` | ❌ | 阿里云 OSS AccessKey Secret | `xxx` |
| `ALIYUN_OSS_BUCKET` | ❌ | OSS Bucket 名称 | `my-bucket` |
| `AMAP_MAPS_API_KEY` | ❌ | 高德地图 API Key | `xxx` |

## 项目结构

```
ai-agent/
├── src/main/java/com/ahead/aiagent/
│   ├── app/                  # AI 应用入口 (TravelApp)
│   ├── controller/           # REST API 控制器
│   │   ├── FavoriteController.java
│   │   ├── ItineraryController.java
│   │   ├── LocationController.java
│   │   └── OrderController.java
│   ├── service/              # 业务逻辑层
│   ├── mapper/               # MyBatis Mapper 接口
│   ├── model/                # 实体、DTO、VO
│   │   ├── entity/           # 数据库实体
│   │   ├── dto/              # 请求参数
│   │   └── vo/               # 响应对象
│   ├── rag/                  # RAG 检索增强
│   │   ├── TravelAppDocumentLoader.java
│   │   └── TravelAppVectorStoreConfig.java
│   ├── tools/                # AI 工具集
│   └── config/               # 配置类
├── src/main/resources/
│   ├── application.yml       # 主配置
│   ├── application-local.yml # 本地环境配置 (需创建)
│   ├── application-prod.yml  # 生产环境配置
│   ├── db/                   # 数据库脚本
│   │   ├── init.sql          # 初始化脚本
│   │   ├── recommendation.sql
│   │   └── recommendation_test_data.sql
│   └── document/             # RAG 知识库文档
└── image-search-mcp-server/  # 图片搜索 MCP 服务
```

## 常见问题

### 1. 启动报错 "Connection refused"

确保 MySQL、PostgreSQL、Redis 服务都已启动，并检查配置文件中的连接信息是否正确。

### 2. pgvector 扩展未安装

```bash
# Ubuntu/Debian
sudo apt-get install postgresql-15-pgvector

# MacOS
brew install pgvector

# 然后在 PostgreSQL 中执行
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. DashScope API Key 无效

前往 [阿里云 DashScope 控制台](https://dashscope.console.aliyun.com/) 获取 API Key。

### 4. 端口被占用

修改 `application.yml` 中的 `server.port` 配置：

```yaml
server:
  port: 8124  # 修改为其他端口
```

### 5. Maven 依赖下载慢

配置阿里云 Maven 镜像：

```xml
<!-- settings.xml -->
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/user/register` | POST | 用户注册 |
| `/api/user/login` | POST | 用户登录 |
| `/api/ai/chat` | POST | AI 对话 |
| `/api/ai/chat/stream` | GET | AI 流式对话 (SSE) |
| `/api/destination/list` | GET | 目的地列表 |
| `/api/itinerary/create` | POST | 创建行程 |
| `/api/favorite/add` | POST | 添加收藏 |
| `/api/order/create` | POST | 创建订单 |

## 许可证

本项目采用 MIT 许可证

## 联系方式

- 项目地址: https://github.com/Superman1693/Agent.git
- 问题反馈: [Issues](https://github.com/Superman1693/Agent/issues)
