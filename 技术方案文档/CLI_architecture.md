# CLI服务技术架构文档

## 1. 项目概述

### 1.1 背景

本项目为工单系统（Work Order System）提供CLI服务接口，用于Agent通过命令行方式查询工单系统数据。CLI服务基于Java Spring Boot框架开发，作为工单系统后端（Spring Boot）的代理服务，提供统一的数据查询入口。

**核心变更**：采用"共享Service层jar包 + Spring Cloud OpenFeign RPC"方案，CLI服务通过OpenFeign声明式调用后端服务，实现类型安全的RPC调用体验。鉴权通过RPC调用后端的验证接口完成。

### 1.2 目标

- 为Agent提供标准化的数据查询接口，支持通过dataCode标识不同的数据类型
- 实现三步调用流程：鉴权 → TraceID生成 → 数据查询
- 通过OpenFeign实现RPC式调用，类型安全，自动补全
- 统一接口设计，简化Agent调用复杂度

### 1.3 架构定位

```
┌─────────────────────────────────────────────────────────────────┐
│                     Agent CLI 脚本                              │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP请求
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   CLI服务（Spring Boot）                          │
│  ┌──────────────┐  ┌──────────────────────────┐                │
│  │ 鉴权模块      │   │ 数据查询代理模块           │                │
│  │ (RPC验证)    │  │ (OpenFeign调用)          │                │
│  └──────────────┘   └──────────────────────────┘                │
│                        │ 依赖workorder-api模块                   │
│                        │ OpenFeign声明式调用                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP请求（OpenFeign自动处理）
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              工单系统后端（Spring Boot）                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ JWT过滤器    │  │ TraceID拦截器│  │ WorkOrderServiceImpl │   │
│  │              │  │              │  │ (实现WorkOrderService)│   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 1.4 模块依赖关系

```
workorder-api (共享模块)
├── Service接口 (WorkOrderService, DashboardService, FlowService)
├── Param类 (WorkOrderPageParam, WorkOrderDetailParam, ...)
├── VO类 (WorkOrderPageVO, WorkOrderDetailVO, ...)
└── 公共枚举和工具类

backend (工单系统后端)
└── 依赖 workorder-api → 实现Service接口

cli-service (CLI服务)
├── 依赖 workorder-api → 通过OpenFeign RPC调用后端实现
└── OpenFeign客户端 → 声明式调用后端Controller接口
```

---

## 2. RPC框架选型

### 2.1 选择Spring Cloud OpenFeign

| 维度 | Spring Cloud OpenFeign | RestTemplate | 纯RPC框架（Dubbo/gRPC） |
|------|----------------------|-------------|----------------------|
| 调用体验 | 声明式，像调用本地方法 | 手动构建请求 | 声明式，高性能 |
| 类型安全 | 是，编译时检查 | 否，运行时检查 | 是，编译时检查 |
| 集成复杂度 | 低，与Spring Boot无缝集成 | 低 | 高，需注册中心 |
| 改造成本 | 低，后端无需改动 | 低 | 高，后端需改造 |
| 依赖管理 | 标准化Maven依赖 | 内置 | 需额外依赖和配置 |

### 2.2 OpenFeign依赖说明

**Maven依赖**：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
    <version>4.1.0</version>
</dependency>
```

**Spring Boot版本要求**：
- Spring Boot 3.2+（与OpenFeign 4.x兼容）
- 当前项目使用Spring Boot 3.3+，完全兼容

---

## 3. 调用流程设计

### 3.1 三步调用流程

#### 第一步：鉴权（RPC鉴权）

- **方式**：CLI服务通过OpenFeign调用后端的鉴权验证接口
- **请求格式**：RPC调用 `AuthApi.validateToken(token)`
- **Token来源**：工单系统登录接口返回的JWT Token
- **验证方式**：后端 `JwtAuthenticationFilter` 的验证逻辑

