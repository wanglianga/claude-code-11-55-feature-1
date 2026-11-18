# 碳排放披露数据血缘与版本影响管理平台

面向制造集团的碳排放披露管理平台：按工厂、能源类型、产品线汇总范围一/二/三排放，
对每一项 tCO2e 指标回答"用了哪份表计数据、哪版排放因子、为什么没随最新资料改变"。

- **来源批次**：工厂上传燃料/用电/采购活动数据，审核员核验；采购批次按来源行+供应商凭证检测冲突，已核算采购量不可被后续上传覆盖
- **因子目录**：按地域、适用期维护排放因子版本，审批生效；平台只使用地域与期间匹配的批准因子，缺因子时指标列为**待核**，不用集团平均数兜底
- **核算运行**：启动时快照因子清单（运行中替换因子不影响本次运行），活动量×因子生成带血缘的排放结果
- **披露模块**：运行结果冻结为报告版本；产品线合计与工厂明细不勾稽时报告保持**待核**，差额不能归零；已发布版本内容不可改写
- **影响服务**：来源补录/修订/缺失、因子修订时自动重算并对比受影响指标与已发布报告，生成重述建议与差异说明，经审批后以新版本重述，披露历史完整保留
- **市场化电力声明（凭证逐期归属）**：登记可再生电力属性凭证（序列号、发电/交付区间、区域、技术属性、注销状态、持有人变更留痕）与工厂用电账，把凭证逐期归属到可声明用电量；同时提供**逐张分配**与**批次池扣减**两种一等归属方式，输出每个工厂期间的可匹配量、不可匹配量、剩余量与凭证链；重复上传冻结未决、时间/区域错配不得抵销、报告后撤销/转让只标记失效不改写历史，凭证总量不会直接从集团排放中扣除

## 技术栈与架构

```
┌────────────┐   HTTP    ┌─────────────────────────┐      ┌──────────────┐
│ Vue3 前端   │ ───────▶ │ SpringBoot 后端 (Java)   │ ───▶ │ PostgreSQL   │
│ 血缘下钻/   │  /api/*  │ 批次校验·运行编排·版本状态 │      │ 来源/因子/运行 │
│ 影响对比/   │          │ 影响评估·披露审批          │      │ 快照/披露证据 │
│ 披露审批    │          └─────────────────────────┘      └──────────────┘
└────────────┘
```

- 前端：Vue 3 + Vite + Element Plus + Vue Router（hash 模式）
- 后端：Spring Boot 3.5（Web / Data JPA / Validation），HMAC 令牌认证，角色权限（管理员/分析员/审核员/财务披露）
- 数据库：PostgreSQL（表结构见 `backend/src/main/resources/schema.sql`）

## 一、Docker Compose 一键启动（推荐）

```bash
docker compose up --build
```

- 前端：http://localhost （nginx，反向代理 /api 到后端）
- 后端：http://localhost:8080/api/health
- 数据库：PostgreSQL 18（compose 内部 `postgres:5432`，库/用户/密码均为 `ccdev`）

首次启动时后端自动建表并写入样例数据（工厂、表计/燃料/采购批次、因子、2025-05 已发布报告，以及属性凭证、用电账、批次池与声明册演示数据）。

## 二、本地开发启动

前置：本机 PostgreSQL 可用（`127.0.0.1:5432`，库/用户/密码 `ccdev`）。

```bash
# 1. 后端（端口 8080；启动时自动建表+写入种子数据）
cd backend
mvn package -DskipTests
java -jar target/carbon-platform.jar

# 2. 前端（二选一）
#  a) 开发模式：vite 开发服务器，端口 5173，代理 /api 到 8080
cd frontend && npm install && npm run dev        # 打开 http://localhost:5173
#  b) 一体化模式：构建后由后端直接托管
cd frontend && npm install && npm run build      # 然后打开 http://localhost:8080
```

> 后端以 `file:./frontend/dist/` 与 `file:../frontend/dist/` 作为可选静态资源目录，
> 在仓库根目录或 backend/ 目录启动 jar 均可托管已构建的前端。

## 三、测试账号

| 账号 | 密码 | 角色 | 主要权限 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全部操作 |
| `analyst` | `analyst123` | 分析员 | 上传/修订批次、新建因子版本、启动运行、创建报告 |
| `auditor` | `auditor123` | 审核员 | 核验/驳回/作废批次、批准因子版本 |
| `finance` | `finance123` | 财务披露 | 审批/发布报告、接受/驳回重述建议 |

越权操作会被拒绝（403），例如分析员不能核验批次。

## 四、验证方式

### 4.1 自动化验收回放（已实际执行，原平台 59 项 + 电力声明 49 项全部通过）

```bash
# 前置：后端已启动且数据库为初始种子状态
python3 scripts/verify.py          # 原平台 S0~S5（59 项断言）
python3 scripts/verify_claims.py   # 市场化电力声明 C1~C8（49 项断言）
# 如需重置到种子状态后重跑：
bash scripts/reset-db.sh                  # 清空数据库
# 重启后端（自动重建表+种子数据），再依次执行两个 verify 脚本
```

