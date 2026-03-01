# AISQL

一个基于 Spring Boot 的自然语言转 SQL 查询服务。  
输入自然语言，系统会生成 SQL、执行查询，并返回结果与 SQL 评分分析。

## 功能特性

- 自然语言转 MySQL 查询语句
- SQL 安全校验（拦截高风险语句）
- 查询审计日志（生成 SQL、执行耗时、结果行数）
- 数据库 Schema 缓存，减少元数据读取开销
- 前端控制台页面（`/`）可直接交互查询
- SQL 评分分析：返回 `score / level / risks / suggestions`

## 技术栈

- Java 17+
- Spring Boot 3.2.0
- Spring AI（DeepSeek）
- Spring JDBC
- MySQL
- Redis

## 项目结构

```text
src/main/java/com/wcw/aisql
├─ query/controller   # HTTP 接口
├─ query/service      # 业务逻辑
├─ query/security     # SQL 安全校验
├─ query/config       # 配置类
├─ query/model        # 返回模型、DTO
└─ query/common       # 通用响应与错误码

src/main/resources
├─ application.yml
└─ static/index.html  # 前端控制台
```

## 快速启动

1. 准备环境
- JDK 17+
- MySQL 8.x
- Redis（可按当前配置启用）

2. 配置数据库与 AI 密钥  
编辑 `src/main/resources/application.yml`：
- `spring.datasource.*`
- `spring.ai.deepseek.api-key`

3. 启动项目

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS/Linux
./mvnw spring-boot:run
```

4. 访问页面

- 控制台：`http://localhost:8080`
- 查询接口：`GET http://localhost:8080/api/query/natural?q=查询所有用户`

## 构建与测试

```bash
# 运行测试
./mvnw test

# 打包
./mvnw clean package

# 跳过测试快速打包
./mvnw clean package -DskipTests
```

Windows 下将 `./mvnw` 替换为 `mvnw.cmd`。

## API 说明

### `GET /api/query/natural`

请求参数：

- `q`：自然语言查询文本（必填）

响应示例：

```json
{
  "code": 0,
  "data": {
    "success": true,
    "message": "OK",
    "sql": "select id,name from user limit 20",
    "parameters": [],
    "sqlAnalysis": {
      "score": 90,
      "level": "A",
      "risks": [
        "缺少 WHERE 条件，存在全表扫描风险。"
      ],
      "suggestions": [
        "补充过滤条件并优先使用索引字段。"
      ]
    },
    "data": [
      {
        "id": 1,
        "name": "Tom"
      }
    ]
  },
  "message": "ok"
}
```

## 配置说明

`application.yml` 关键项：

- `spring.datasource.*`：MySQL 连接配置
- `spring.ai.deepseek.*`：DeepSeek 模型调用配置
- `ai.query.slow-query-threshold-ms`：慢查询阈值（毫秒）
- `ai.query.schema-cache-seconds`：Schema 缓存秒数
- `server.port`：服务端口（默认 `8080`）

## 安全注意事项

- 不要在仓库中提交真实数据库密码和 AI Key
- 建议使用环境变量或 `application-local.yml` 管理本地敏感配置
- 涉及 SQL 生成逻辑变更时，需同步回归安全校验规则