**RPC鉴权接口设计**（在workorder-api中定义）：

```java
public interface AuthService {
    ValidateTokenResult validateToken(String token);
}

public class ValidateTokenResult {
    private boolean valid;
    private String userId;
    private String role;
    private String message;
}
```

#### 第二步：生成TraceID

- **方式**：CLI服务生成随机UUID作为TraceID
- **请求头格式**：`X-Trace-ID: <uuid>`
- **用途**：用于日志追踪，便于问题排查

#### 第三步：数据查询（RPC调用）

- **方式**：CLI服务通过OpenFeign调用后端的业务接口
- **请求路径**：`POST /api/query`（CLI服务统一入口）
- **请求体**：`{"dataCode": "...", "params": {...}}`
- **底层调用**：通过OpenFeign调用后端Controller接口

### 3.2 完整调用示例

```bash
# 第一步：获取Token（后续由Agent CLI脚本实现）
# 从浏览器Cookie中提取token

# 第二步：生成TraceID
trace_id=$(python -c "import uuid; print(uuid.uuid4())")

# 第三步：查询数据
curl -X POST http://localhost:5000/api/query \
  -H "Authorization: <token>" \
  -H "X-Trace-ID: $trace_id" \
  -H "Content-Type: application/json" \
  -d '{"dataCode": "work_order_page", "params": {"pageNum": 1, "pageSize": 10}}'
```

---

## 5. CLI服务接口设计

### 5.1 接口列表

| 接口路径 | HTTP方法 | 功能描述 |
|----------|----------|----------|
| /api/dataCodes | GET | 查询所有可查询的数据dataCode列表 |
| /api/schema/{dataCode} | GET | 查询某个dataCode的Schema（入参出参说明） |
| /api/query | POST | 统一查询接口（传入dataCode查询数据） |

### 5.2 /api/dataCodes - 获取所有dataCode

**请求**：
```
GET /api/dataCodes
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "dataCode": "work_order_page",
      "name": "工单分页查询",
      "description": "分页查询工单列表",
      "permission": "all"
    },
    {
      "dataCode": "work_order_detail",
      "name": "工单详情",
      "description": "查询工单详细信息",
      "permission": "all"
    }
  ]
}
```

### 5.3 /api/schema/{dataCode} - 获取Schema

**请求**：
```
GET /api/schema/work_order_page
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "dataCode": "work_order_page",
    "name": "工单分页查询",
    "description": "分页查询工单列表",
    "inputSchema": {
      "pageNum": {"type": "Integer", "required": true, "description": "当前页数"},
      "pageSize": {"type": "Integer", "required": true, "description": "每页大小"},
      "type": {"type": "Integer", "required": false, "description": "工单类型（0需求，1故障）"}
    },
    "outputSchema": {
      "records": {"type": "List", "description": "工单列表"},
      "total": {"type": "Long", "description": "总数量"}
    }
  }
}
```

### 5.4 /api/query - 统一查询接口

**请求**：
```
POST /api/query
Content-Type: application/json
Authorization: <token>
X-Trace-ID: <trace_id>

{
  "dataCode": "work_order_page",
  "params": {
    "pageNum": 1,
    "pageSize": 10,
    "type": 0
  }
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100
  },
  "traceId": "<trace_id>"
}
```

### 5.5 响应归一化设计

后端接口返回格式不统一，CLI服务需要做响应归一化处理：

| 后端返回类型 | 处理方式 | 归一化后结构 |
|-------------|----------|-------------|
| Result (code/msg/data) | 直接提取data字段 | `{"code": 0, "message": "success", "data": <result.data>}` |
| IPage (records/total/pages/size) | 保留原始结构 | `{"code": 0, "message": "success", "data": {"records": [...], "total": N}}` |
| 直接返回VO | 包装为data字段 | `{"code": 0, "message": "success", "data": <vo>}` |
| List\<VO\> | 包装为data字段 | `{"code": 0, "message": "success", "data": [<vo>, ...]}` |