电力声明回放场景（对应验收要求：重复上传、跨区凭证、月度错配、部分拆分、撤销样例）：

| 场景 | 内容 | 关键断言 |
| --- | --- | --- |
| C1 | 种子基线 | 苏州 5 月可匹配 90 万、跨区凭证 10 万计入不可匹配；重复上传冻结 12 万；声明册随报告 PUBLISHED；批次池 60 万可扣 |
| C2 | 批次池扣减 | 申请 70 万仅授予 60 万并报 10 万缺口；跨成员逐张留痕 40 万+20 万；池余额耗尽；池成员再逐张主张被 409 拦截 |
| C3 | 月度/区域错配 | 错月凭证 PERIOD_MISMATCH、跨区凭证 REGION_MISMATCH；不可匹配量 14 万单列，剩余量不被抵减 |
| C4 | 冻结未决与裁决 | 冻结凭证主张登记 FROZEN_PENDING 不形成覆盖；重复发起 409；裁决 FIRST_WON 后解冻、可声明余额恢复 12 万并成功匹配 |
| C5 | 部分拆分 | 10 万凭证分 3 万+4.5 万两条 MATCHED 链；超额申请截到余额 2.5 万并报缺口；余额耗尽后再主张 409 |
| C6 | 报告后撤销 | 撤销已发布报告中的凭证：声明册仍 PUBLISHED，原链保留且 chainValid=false、带原因与报告版本号；剩余用电回补；新声明 409 |
| C7 | 凭证转让 | 持有人变更、原工厂声明链失效 15 万并回补剩余；转让后新声明 409；他厂凭证声明本厂用电 400 |
| C8 | 恒等式与权限 | 全部声明册满足 total=matched+residual；用电账工厂×期间唯一 409；分析员撤销/裁决越权 403 |

### 4.2 页面手动验证

登录 http://localhost:8080 （或 5173）后：

1. **仪表盘**：最新发布披露总量、范围一/二/三、待处理影响评估数、待核报告/指标数、分工厂排放
2. **核算运行 → 详情 → 血缘**：任一排放结果可下钻到来源批次行（含供应商凭证、核验人）与因子版本（值、适用期、批准人）
3. **影响评估 → 详情**：受影响指标旧值/新值/差异对比、受影响报告链接、重述建议处置
4. **披露报告 → 详情**：版本历史（已发布/已重述/被取代）、披露条目、勾稽核对表、重述记录
5. **来源批次**：上传/核验/修订/作废；修订已消耗采购批次时可见 409 冲突提示
6. **因子目录**：新增版本→批准（替换旧版）；使用追踪展示绑定该版本的运行
7. **属性凭证**：登记凭证（重复上传提示冻结未决）、入池、注销/撤销/转让、凭证链与变更时间线、重复上传裁决
8. **声明册**：用电账登记或从电力批次生成；逐张分配/批次池扣减归属；查看可匹配量、不可匹配量（错配原因）、剩余量、重复冻结与事后失效链

## 五、关键业务规则实现

- **因子绑定快照**：运行创建时把当时已批准且覆盖该期间的因子版本写入 `run_factor_bindings`，核算只读快照；因子在运行中被替换只影响之后的新运行（部分唯一索引保证同一期间同时只有一个进行中的正式运行）
- **待核而非平均数**：因子匹配严格按 工厂地域+能源类型+适用期间；匹配不到批准因子时结果标记 `PENDING_FACTOR`（排放量留空），报告勾稽状态为 `PENDING_FACTOR`，审批/发布被拒
- **采购量保护**：修订批次核验时，原批次中已被核算消耗（存在血缘记录）的采购行必须按凭证号保持数量不变，否则整单拒绝并标记冲突；同一批次同时只允许一个进行中的修订
- **勾稽**：冻结时按"工厂明细合计 vs 产品线合计"核对，未分摊差异记入 `reconciliations`；`MISMATCH`/`PENDING_FACTOR` 状态下审批与发布均被拒，无"差额归零"入口
- **不可改写与重述**：已发布版本内容不更新；来源缺失/因子修订触发影响评估（候选运行 vs 已发布版本逐指标对比），重述建议被接受后冻结新版本，发布时旧版本标记 `RESTATED`，版本链完整可查

### 5.1 市场化电力声明（凭证逐期归属）规则

- **两种一等归属方式，互不降级**：
  - `CERTIFICATE` 逐张分配：按序列号精确归属，支持部分拆分（同凭证多条声明链，累计不超可声明余额），凭证级悲观锁（`PESSIMISTIC_WRITE`）串行化竞争；
  - `POOL` 批次池扣减：仅同区域 + 同发电月份 + 同技术属性的凭证可混池（池唯一键强约束），扣减按成员逐张留痕（`certificate_pool_members` 保留入池/已扣/冻结量），汇总快且可逐张审计。
