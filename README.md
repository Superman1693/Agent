# AI 智能旅行助手

基于 Spring AI + 阿里云 DashScope 的 AI 智能体(Agent)旅行助手应用，支持智能对话、行程规划、RAG 检索增强等功能。

## 技术栈

| 类别 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5.13、Java 21 |
| **AI 框架** | Spring AI、阿里云 DashScope |
| **Agent 框架** | Spring AI Alibaba Agent Framework |
| **向量存储** | PGVector (PostgreSQL) |
| **数据库** | MySQL、PostgreSQL |
| **缓存** | Redis |
| **ORM** | MyBatis-Plus 3.5.14 |
| **文件存储** | 阿里云 OSS |
| **API 文档** | Knife4j (OpenAPI 3) |
| **认证** | JWT |
| **MCP 协议** | Spring AI MCP Client |

## 功能特性

### AI Agent 能力
- **ReAct Agent**: 推理-行动循环模式
- **ToolCall Agent**: 工具调用代理
- **流式对话**: SSE 实时流式输出
- **多轮对话**: 上下文记忆管理

### RAG 检索增强
- 文档自动分块与向量化
- PGVector 向量相似度检索
- 查询重写与扩展
- 检索增强生成

### MCP 集成
- 支持 MCP 协议连接外部服务
- 内置图片搜索 MCP Server

### 旅行功能
- 目的地管理与推荐
- AI 智能行程规划
- 收藏夹管理
- 订单管理

### 工具集
- 网页搜索与抓取
- 文件操作
- 终端命令执行
- 资源下载
- PDF 生成

### 用户体系
- 用户注册/登录
- JWT 认证授权
- API Key 管理

## 项目结构

```
ai-agent/
├── src/main/java/com/ahead/aiagent/
│   ├── app/                  # AI 应用入口
│   ├── controller/           # REST API 控制器
│   ├── service/              # 业务逻辑层
│   ├── mapper/               # MyBatis Mapper
│   ├── model/                # 实体、DTO、VO
│   ├── rag/                  # RAG 检索增强
│   ├── tools/                # AI 工具集
│   └── config/               # 配置类
├── src/main/resources/
│   ├── application.yml       # 主配置
│   ├── application-local.yml # 本地环境配置
│   └── application-prod.yml  # 生产环境配置
└── image-search-mcp-server/  # 图片搜索 MCP 服务
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- PostgreSQL 12+ (需安装 pgvector 扩展)
- Redis 6.0+

### 配置说明

1. 复制配置文件模板:
```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

2. 修改 `application-local.yml` 配置:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_agent
    username: your_username
    password: your_password
  
  data:
    redis:
      host: localhost
      port: 6379

  ai:
    dashscope:
      api-key: your_dashscope_api_key

oss:
  access-key-id: your_aliyun_access_key
  access-key-secret: your_aliyun_access_secret
```

### 运行项目

```bash
# 克隆项目
git clone https://github.com/Superman1693/Agent.git
cd Agent

# 编译打包
mvn clean package -DskipTests

# 运行应用
java -jar target/ai-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

应用启动后访问:
- API 服务: http://localhost:8123/api
- API 文档: http://localhost:8123/api/doc.html

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

## 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | `sk-xxx` |
| `OSS_ACCESS_KEY_ID` | 阿里云 OSS AccessKey ID | `LTAI5txxx` |
| `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS AccessKey Secret | `xxx` |
| `DB_PASSWORD` | 数据库密码 | `your_password` |
| `REDIS_PASSWORD` | Redis 密码 | `your_password` |

## 开发指南

### 添加新的 AI 工具

1. 在 `tools` 包下创建工具类
2. 实现工具方法并添加 `@Tool` 注解
3. 在 Agent 配置中注册工具

### 扩展 RAG 知识库

1. 将文档放入 `src/main/resources/docs/`
2. 运行文档加载器进行向量化
3. 重启应用使新知识生效

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- 项目地址: https://github.com/Superman1693/Agent.git
- 问题反馈: [Issues](https://github.com/Superman1693/Agent/issues)