**归一化规则**：
1. 后端返回 `Result` 类型时，检查 `code` 字段：
   - `code == 1`：成功，提取 `data` 字段
   - `code != 1`：失败，返回错误信息
2. 后端返回 `IPage` 类型时，直接作为 `data` 返回
3. 后端返回单个VO或List时，直接作为 `data` 返回
4. 所有错误统一转换为：`{"code": <error_code>, "message": "<error_msg>", "data": null, "traceId": "<trace_id>"}`

---

## 6. OpenFeign RPC客户端设计

### 6.1 客户端接口定义

在cli-service中定义OpenFeign客户端接口：

```java
@FeignClient(name = "workorder-backend", url = "${workorder.backend.url}")
public interface WorkOrderFeignClient {

    @PostMapping("/workOrder/page")
    ResponseEntity<IPage<WorkOrderPageVO>> pageWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody WorkOrderPageParam param);

    @PostMapping("/workOrder/detail")
    ResponseEntity<WorkOrderDetailVO> detail(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody WorkOrderDetailParam param);

    @GetMapping("/workOrder/search")
    ResponseEntity<SearchResult<WorkOrder>> searchWorkOrders(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);
}
```

```java
@FeignClient(name = "workorder-backend", url = "${workorder.backend.url}")
public interface DashboardFeignClient {

    @PostMapping("/dashboard/data")
    ResponseEntity<DashboardDataVO> getData(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId);

    @PostMapping("/dashboard/handleQuantity")
    ResponseEntity<List<WeekHandleVO>> getWeekHandleQuantity(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId);

    @PostMapping("/dashboard/pageMessages")
    ResponseEntity<IPage<MessageVO>> pageMessages(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody MessageParam param);
}
```

```java
@FeignClient(name = "workorder-backend", url = "${workorder.backend.url}")
public interface FlowFeignClient {

    @PostMapping("/flow/getById")
    ResponseEntity<FlowVO> getById(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody FlowIdParam param);

    @PostMapping("/flow/page")
    ResponseEntity<IPage<FlowVO>> page(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody FlowPageParam param);
}
```

```java
@FeignClient(name = "workorder-backend", url = "${workorder.backend.url}")
public interface AuthFeignClient {

    @PostMapping("/auth/validate")
    ResponseEntity<ValidateTokenResult> validateToken(
            @RequestHeader("Authorization") String token);
}
```

### 6.2 RPC鉴权流程

```
Agent请求 → CLI服务AuthInterceptor → AuthService → AuthFeignClient → 后端/auth/validate
                                                                       │
                                                                       ▼
                                                              ValidateTokenResult
                                                                       │
                                                                       ▼
                                                              返回{valid, userId, role, message}
```

### 6.3 OpenFeign配置

**application.yaml配置**：
```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: BASIC
  httpclient:
    enabled: true
```

**FeignConfig配置类**：
```java
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Content-Type", "application/json");
        };
    }
}
```

---

## 7. workorder-api模块设计

### 7.1 模块定位

`workorder-api` 是一个纯Java库模块，不包含任何Spring依赖，只包含接口定义和数据传输对象，供backend和cli-service共同依赖。

### 7.2 目录结构

```
workorder-api/
├── pom.xml                                    # Maven依赖管理（仅lombok）
└── src/
    └── main/
        └── java/
            └── com/example/workorder/api/
                ├── service/                   # Service接口定义
                │   ├── WorkOrderService.java  # 工单服务接口
                │   ├── DashboardService.java  # 数据看板服务接口
                │   ├── FlowService.java       # 流程服务接口
                │   └── AuthService.java       # 鉴权服务接口
                ├── param/                     # 请求参数类
                │   ├── WorkOrder/
                │   │   ├── WorkOrderPageParam.java
                │   │   └── WorkOrderDetailParam.java
                │   └── Flow/
                │       ├── FlowIdParam.java
                │       └── FlowPageParam.java
                ├── vo/                        # 返回值对象
                │   ├── WorkOrder/
                │   │   ├── WorkOrderPageVO.java
                │   │   └── WorkOrderDetailVO.java
                │   └── Flow/
                │       ├── FlowVO.java
                │       └── FlowNodeVO.java
                ├── dto/                        # 数据传输对象
                │   └── ValidateTokenResult.java
                └── enums/                     # 枚举类
                    ├── WorkOrderStatusEnum.java
                    ├── WorkOrderTypeEnum.java
                    └── WorkOrderPriorityLevelEnum.java
```