- **不重复主张**：可声明余额 = 凭证电量 − 已有效匹配量 − 冻结未决量；同凭证对同用电账的冻结未决链有部分唯一索引约束，超余额主张直接 409，重复上传在登记环节即识别。
- **重复上传与竞争冻结**：同序列号被第二个工厂上传时不新建凭证（序列号全局唯一），竞争主张进入 `certificate_duplicate_uploads`，首张凭证的未决部分冻结；裁决"首张有效"释放冻结，裁决"后传有效"按转让过户并使原持有人声明链失效。
- **错配不得抵销**：区域不同 → `REGION_MISMATCH`，发电月份不同或交付区间不覆盖计量月 → `PERIOD_MISMATCH`；错配链只计入声明册**不可匹配量**，不增加覆盖、不占用凭证余额、不抵减剩余用电。
- **撤销/转让传播**：凭证撤销或转让后新声明立即停用；既有有效链标记 `chain_valid=FALSE` 并保留原因与时间，已发布报告中的声明链同样只打标记、不改写，声明册回补剩余用电并统计事后失效链量。
- **声明册恒等式**：`用电总量 = 可匹配量 + 剩余量`；不可匹配量与重复冻结量单独列示；报告发布时声明册与有效链固化到报告版本，冻结新版本时声明册重开允许调整。

## 六、API 概览（/api，除登录外均需 `X-Auth-Token`）

| 模块 | 端点 |
| --- | --- |
| 认证 | `POST /auth/login`、`GET /auth/me`、`GET /health` |
| 元数据 | `GET /meta/plants`、`GET /meta/product-lines`、`GET /meta/energy-types` |
| 批次 | `GET/POST /batches`、`GET /batches/{id}`、`POST /batches/{id}/verify|reject|revise|void` |
| 因子 | `GET/POST /factors`、`POST /factors/{id}/versions`、`POST /factors/versions/{vid}/approve|retire`、`GET /factors/versions/{vid}/usage` |
| 运行 | `GET/POST /runs`、`GET /runs/{id}`、`GET /results/{rid}/lineage` |
| 披露 | `GET/POST /reports`、`GET /reports/{id}`、`POST /reports/{id}/freeze|approve|publish`、`GET /reports/versions/{vid}` |
| 影响 | `GET /impacts`、`GET /impacts/{id}`、`POST /impacts/restatements/{id}/accept|reject` |
| 仪表盘 | `GET /dashboard/summary` |
| 属性凭证 | `GET/POST /certificates`、`GET /certificates/{id}`、`POST /certificates/{id}/cancel|revoke|transfer`、`GET /certificates/duplicates`、`POST /certificates/duplicates/{id}/resolve`、`GET /certificates/technologies` |
| 电力声明 | `GET/POST /claims/ledgers`、`POST /claims/ledgers/sync`、`GET /claims/ledgers/{id}`、`POST/DELETE /claims/allocations`、`POST /claims/pools/join`、`GET /claims/pools`、`GET /claims/pools/{id}`、`GET /claims/statements`、`GET /claims/statements/{id}` |

## 七、目录结构

```
├── backend/                 # SpringBoot 后端
│   ├── Dockerfile
│   ├── src/main/resources/schema.sql       # 表结构（幂等）
│   └── src/main/java/com/group/carbon/     # config/security/model/repo/service/controller
├── frontend/                # Vue3 前端
│   ├── Dockerfile  nginx.conf
│   └── src/views/           # 仪表盘/来源/因子/运行/影响/披露/属性凭证/声明册页面
├── scripts/
│   ├── verify.py            # 平台验收回放（S0~S5，59 项断言）
│   ├── verify_claims.py     # 电力声明验收回放（C1~C8，49 项断言）
│   └── reset-db.sh          # 重置本地数据库
└── docker-compose.yml       # 一键启动（postgres + backend + frontend）
```

## 八、验证记录（实际执行过的验证）

以下验证均在本开发容器内实际执行通过：

- `mvn package -DskipTests`：后端编译打包通过（Spring Boot 3.5.7，Java 25 运行时，编译目标 21）
- `npm install && npm run build`：前端构建通过（vite 6，产物 dist/）
- 后端启动后自动建表+种子数据：`/api/health` → UP；种子报告总量 2503.18 tCO2e 与手工核算一致
- `python3 scripts/verify.py`：**59 项断言全部通过**（S0~S5 全场景回放）
- `python3 scripts/verify_claims.py`：**49 项断言全部通过**（C1~C8：重复上传/跨区/月度错配/部分拆分/撤销/转让/恒等式/权限）
- 后端托管前端：`GET /` 与 `GET /assets/*.js` 均 200
- Docker Compose 配置：本容器无 docker CLI，`docker compose up` 未实际执行；Dockerfile/compose 已经静态核验（镜像标签、构建上下文、健康检查、依赖顺序、nginx 代理路径），请在有 Docker 的环境执行 `docker compose up --build` 验证
