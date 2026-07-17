# AI 接入服务 — 实现方案

## 概述
类似 OneAPI 的统一 AI 接入服务，支持多服务商、多模型管理，用户订阅与额度控制，自动 fallback 切换。

## 数据库表设计（7 张表）

### 1. t_ai_provider — AI 服务商
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| name | VARCHAR(100) | 服务商名称 |
| code | VARCHAR(50) UNIQUE | 编码，如 openai, vllm, claude, qwen, deepseek |
| base_url | VARCHAR(500) | API 基础地址 |
| api_type | VARCHAR(30) | openai / claude / qwen / deepseek（统一转 openai 格式） |
| auth_type | VARCHAR(20) | api_key / bearer / none |
| api_key | VARCHAR(500) | API 密钥（加密存储） |
| status | TINYINT | 0=禁用 1=启用 |
| description | TEXT | 备注 |
| create_time | DATETIME | |
| update_time | DATETIME | |

### 2. t_ai_model — AI 模型
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| provider_id | BIGINT FK | 所属服务商 |
| model_name | VARCHAR(100) | 模型标识，如 gpt-4o, deepseek-r1 |
| display_name | VARCHAR(100) | 显示名称 |
| input_price | DECIMAL(10,6) | 输入价格（每1K token） |
| output_price | DECIMAL(10,6) | 输出价格（每1K token） |
| max_tokens | INT | 最大 token |
| supports_stream | TINYINT | 是否支持流式，默认 1 |
| health_status | VARCHAR(20) | online/degraded/offline |
| last_health_check | DATETIME | |
| cooldown_until | DATETIME | 冷却截止 |
| consecutive_failures | INT | 连续失败次数 |
| avg_latency_ms | BIGINT | 平均延迟 |
| status | TINYINT | 0=禁用 1=启用 |
| create_time | DATETIME | |
| update_time | DATETIME | |

### 3. t_ai_subscription — 用户订阅
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| provider_id | BIGINT FK | |
| model_id | BIGINT FK | |
| priority | INT DEFAULT 0 | 优先级（越小越优先） |
| weight | INT DEFAULT 1 | 权重 |
| tags | VARCHAR(200) | 标签 fast,cheap,code,reasoning |
| fallback_enabled | TINYINT DEFAULT 1 | 是否参与 fallback |
| max_tokens_per_request | INT | |
| status | TINYINT | 0=禁用 1=启用 |
| expire_time | DATETIME | null=永久 |
| create_time | DATETIME | |
| update_time | DATETIME | |

### 4. t_ai_quota — 额度管理
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| subscription_id | BIGINT FK | null=全局额度 |
| token_limit | BIGINT | 总额度 |
| token_used | BIGINT DEFAULT 0 | |
| time_window_seconds | INT | 时间窗口秒，如 18000=5h |
| window_start | DATETIME | |
| status | TINYINT | 0=禁用 1=启用 |
| create_time | DATETIME | |

### 5. t_ai_usage_log — 调用日志
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| provider_id | BIGINT FK | |
| model_id | BIGINT FK | |
| request_body | TEXT | 截断存储 |
| response_body | TEXT | 截断存储 |
| prompt_tokens | INT | |
| completion_tokens | INT | |
| total_tokens | INT | |
| cost | DECIMAL(10,6) | |
| latency_ms | BIGINT | |
| fallback_from | BIGINT | 从哪个模型 fallback |
| status_code | INT | |
| error_msg | VARCHAR(500) | |
| create_time | DATETIME | |

### 6. t_ai_model_health — 健康检查记录
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| model_id | BIGINT FK | |
| check_time | DATETIME | |
| status | VARCHAR(20) | success/timeout/error |
| latency_ms | BIGINT | |
| error_msg | VARCHAR(500) | |

### 7. t_ai_api_key — 用户 API Key
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| api_key | VARCHAR(64) UNIQUE | sk-xxx 格式 |
| key_name | VARCHAR(100) | |
| last_used | DATETIME | |
| status | TINYINT | 0=禁用 1=启用 |
| create_time | DATETIME | |

## API 接口

### 对外（OpenAI 兼容）
- POST /api/ai/v1/chat/completions — Chat（支持流式 SSE）
- POST /api/ai/v1/embeddings（可选）
- GET /api/ai/v1/models

### 管理后台
- CRUD /api/admin/ai/providers
- CRUD /api/admin/ai/providers/{id}/models
- GET /api/admin/ai/models/health
- POST /api/admin/ai/models/{id}/health-check
- CRUD /api/admin/ai/subscriptions
- CRUD /api/admin/ai/quotas
- CRUD /api/admin/ai/api-keys
- GET /api/admin/ai/statistics
- GET /api/admin/ai/usage-logs
- GET /api/admin/ai/fallback-logs

## Auto 模式 + Fallback

- model="auto"：按策略排序 + 过滤不可用 → 选第一个
- 指定模型不可用 → 按优先级 fallback（最多3次）
- Fallback 策略：priority(默认) / random / cost_first / performance_first
- 健康检查：每60s探测，连续失败5次标记 offline，冷却5分钟

## 默认使用 vLLM（OpenAI 兼容格式）
本地部署填 vLLM 地址：http://localhost:8000/v1

## 文件清单

### 后端
- Entity: AiProvider, AiModel, AiSubscription, AiQuota, AiUsageLog, AiModelHealth, AiApiKey
- Repository: 7个
- DTO: ChatCompletionRequest/Response/Chunk, 管理用DTO
- Service: AiProxyService, AiModelSelector, AiQuotaService, AiHealthCheckService, AiUsageService
- Controller: AiProxyController, AiStreamController, AiAdminController

### 前端
- AiProviders.vue, AiModels.vue, AiSubscriptions.vue, AiQuotas.vue
- AiApiKeys.vue, AiStatistics.vue, AiUsageLogs.vue
- i18n: zh-CN, en-US, ja-JP