### 7.3 Service接口定义

#### WorkOrderService

```java
public interface WorkOrderService {
    IPage<WorkOrderPageVO> pageWorkOrder(WorkOrderPageParam param);
    WorkOrderDetailVO detail(WorkOrderDetailParam param);
    SearchResult<WorkOrder> searchWorkOrders(String keyword, int pageNum, int pageSize) throws IOException;
}
```

#### DashboardService

```java
public interface DashboardService {
    DashboardDataVO getData();
    List<WeekHandleVO> getWeekHandleQuantity();
    IPage<MessageVO> pageMessages(MessageParam param);
}
```

#### FlowService

```java
public interface FlowService {
    FlowVO getById(FlowIdParam param);
    IPage<FlowVO> page(FlowPageParam param);
}
```

#### AuthService

```java
public interface AuthService {
    ValidateTokenResult validateToken(String token);
}
```

#### ValidateTokenResult

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResult {
    private boolean valid;
    private String userId;
    private String role;
    private String message;
}
```

### 7.4 依赖关系

```
workorder-api (独立模块，无Spring依赖)
    │
    ├── backend (依赖workorder-api，实现Service接口)
    │       │
    │       ├── WorkOrderServiceImpl implements WorkOrderService
    │       └── AuthServiceImpl implements AuthService
    │
    └── cli-service (依赖workorder-api，通过OpenFeign RPC调用)
            │
            ├── WorkOrderFeignClient (声明式调用后端)
            ├── DashboardFeignClient (声明式调用后端)
            ├── FlowFeignClient (声明式调用后端)
            └── AuthFeignClient (声明式调用后端鉴权)
```

---

## 8. 后端TraceID改造方案

### 8.1 当前状态

后端代码目前没有TraceID相关逻辑，需要添加以下改造：

### 8.2 改造内容

#### 8.2.1 添加TraceID拦截器

创建 `TraceIdInterceptor.java`：

```java
@Component
public class TraceIdInterceptor implements HandlerInterceptor {
    
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String TRACE_ID_KEY = "traceId";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(TRACE_ID_KEY);
    }
}
```

#### 8.2.2 注册拦截器

在 `WebConfig.java` 中注册TraceID拦截器：

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private TraceIdInterceptor traceIdInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor).addPathPatterns("/**");
    }
}
```

#### 8.2.3 日志格式配置

修改 `application.yaml` 添加TraceID到日志格式：

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] - %msg%n"
```

#### 8.2.4 新增鉴权验证接口

在后端添加 `AuthController`：

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    @Autowired
    private TokenUtil tokenUtil;
    
    @PostMapping("/validate")
    public ValidateTokenResult validateToken(@RequestHeader("Authorization") String token) {
        ValidateTokenResult result = new ValidateTokenResult();
        try {
            if (tokenUtil.verifyToken(token)) {
                Claims claims = tokenUtil.parseToken(token);
                result.setValid(true);
                result.setUserId(claims.get("userId", String.class));
                result.setRole(claims.get("role", String.class));
                result.setMessage("Token valid");
            } else {
                result.setValid(false);
                result.setMessage("Token invalid");
            }
        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("Token parse error: " + e.getMessage());
        }
        return result;
    }
}
```

---

## 9. CLI服务目录结构

```
cli-service/
├── pom.xml                                    # Maven依赖管理（依赖workorder-api + OpenFeign）
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/workorder/cli/
│       │       ├── CliApplication.java        # Spring Boot启动类（@EnableFeignClients）
│       │       ├── controller/                # REST API控制层
│       │       │   └── ApiController.java     # 统一API控制器
│       │       ├── service/                   # 业务逻辑层
│       │       │   ├── AuthService.java       # 鉴权服务（调用AuthFeignClient）
│       │       │   ├── TraceService.java      # TraceID服务
│       │       │   ├── QueryService.java      # 查询代理服务（调用Feign客户端）
│       │       │   └── SchemaService.java     # Schema管理服务
│       │       ├── feign/                     # OpenFeign客户端接口
│       │       │   ├── WorkOrderFeignClient.java
│       │       │   ├── DashboardFeignClient.java
│       │       │   ├── FlowFeignClient.java
│       │       │   └── AuthFeignClient.java
│       │       ├── config/                    # 配置类
│       │       │   ├── FeignConfig.java       # OpenFeign配置
│       │       │   └── WebConfig.java         # Web配置
│       │       ├── interceptor/               # 拦截器
│       │       │   ├── TraceIdInterceptor.java # TraceID拦截器
│       │       │   └── AuthInterceptor.java   # 鉴权拦截器（调用AuthService）
│       │       ├── dto/                       # 数据传输对象
│       │       │   ├── request/               # 请求DTO
│       │       │   │   └── QueryRequest.java  # 查询请求
│       │       │   └── response/              # 响应DTO
│       │       │       ├── ApiResponse.java   # 统一响应封装
│       │       │       ├── DataCodeDTO.java   # dataCode信息
│       │       │       └── SchemaDTO.java     # Schema信息
│       │       ├── enums/                     # 枚举类
│       │       │   └── DataCodeEnum.java      # dataCode枚举
│       │       └── util/                      # 工具类
│       │           └── TraceIdUtil.java       # TraceID工具
│       └── resources/
│           ├── application.yaml               # 应用配置（包含Feign配置）
│           └── data-schemas.json              # dataCode Schema定义文件
└── README.md                                  # 项目说明
```

---

## 10. 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| Web框架 | Spring Boot | 3.3+ |
| RPC框架 | Spring Cloud OpenFeign | 4.1.0 |
| HTTP客户端 | Apache HttpClient (Feign内置) | 5.x |
| 配置管理 | Spring Boot配置 | YAML/Properties |
| JSON处理 | Jackson | Spring Boot内置 |
| 共享API模块 | 纯Java | - |

---

## 11. OpenFeign依赖明细

### 11.1 cli-service依赖

```xml
<dependencies>
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.3.0</version>
    </dependency>
    
    <!-- Spring Cloud OpenFeign -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
        <version>4.1.0</version>
    </dependency>
    
    <!-- Apache HttpClient for Feign -->
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
        <version>5.2.1</version>
    </dependency>
    
    <!-- WorkOrder API (共享模块) -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>workorder-api</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.30</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 11.2 workorder-api依赖

```xml
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.30</version>
        <scope>provided</scope>
    </dependency>
    
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-extension</artifactId>
        <version>3.5.5</version>
        <scope>provided</scope>
    </dependency>
    
    <dependency>
        <groupId>io.swagger.core.v3</groupId>
        <artifactId>swagger-annotations</artifactId>
        <version>2.2.20</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 12. 安全考虑

1. **RPC鉴权**：CLI服务通过OpenFeign调用后端鉴权接口，确保Token验证与后端一致
2. **权限控制**：根据Token中的用户角色过滤可访问的dataCode
3. **请求频率限制**：考虑添加限流机制防止恶意请求
4. **日志脱敏**：日志中不记录完整Token信息
5. **RPC安全**：OpenFeign传输使用HTTP，生产环境建议使用HTTPS
