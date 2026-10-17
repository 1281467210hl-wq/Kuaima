# Kuaima 后端接口文档

> 业务流程（老板端 / 用户端）时序图与状态机见 [FLOW.md](./FLOW.md)。

## 基本信息

- 服务地址：`http://localhost:8080`
- 接口前缀：无
- 数据格式：`application/json`
- 鉴权方式：`Authorization: Bearer <accessToken>`

## 统一响应格式

所有接口返回 `Result` 结构：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 状态码，`200` 成功 |
| `message` | string | 提示信息 |
| `data` | any | 业务数据（无数据时为 `null`，会被省略） |
| `page` | int | 当前页码（仅列表分页接口返回，从 0 开始） |
| `total` | long | 总条数（仅列表分页接口返回） |

**普通响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**分页响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": [],
  "page": 0,
  "total": 36
}
```

**错误响应示例：**

```json
{
  "code": 400,
  "message": "订单标题不能为空"
}
```

> 说明：fastjson2 序列化默认忽略 `null` 字段，因此非分页接口不会出现 `page`/`total`。

### 状态码

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误 / 业务校验失败 |
| 401 | 未登录或 Token 无效（`微信登录失败` 等） |
| 403 | 无权限访问 |
| 500 | 服务器内部错误 |

---

## 一、认证接口 `/auth`

> 系统仅支持微信小程序登录，无用户名密码登录。

### 1. 微信小程序登录

- **URL**：`POST /auth/wechat/login`
- **鉴权**：无需
- **流程**：小程序端 `wx.login()` 获取 `code`，后端调用微信 `jscode2session` 换取 `openid`；用户不存在时按所选身份自动注册（`username` 为 `wx_` 前缀，随机密码不可用密码登录），老用户则以本次选择的身份为准直接切换。若传入 `phoneCode`，后端调用微信 `getuserphonenumber` 换取手机号并保存（新用户注册时保存，老用户手机号为空时补全）。
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 是 | 小程序 `wx.login()` 返回的临时 code |
| `role` | string | 否 | 登录选择的身份：`BOSS`(老板) / `USER`(员工)；缺省按 `USER` 处理，非法值报 400 |
| `nickname` | string | 否 | 昵称（首次注册时保存，通过微信头像昵称填写能力获取） |
| `avatar` | string | 否 | 头像 URL（首次注册时保存） |
| `phoneCode` | string | 否 | 手机号动态令牌，来自小程序 `open-type="getPhoneNumber"` 按钮回调的 `code` |

**请求示例：**

```json
{
  "code": "0b3Xxxxx",
  "role": "BOSS",
  "nickname": "小明",
  "avatar": "https://thirdwx.qlogo.cn/xxx",
  "phoneCode": "dyn_xxxxxx"
}
```

**响应：** 返回登录令牌与用户信息，字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | string | JWT（有效期 3 天），载荷含 `uid`(用户ID) / `role` / `type=access` |
| `userId` | long | 当前用户ID |
| `username` | string | 用户名（`wx_` 前缀） |
| `role` | string | 本次生效的角色：`BOSS` / `USER`，前端据此路由到对应身份首页 |

> 审计说明：实体 `createBy` 即当前登录用户的 `userId`，由 JWT 载荷 `uid` 直接取得（`SecurityAuditorAware` 不再按 username 反查，旧 token 缺失 `uid` 时才回退反查）。

**错误：** `400 微信 code 不能为空`、`400 身份不合法：仅支持 BOSS(老板) / USER(员工)`、`401 微信登录失败：未获取到 openid`

### 2. 获取当前用户

- **URL**：`GET /auth/me`
- **鉴权**：需要 `Authorization: Bearer <accessToken>`

**响应：**

```json
{ "code": 200, "message": "success", "data": "zhangsan" }
```

---

## 二、招工订单接口 `/boss`

> 除特别说明外，以下接口均需鉴权。状态值为中文，使用 URL 编码（`Content-Type: application/x-www-form-urlencoded` 场景下可直接传中文）。

### 订单状态

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `ORDER_DRAFT` | `草稿` | 预留 |
| `ORDER_PENDING_AUDIT` | `待审核` | 初始状态，等待 admin 审核，审核通过后用户端可见 |
| `ORDER_AUDIT_REJECT` | `审核拒绝` | 终态 |
| `ORDER_RECRUITING` | `招工中` | 审核通过后进入，可报名 |
| `ORDER_RECRUIT_END` | `招工结束` | 停止报名 |
| `ORDER_PENDING_SETTLE` | `待结算` | |
| `ORDER_COMPLETED` | `已完成` | 终态 |
| `ORDER_CANCELED` | `取消招工` | 终态，级联取消未完成报名 |

**状态流转：** `待审核 →(admin 审核通过) 招工中 → 招工结束 → 待结算 → 已完成`；`待审核 →(admin 审核拒绝) 审核拒绝`；`招工中 / 招工结束 / 待结算 → 取消招工`

### 报名状态

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `ITEM_APPLIED` | `已报名` | 初始状态 |
| `ITEM_HIRED` | `已录用` | 老板录用 |
| `ITEM_ON_WORK` | `已到岗` | 用户确认到岗 |
| `ITEM_FINISHED` | `已完成` | 终态 |
| `ITEM_CANCELED` | `取消报名` | 用户取消 |
| `ITEM_CANCEL_BY_BOSS` | `取消招工` | 订单取消时级联置位 |

**报名流转：** `已报名 → 已录用 → 已到岗 → 已完成`；`已报名 / 已录用 → 取消报名`

### 招工类型

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `TYPE_DAILY` | `daily` | 每天日结，工作时长按小时计 |
| `TYPE_HELD_BACK` | `heldBack` | 压薪日结，工作时长按天计 |
| `TYPE_MONTH` | `month` | 月结，可设试工时间，报名时可勾选"我要试工" |

### 1. 发布订单

- **URL**：`POST /boss/order`
- **请求体**（`BossOrder`，初始状态自动为 `待审核`，admin 审核通过后变为 `招工中` 并广播新岗位消息）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderTitle` | string | 是 | 订单标题 |
| `type` | string | 是 | 招工类型：`daily` / `heldBack` / `month` |
| `postion` | string | 是 | 招聘岗位 |
| `orderNum` | int | 是 | 招工人数，必须大于 0 |
| `duration` | int | 是 | 工作时长：日结按小时，压薪日结/月结按天 |
| `salary` | int | 是 | 工资（元/天），必须大于 0 |
| `startTime` | datetime | 否 | 开始时间，与 `endTime` 一起填写时须早于结束时间 |
| `endTime` | datetime | 否 | 结束时间 |
| `orderContent` | string | 否 | 招工内容（最长 500） |
| `orderRemark` | string | 否 | 订单备注 |
| `address` | string | 否 | 工作地点 |
| `tags` | string | 否 | 标签 |
| `trialDuration` | string | 否 | 试工时间，仅月结(`month`)类型有效，其他类型会被忽略 |

**请求示例：**

```json
{
  "orderTitle": "工地搬砖 5 人",
  "type": "daily",
  "postion": "搬砖工",
  "orderNum": 5,
  "duration": 8,
  "salary": 300,
  "orderContent": "日结，管午饭",
  "orderRemark": "自带工具",
  "address": "XX工地",
  "tags": "日结,包午饭",
  "startTime": "2026-09-03 08:00:00",
  "endTime": "2026-09-03 18:00:00"
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "orderTitle": "工地搬砖 5 人",
    "orderStatus": "招工中",
    "type": "daily",
    "postion": "搬砖工",
    "orderNum": 5,
    "duration": 8,
    "salary": 300,
    "createBy": 1,
    "date": "2026-09-02"
  }
}
```

**错误：** `400 订单标题不能为空`、`400 招工类型不能为空`、`400 招工类型不合法: xxx`、`400 招聘岗位不能为空`、`400 招工人数必须大于 0`、`400 工作时长必须大于 0`、`400 工资必须大于 0`、`400 结束时间必须晚于开始时间`

### 2. 修改订单

- **URL**：`PUT /boss/order/{id}`
- **说明**：仅 `待审核` 或 `招工中` 状态可修改；请求体字段非空才会被更新。类型改为非月结时会清空 `trialDuration`；试工时间仅在类型为 `month` 时可设置。

**错误：** `400 仅待审核或招工中的订单可以修改`、`400 招工类型不合法: xxx`、`400 结束时间必须晚于开始时间`

### 3. 订单详情

- **URL**：`GET /boss/order/{id}`
- **响应**：`data` 为完整订单对象（含 `id`、`date`、`createBy`、`timestamp` 及订单字段）。

**错误：** `404 订单不存在: {id}`

### 4. 订单列表（分页）

- **URL**：`GET /boss/order`
- **鉴权**：需要
- **查询参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `type` | string | 否 | - | 按招工类型过滤：`daily` / `heldBack` / `month` |
| `status` | string | 否 | - | 按订单状态过滤（如 `招工中`） |
| `title` | string | 否 | - | 按标题模糊过滤 |
| `page` | int | 否 | `0` | 页码，从 0 开始 |
| `size` | int | 否 | `10` | 每页条数，限制 1~100 |

**请求示例：**

```
GET /boss/order?type=daily&status=%E6%8B%9B%E5%B7%A5%E4%B8%AD&page=0&size=10
```

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 3,
      "orderTitle": "工地搬砖 5 人",
      "orderStatus": "招工中",
      "orderNum": 5
    }
  ],
  "page": 0,
  "total": 1
}
```

### 5. 删除订单

- **URL**：`DELETE /boss/order/{id}`
- **说明**：仅 `待审核` / `审核拒绝` / `招工中` / `取消招工` 状态可删除。

**错误：** `400 当前状态的订单不允许删除`、`404 订单不存在: {id}`

### 6. 订单状态流转

- **URL**：`PUT /boss/order/{id}/status?target={目标状态}`
- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `target` | string | 是 | `招工结束` / `待结算` / `已完成` / `取消招工` |

- **说明**：`取消招工` 会将该订单下所有未完成的报名记录置为 `取消招工` 并记录取消时间。

**错误：** `400 不允许从「当前状态」流转到「目标状态」`

### 7. 用户报名

老板端不再提供报名接口（原 `POST /boss/order/{orderId}/apply` 以请求参数 `userId` 识别报名人，可冒用他人身份，已删除）。零工报名统一走 `POST /jobs/{orderId}/apply`，报名人取自登录 JWT，并要求完成人脸实名认证。

### 8. 老板录用

- **URL**：`PUT /boss/item/{id}/hire`
- **说明**：仅 `已报名` 的记录可录用，状态变为 `已录用` 并记录录用时间。

**错误：** `400 仅已报名的记录可以录用`、`404 报名记录不存在: {id}`

### 9. 用户确认到岗

- **URL**：`PUT /boss/item/{id}/work`
- **说明**：仅 `已录用` 的记录可确认到岗，状态变为 `已到岗` 并记录到岗时间。

**错误：** `400 仅已录用的记录可以确认到岗`

### 10. 完成

- **URL**：`PUT /boss/item/{id}/finish`
- **说明**：仅 `已到岗` 的记录可完成，状态变为 `已完成` 并记录完成时间。

**错误：** `400 仅已到岗的记录可以完成`

### 11. 取消报名

- **URL**：`PUT /boss/item/{id}/cancel`
- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `reason` | string | 否 | 取消原因 |

- **说明**：仅 `已报名` / `已录用` 状态可取消，状态变为 `取消报名` 并记录取消时间、原因。

**错误：** `400 当前状态不可取消报名`

### 12. 某订单的报名列表

- **URL**：`GET /boss/order/{orderId}/items`
- **响应**：`data` 为该订单下所有报名记录数组（`BaseOrderItem`）。

**错误：** `404 订单不存在: {id}`

### 13. 某用户的报名记录

- **URL**：`GET /boss/user/items?userId={userId}`
- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | long | 是 | 用户 ID |

- **响应**：`data` 为该用户所有报名记录数组。

### 14. 岗位快开始提醒（预留接口）

- **URL**：`POST /boss/order/{id}/remind-start`
- **说明**：手动触发「岗位快开始」通知，推送给该订单下 **已报名 / 已录用 / 已到岗** 的零工。当前**不做定时任务**，由前端/人工调用（后续如需自动提醒，可在此之上加定时任务扫描 `startTime` 自动触发）。

---

## 三、实体公共字段

所有实体继承 `BaseEntity`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 主键，自增 |
| `date` | date | 创建日期（`@CreatedDate`） |
| `createBy` | long | 创建人 ID（`@CreatedBy`，由当前登录用户自动填充） |
| `timestamp` | datetime | 最近更新时间（`@UpdateTimestamp`） |

**User（表 `sys_user`）**：`username`、`password`（不返回）、`role`（`BOSS` 老板 / `USER` 员工，登录时按所选身份切换）、`openid`、`nickname`、`avatar`、`phone`、`email`、`status`（`正常` / `冻结`）、`remark`、`age`、`gender`、`balance`（`@Transient`，不落库）；admin 管理扩展字段：`certStatus`（`未认证`/`待审核`/`已通过`/`已拒绝`）、`creditScore`（0~100）、`certType`（`REALNAME` 零工实名 / `ENTERPRISE` 企业认证）、`skills`（技能标签，逗号分隔）、`companyName`（仅 BOSS）、`industry`（仅 BOSS）、`contact`（仅 BOSS）、`contactPhone`（仅 BOSS）

后台用户展示使用独立业务编号：零工 `workerCode` 为 `G` + 7 位数字，老板 `bossCode` 为 `B` + 7 位数字；两者均持久化且唯一，不向管理端展示数据库主键。后台信用分列表的 `id` 为该业务编号，`userId` 仅用于兼容内部关联。

**BossOrder（表 `boss_order`）**：`orderTitle`、`orderStatus`、`type`、`orderContent`、`orderRemark`、`postion`、`orderNum`、`duration`、`address`、`tags`、`startTime`、`endTime`、`salary`、`trialDuration`

**BaseOrderItem（表 `boss_order_item`）**：`orderId`、`userId`、`status`、`trialRequested`、`remark`、`applyDate`、`hireDate`、`workDate`、`finishDate`、`cancelDate`、`cancelReason`

---

## 四、结算与钱包接口 `/settle`、`/wallet`

> 资金流：老板结算（付工资给零工 + 平台服务费）→ 工资入零工钱包 → 零工自行申请提现 → 平台模拟打款（后续接微信支付/商家转账）。
>
> **金额单位：一律为「分」（Long）**，与微信支付分单位一致；展示层请自行除以 100。
>
> 当前为**钱包记账 + 模拟支付**阶段：
> - 结算支付、提现打款均为模拟成功，不做真实扣款/入账渠道；
> - 平台服务费率默认 0（`kuaima.settle.service-fee-rate`），字段与流水结构已预留。

### 结算单状态

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `PENDING` | `待支付` | 老板发起结算后生成 |
| `PAID` | `已支付` | 模拟支付成功，工资已入零工钱包 |
| `CANCELED` | `已取消` | |

### 提现单状态

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `PENDING` | `申请中` | 申请成功即扣减钱包余额，待打款 |
| `SUCCESS` | `已打款` | 模拟打款成功 |
| `FAILED` | `打款失败` | 打款失败，余额自动退回钱包 |

### 钱包流水方向与业务类型

| 字段 | 取值 | 说明 |
| --- | --- | --- |
| `direction` | `income` / `outcome` | 收入 / 支出 |
| `bizType` | `WAGE`(工资入账) / `WITHDRAW`(提现) / `WITHDRAW_REFUND`(提现退回) | 业务类型 |

### 1. 发起结算

- **URL**：`POST /settle?itemId={itemId}&workDays={workDays}`
- **鉴权**：需要（老板端）
- **前置条件**：报名记录须为 `已完成`（item.finish 置为已完成）。
- **说明**：系统按 **订单日薪(元/天) × 工作天数** 自动算出应付零工工资并转分为单位；`workDays` 为空时按「到岗日 ~ 完成日」自动推导天数（至少 1 天）。生成 `待支付` 结算单；同一报名记录不允许重复发起（已存在待支付/已支付结算单时拒绝）。服务费当前按配置费率计算（默认 0）。
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemId` | long | 是 | 报名记录 ID |
| `workDays` | int | 否 | 实际工作天数，为空自动推导 |

**响应**：`Settlement`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "itemId": 5,
    "orderId": 2,
    "workerId": 3,
    "workDays": 2,
    "wage": 60000,
    "serviceFee": 0,
    "totalAmount": 60000,
    "status": "待支付"
  }
}
```

**错误：** `400 仅已完成的报名记录可以发起结算`、`400 该报名记录已存在待支付/已支付的结算单，请勿重复结算`、`400 订单工资未设置，无法结算`、`404 报名记录不存在: {id}`

### 2. 模拟支付结算单

- **URL**：`POST /settle/{id}/pay`
- **鉴权**：需要（老板端）
- **说明**：将 `待支付` 结算单置为 `已支付`（生成模拟支付流水号、记录支付时间），并把工资入零工钱包并记一笔 `income/WAGE` 流水；服务费归平台，仅记录在结算单上。
- **响应**：`Settlement`

**错误：** `400 仅待支付的结算单可以支付`、`404 结算单不存在: {id}`

### 3. 某订单的结算单列表

- **URL**：`GET /settle/order/{orderId}`
- **响应**：`data` 为该订单下所有结算单数组（`Settlement`）。

### 4. 某零工的结算单列表

- **URL**：`GET /settle/worker/{userId}`
- **响应**：`data` 为该零工所有结算单数组。

### 5. 零工钱包查询

- **URL**：`GET /wallet/{userId}`（等价别名：`GET /settle/wallet/{userId}`，两者返回相同）
- **说明**：返回该用户钱包（`balance` 单位分）；不存在则自动创建余额 0 的钱包。

### 6. 零工钱包流水

- **URL**：`GET /wallet/{userId}/flows`
- **说明**：`data` 为该用户钱包流水数组（最新在前），含方向、业务类型、变动金额、变动后余额、关联业务 id。

### 7. 零工提现记录

- **URL**：`GET /wallet/{userId}/withdraws`
- **说明**：`data` 为该用户提现单数组（最新在前）。

### 8. 申请提现

- **URL**：`POST /wallet/withdraw?userId={userId}&amount={amount}&account={account}&remark={remark}`
- **鉴权**：需要（零工端）
- **说明**：校验金额大于 0 且不超过钱包余额；成功后**立即扣减钱包余额**并生成 `申请中` 提现单，记一笔 `outcome/WITHDRAW` 流水。`amount` 单位分。
- **响应**：`WithDraw`

**错误：** `400 提现金额必须大于 0`、`400 余额不足，当前可用余额(分): xxx`

### 9. 模拟打款成功

- **URL**：`POST /wallet/withdraw/{id}/payout`
- **鉴权**：需要（平台/管理员模拟操作；真实接入后由微信商家转账回调驱动）
- **说明**：将 `申请中` 提现单置为 `已打款` 并记录打款时间。

**错误：** `400 仅申请中的提现单可以打款`

### 10. 模拟打款失败（退回余额）

- **URL**：`POST /wallet/withdraw/{id}/fail?reason={reason}`
- **说明**：将 `申请中` 提现单置为 `打款失败`，并把提现金额退回钱包，记一笔 `income/WITHDRAW_REFUND` 流水。

**错误：** `400 仅申请中的提现单可以标记失败`

### 11. 老板全部待结算订单

`GET /boss/settlements/pending`

按当前老板 JWT 返回全部岗位的待结算报名记录、零工、金额和结算状态。**不接受前端传入 userId/bossId**，身份从 JWT 获取。

返回 `PendingSettlementOrder` 列表（含订单、报名记录、零工信息、工资金额、结算状态）。

**错误：** `500 当前登录账号不是有效的老板账号`

### 实体补充

**Wallet（表 `wallet`）**：`userId`（唯一）、`balance`（可用余额，分）

**Settlement（表 `boss_settlement`）**：`itemId`、`orderId`、`workerId`、`workDays`、`wage`(分)、`serviceFee`(分)、`totalAmount`(分)、`status`、`payNo`、`payTime`

**WithDraw（表 `with_draw`）**：`userId`、`amount`(分)、`status`、`channel`(模拟 `mock`)、`account`(收款账户，预留)、`applyTime`、`payTime`、`remark`

**WalletFlow（表 `wallet_flow`）**：`userId`、`direction`、`bizType`、`amount`(分)、`balanceAfter`(分)、`bizId`、`remark`

---

## 五、消息中心接口 `/message`

> 载体：**站内消息中心**（无 WebSocket / 订阅消息依赖）。事件发生时后端把一条消息写入接收者收件箱（表 `sys_message`），小程序**轮询**以下接口获取，实现「报名/录用/到岗/发布/取消/到账/失败」等业务提醒。
>
> 接口不对消息类型鉴权，`userId` 即当前登录用户；未读数用于 tab 红点。

### 消息类型（`type`，写入 `sys_message.type`）

| 常量 | 触发事件 | 接收方 | 含义 |
| --- | --- | --- | --- |
| `ORDER_PUBLISH` | admin 审核通过（自动广播） | 全部员工(USER)（招聘广播，量大后可按城市/类型定向） | 新岗位发布 |
| `ORDER_APPLY` | 零工报名岗位 | 订单老板(BOSS) | 您有新的报名 |
| `ORDER_HIRE` | 老板录用零工 | 被录用者(USER) | 录用通知 |
| `ORDER_START_REMIND` | 手动触发（预留，见 `/boss/order/{id}/remind-start`） | 该单已报名/已录用/已到岗 USER | 岗位快开始 |
| `ORDER_CANCEL` | 老板取消招工 | 该单未完成的报名者(USER) | 招工已取消 |
| `ITEM_CANCEL` | 零工取消报名 | 订单老板(BOSS) | 报名取消提醒 |
| `ITEM_WORK_CONFIRM` | 零工确认到岗 | 订单老板(BOSS) | 零工已到岗 |
| `SETTLE_PAID` | 结算单模拟支付成功（工资入钱包） | 收款零工(USER) | 工资已到账 |
| `WITHDRAW_FAIL` | 提现模拟打款失败（金额已退回钱包） | 提现零工(USER) | 提现打款失败 |

### 1. 未读消息数

- **URL**：`GET /message/unread?userId={userId}`
- **响应**：`data` 为未读数（long）。

### 2. 消息列表（分页）

- **URL**：`GET /message/list?userId={userId}&read={read}&page={page}&size={size}`
- **参数**：`read` 可选，`true`/`false` 只看已读/未读，不传返回全部；`page` 从 0 开始（默认 0，与 `/boss/order` 等一致），`size` 默认 20。
- **响应**：统一分页结构（`data` 为当前页消息数组 + `page`/`total`，最新在前）。`Message` 字段：`userId`、`role`、`type`、`title`、`content`、`bizType`（跳转业务详情用：`order`/`item`/`settle`/`withdraw`）、`bizId`、`readFlag`、`readTime`、`createTime`。

### 3. 单条标记已读

- **URL**：`PUT /message/{id}/read?userId={userId}`
- **说明**：校验该消息归属当前用户后置为已读并记录已读时间。
- **错误**：`404 消息不存在或不属于该用户`

### 4. 全部标记已读

- **URL**：`PUT /message/readAll?userId={userId}`
- **响应**：`data` 为本次标记条数。

### 实体补充

**Message（表 `sys_message`）**：`userId`（接收者）、`role`（接收者角色 BOSS/USER）、`type`（消息类型）、`title`、`content`(≤500)、`bizType`、`bizId`、`readFlag`（已读标记）、`readTime`、`createTime`

---

## 六、后台管理接口 `/admin`

> 供 admin 管理后台（Vue3 + Element-Plus）使用，与小程序端 `/auth`、`/boss` 等接口相互独立。
>
> 除「管理员登录」外均需携带 `Authorization: Bearer <accessToken>`（登录返回的管理员 token，JWT role 前缀 `ADMIN_`）。
>
> 默认账号：`admin / admin123`（`DataInitializer` 初始化，角色 `SUPER_ADMIN`）。

### 0. 文件上传 `/admin/upload`

上传图片文件（如平台 Logo），返回可访问的静态资源 URL。

| 接口 | 说明 |
| --- | --- |
| `POST /admin/upload` | 上传图片，form-data: `file`（MultipartFile） |

**约束**：仅支持 image/* 格式，大小 ≤ 2MB。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "url": "/uploads/2026/09/05/a1b2c3d4.png",
    "fileName": "a1b2c3d4.png"
  }
}
```

静态资源通过 `/uploads/**` 访问（无需鉴权），对应本地存储目录 `./uploads/`。

### 1. 管理员登录

- **URL**：`POST /admin/auth/login`
- **鉴权**：无需
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 管理员账号 |
| `password` | string | 是 | 密码 |

**响应：** `data` 含 `accessToken`（JWT，role 为 `ADMIN_` 前缀）、`adminId`、`username`、`name`、`role`（`SUPER_ADMIN`/`ADMIN`/`EDITOR`/`VIEWER`）；登录成功自动更新 `lastLoginTime`。

**错误：** `400 用户名和密码不能为空`、`400 用户名或密码错误`、`403 账号已被禁用`

### 2. 获取当前管理员

- **URL**：`GET /admin/auth/me`
- **鉴权**：需要
- **说明**：返回当前登录管理员信息（预留接口，当前返回空对象）。

**响应示例：**

```json
{ "code": 200, "message": "success", "data": {} }
```

### 3. Dashboard 统计

- **URL**：`GET /admin/dashboard/stats`
- **响应字段**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `workerTotal` | long | 零工（USER）总数 |
| `bossTotal` | long | 雇主（BOSS）总数 |
| `orderTotal` | long | 招工订单总数 |
| `settledTotal` | long | 结算单总数（**条数**，非金额） |
| `pendingAudit` | long | 待审核订单数 |

### 3a. 订单趋势

- **URL**：`GET /admin/dashboard/trend`
- **说明**：返回近 7 天每日订单数，用于折线图。

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "labels": ["05-29", "05-30", "05-31", "06-01", "06-02", "06-03", "06-04"],
    "values": [3, 5, 2, 8, 4, 6, 1]
  }
}
```

### 3b. 工种分布

- **URL**：`GET /admin/dashboard/distribution`
- **说明**：按招工类型（日结/压薪日结/月结）分组统计订单数，用于饼图。

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "name": "日结", "value": 12, "color": "#FF6B35" },
    { "name": "月结", "value": 5, "color": "#2563EB" }
  ]
}
```

### 3c. 最近订单

- **URL**：`GET /admin/dashboard/recent-orders`
- **说明**：返回最新 8 条订单（含 `employerName` 雇主名称），按 id 倒序。返回 `BossOrderView`（字段同 `/admin/jobs` 列表项）。

### 4. 用户管理 `/admin/users`

| 接口 | 说明 |
| --- | --- |
| `GET /admin/users/workers` | 零工列表（分页）。参数：`status`（`正常`/`冻结`）、`keyword`（昵称/手机号模糊）、`page`（默认 0）、`size`（默认 10）。返回 `User`，**不含 password** |
| `GET /admin/users/bosses` | 雇主列表（分页），参数同上，含 `companyName` 等企业字段 |
| `GET /admin/users/{id}` | 用户详情 |
| `PUT /admin/users/{id}/freeze` | 冻结用户（`status` 置为 `冻结`） |
| `PUT /admin/users/{id}/unfreeze` | 解冻用户（`status` 置为 `正常`） |
| `PUT /admin/users/freeze/batch?ids=1,2,3` | 批量冻结 |
| `PUT /admin/users/unfreeze/batch?ids=1,2,3` | 批量解冻 |

**错误：** `404 用户不存在: {id}`

### 5. 管理员账号管理 `/admin/admin-users`

| 接口 | 说明 |
| --- | --- |
| `GET /admin/admin-users` | 管理员列表（分页，`page`/`size`） |
| `GET /admin/admin-users/{id}` | 管理员详情 |
| `POST /admin/admin-users` | 新增管理员。请求体 `AdminUser`（`username` 唯一、`password` 自动加密、`role`、`name`、`dept`、`phone`、`email`、`status`） |
| `PUT /admin/admin-users/{id}` | 更新管理员，请求体字段非空才更新；`password` 非空则重加密 |
| `PUT /admin/admin-users/{id}/reset-password` | 重置密码。请求体 `{"newPassword": "xxx"}`，缺省重置为 `admin123` |
| `DELETE /admin/admin-users/{id}` | 删除管理员 |

**错误：** `400 账号已存在`、`404`（`orElseThrow`，id 不存在返回 500 包装错误）

### 6. 招工管理 / 招工审核 `/admin/jobs`

| 接口 | 说明 |
| --- | --- |
| `GET /admin/jobs` | 招工列表（分页，按 id 倒序）。参数：`type`（`daily`/`heldBack`/`month`）、`status`（如 `待审核`）、`title`（模糊）、`page`、`size`。返回 `BossOrderView`，在订单字段基础上扩展 `employerName`（雇主企业名，为空取昵称，再取用户名） |
| `GET /admin/jobs/{id}` | 招工详情（完整 `BossOrder`） |
| `PUT /admin/jobs/{id}/audit/pass` | 审核通过：`待审核 → 招工中`，并向全部员工广播 `ORDER_PUBLISH` 消息 |
| `PUT /admin/jobs/{id}/audit/reject?reason={原因}` | 审核拒绝：`待审核 → 审核拒绝`，原因追加到 `orderRemark` |
| `PUT /admin/jobs/{id}/status?target={目标状态}` | 状态流转（同 `/boss/order/{id}/status`，admin 端手动推进） |

**错误：** `400 仅待审核的订单可以审核通过`、`400 仅待审核的订单可以审核拒绝`、`404 订单不存在: {id}`

**BossOrderView 字段**：`id`、`orderTitle`、`orderContent`、`orderNo`（暂为 null）、`orderNum`、`orderStatus`、`type`、`postion`、`duration`、`salary`、`address`、`tags`、`trialDuration`、`timestamp`、`startTime`、`endTime`、`employerName`

### 7. 订单管理（报名记录全量视图） `/admin/orders`

| 接口 | 说明 |
| --- | --- |
| `GET /admin/orders` | 全部报名记录列表（非分页）。参数：`status` 可选（`已报名`/`已录用`/`已到岗`/`已完成`/`取消报名`/`取消招工`） |

### 8. 结算管理 `/admin/settlements`

| 接口 | 说明 |
| --- | --- |
| `GET /admin/settlements` | 结算单列表（分页，`status` 可选：`待支付`/`已支付`/`已取消`，`page`/`size`） |
| `GET /admin/settlements/{id}` | 结算单详情 |
| `POST /admin/settlements/{id}/pay` | 手动结算（模拟支付）：`待支付 → 已支付`，工资入零工钱包 |

**错误：** `400 仅待支付的结算单可以支付`、`404 结算单不存在: {id}`

### 9. 操作日志 `/admin/logs`

| 接口 | 说明 |
| --- | --- |
| `GET /admin/logs` | 日志列表（分页，`page` 默认 0、`size` 默认 20，按 id 倒序）。`AdminLog` 字段：`operator`、`type`（登录/数据修改/审核/权限变更/删除/系统操作）、`target`、`ip`、`result`（成功/失败）、`detail`、`createTime` |

### 10. 系统设置 `/admin/settings`

KV 存储（表 `admin_setting`），`AdminSetting` 字段：`settingKey`（主键）、`settingValue`、`category`（`platform`/`notification`/`security`/`points`）、`description`。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/settings` | 全部设置 |
| `GET /admin/settings/category/{category}` | 按分类查询 |
| `GET /admin/settings/{key}` | 按 key 查询单条 |
| `PUT /admin/settings/{key}` | 保存/更新，请求体 `{"settingValue": "...", "category": "...", "description": "..."}` |

#### 银行账户设置

| 接口 | 说明 |
| --- | --- |
| `GET /admin/settings/bank-account` | 获取银行账户信息（未配置返回默认值） |
| `PUT /admin/settings/bank-account` | 保存银行账户信息（请求体为 JSON 字符串，字段：`bankName`、`cardNumber`、`holder`、`branch`、`swiftCode`、`bankCode`、`accountType`） |

> 存储 key：`bank_account_info`，category：`platform`

#### 钱包账户设置

| 接口 | 说明 |
| --- | --- |
| `GET /admin/settings/wallet-account` | 获取钱包账户信息（含支付宝+微信，未配置返回默认值） |
| `PUT /admin/settings/wallet-account` | 保存钱包账户信息（请求体为 JSON 字符串，结构：`{ "alipay": { account, holder, isDefault }, "wechat": { account, holder, isDefault } }`） |

> 存储 key：`wallet_account_info`，category：`platform`

#### 模板测试发送

`POST /admin/settings/test-send`

请求体（JSON 字符串）：
```json
{
  "type": "sms",
  "templateTitle": "订单接单通知",
  "receiver": "13800138000",
  "content": "【快马日结】尊敬的用户，您已成功接单..."
}
```

返回：
```json
{
  "success": true,
  "maskedReceiver": "138****8000",
  "sentAt": "2026-09-05T10:00:00"
}
```

### 11. Banner 管理 `/admin/banners`

`Banner` 字段：`title`、`imageUrl`、`position`（`worker-home`/`boss-home`/`admin-home`）、`weight`（排序权重）、`linkUrl`、`status`（`展示中`/`草稿`/`已下架`）、`startTime`、`endTime`、`createTime`、`updateTime`。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/banners` | 列表 |
| `GET /admin/banners/{id}` | 详情 |
| `POST /admin/banners` | 新增 |
| `PUT /admin/banners/{id}` | 更新（字段非空才更新） |
| `DELETE /admin/banners/{id}` | 删除 |

### 12. 公告管理 `/admin/notices`

`Notice` 字段：`title`、`type`（`系统`/`活动`/`政策`）、`scope`（`全部`/`零工`/`雇主`）、`content`（TEXT）、`status`（`已发布`/`草稿`/`已下架`）、`publishTime`、`createTime`、`updateTime`。新建/更新为 `已发布` 时自动记录 `publishTime`（仅首次）。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/notices` | 列表 |
| `GET /admin/notices/{id}` | 详情 |
| `POST /admin/notices` | 新增 |
| `PUT /admin/notices/{id}` | 更新（字段非空才更新） |
| `DELETE /admin/notices/{id}` | 删除 |

### 12A. 消息模板管理 `/admin/message-templates`

`MessageTemplate` 字段：`id`、`name`、`event`（触发事件）、`scene`（触发场景，人类可读）、`channel`（发送渠道）、`content`（TEXT）、`status`（`enabled`/`disabled`）、`sendWay`（发送方式：即时/定时）、`sendTime`（发送时间：全天/工作时间）、`freqLimit`（频率限制：5/10/不限制）、`lastUsed`、`createTime`、`updateTime`。

**event 可选值**：`order_success`（订单完成）、`order_cancel`（订单取消）、`register`（用户注册）、`realname_approved`（实名认证通过）、`realname_rejected`（实名认证拒绝）、`settlement`（结算到账）、`subsidy`（补贴发放）、`system`（系统通知）。

**channel 可选值**：`inapp`（仅站内信）、`sms`（仅短信）、`both`（站内信+短信）。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/message-templates?status=&event=` | 列表（支持 status/event 过滤） |
| `GET /admin/message-templates/{id}` | 详情 |
| `POST /admin/message-templates` | 新增 |
| `PUT /admin/message-templates/{id}` | 更新（字段非空才更新） |
| `PUT /admin/message-templates/{id}/toggle` | 启用/禁用切换 |
| `PUT /admin/message-templates/{id}/used` | 标记最近使用 |
| `DELETE /admin/message-templates/{id}` | 删除 |

### 13. 规则管理 `/admin/rules`

`Rules` 字段：`title`、`category`（`通知公告`/`信用评定`/`收费标准`/`交易规则`/`隐私协议`）、`version`（默认 `v1.0`）、`status`（`草稿`/`已发布`/`已归档`）、`effectiveTime`、`content`（TEXT）、`createTime`、`updateTime`。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/rules` | 列表 |
| `GET /admin/rules/{id}` | 详情 |
| `POST /admin/rules` | 新增 |
| `PUT /admin/rules/{id}` | 更新（字段非空才更新） |
| `DELETE /admin/rules/{id}` | 删除 |

### 14. 黑名单管理 `/admin/blacklists`

`Blacklist` 字段：`userId`、`userType`（`零工`/`雇主`）、`reason`、`status`（`封禁中`/`已解封`）、`expireTime`、`createTime`、`updateTime`。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/blacklists` | 列表 |
| `GET /admin/blacklists/{id}` | 详情 |
| `POST /admin/blacklists` | 加入黑名单（请求体 `Blacklist`） |
| `PUT /admin/blacklists/{id}/unfreeze` | 解封（`status` 置为 `已解封`） |
| `PUT /admin/blacklists/{id}/extend` | 延长封禁，请求体 `{"expireTime": "2026-12-31T00:00:00"}` |
| `DELETE /admin/blacklists/{id}` | 删除 |

### 15. 认证审核 `/admin/certifications`

`Certification` 字段：`userId`、`type`（`零工实名`/`企业认证`）、`applicantName`、`contactPhone`、`status`（`待审核`/`已通过`/`已拒绝`）、`rejectReason`、`auditTime`、`applyTime`、`createTime`。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/certifications` | 列表 |
| `GET /admin/certifications/{id}` | 详情 |
| `PUT /admin/certifications/{id}/pass` | 审核通过（`status` 置为 `已通过`，记录 `auditTime`） |
| `PUT /admin/certifications/{id}/reject?reason={原因}` | 审核拒绝（记录 `rejectReason`、`auditTime`） |

### 16. 举报处理 `/admin/reports`

`Report` 字段：`reporterId`（举报人）、`targetId`（被举报人）、`type`（`违规`/`纠纷`/`虚假信息`）、`reason`、`status`（`待处理`/`处理中`/`已处理`）、`result`（处理结果）、`orderId`（可选关联订单）、`handleTime`、`createTime`。

| 接口 | 说明 |
| --- | --- |
| `GET /admin/reports` | 列表 |
| `GET /admin/reports/{id}` | 详情 |
| `POST /admin/reports` | 创建举报（来自用户端） |
| `POST /admin/reports/{id}/handle?result={处理结果}` | 处理举报（`status` 置为 `已处理`，记录 `handleTime`） |

---

## 七、用户资料与认证 `/user`

### 1. 获取用户资料

`GET /user/{id}`

返回 `User` 实体完整信息（含 `nickname`、`avatar`、`phone`、`certStatus`、`creditScore`、`companyName` 等）。

### 2. 修改用户资料

`PUT /user/{id}`

请求体（JSON，所有字段可选）：`nickname`、`avatar`、`phone`、`email`、`age`、`gender`、`city`、`skills`、`remark`

### 3. 提交实名认证

`POST /user/{id}/realname`

请求体：`{ "realName": "张三", "idCard": "340..." }`

设置 `User.certType=REALNAME`、`certStatus=待审核`，同时写入 `Certification` 审核记录。

### 4. 认证审核历史

`GET /user/{id}/certifications`

返回用户的认证审核记录列表。

### 5. 信用分与近期流水

`GET /user/{id}/credit`

返回：`{ "creditScore": 85, "flows": [...] }`（flows 为最近 10 条 `CreditFlow`）

后台信用分管理接口 `GET /admin/credit/users` 返回的 `id` 为零工/老板业务编号（`G`/`B` + 7 位数字）；详情和调分接口 `/admin/credit/{id}`、`/admin/credit/{id}/adjust` 支持该业务编号，也兼容历史数据库数字主键。
详情接口仅返回对应信用类型最近 50 条流水（响应字段 `flowLimit=50`），完整历史通过信用流水分页接口查询，避免大流水账号一次性加载导致超时。

学堂视频上传使用 OSS `academy/*` 目录。当前 RAM 子用户必须具备 `oss:PutObject` 权限；最小权限策略见 `src/main/resources/db/aliyun_oss_upload_policy.json`。权限不足时接口返回 403，不再回传阿里云完整诊断信息。

### 6. 信用分明细分页

`GET /user/{id}/credit/flows?page=0&size=20`

分页返回 `CreditFlow` 列表（`userId`、`delta`、`reason`、`bizType`、`timestamp`）。

### 7. 身份切换

`POST /auth/switch-role?role=BOSS|USER`

切换用户角色，返回新的 JWT `accessToken`。

### 8. 账号注销

`POST /auth/cancel?userId={id}&reason={原因}`

软删除：设置 `User.status=注销`，记录注销原因。

### 9. 提交企业认证

`POST /boss/enterprise-cert`

请求体：`{ "userId": 1, "companyName": "公司名", "industry": "电子厂", "licenseNo": "...", "legalRep": "..." }`

设置 `User.certType=ENTERPRISE`、`certStatus=待审核`。

### 10. 老板资料

`GET /boss/profile/{userId}`

返回老板用户完整信息（含企业字段）。

### 11. 老板账户统计

`GET /boss/profile/{userId}/stats`

返回：`{ "totalOrders": 10, "recruitingCount": 3, "applicantCount": 25, "settledAmount": 5000 }`

### 12. 获取当前零工个人资料

`GET /worker/profile`

从 JWT 获取当前零工 ID，**不接收前端传入的 userId**。返回 `WorkerProfile`（含昵称、头像、手机号、城市、技能、信用分等）。

**错误：** `403 当前登录账号不是零工身份`

### 13. 修改当前零工个人资料

`PUT /worker/profile`

请求体 `UpdateWorkerProfileRequest`（字段可选）：`nickname`、`avatar`、`age`、`gender`、`city`、`skills`、`remark` 等。

> 手机号为**只读字段**，修改手机号须通过手机号认证流程。

返回更新后的 `WorkerProfile`。

### 14. 零工订单列表（聚合查询）

`GET /worker/orders?type=&status=&page=0&size=20`

按当前 JWT 零工查询报名记录及岗位完整信息（**不接受前端传入 userId**）。返回 `WorkerOrder` 分页列表。

**参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `type` | string | 否 | - | 按招工类型过滤：`daily`/`heldBack`/`month` |
| `status` | string | 否 | - | 按报名状态过滤（如 `已报名`/`已录用`/`已到岗`/`已完成`） |
| `page` | int | 否 | `0` | 页码，从 0 开始 |
| `size` | int | 否 | `20` | 每页条数，限制 1~100 |

**错误：** `403 当前登录账号不是零工账号`

---

## 八、老板端招工与人才

### 1. 保存草稿

`POST /boss/order/draft`

请求体：BossOrder 字段。创建 `BossOrder` 且 `orderStatus=草稿`。

### 2. 草稿列表

`GET /boss/orders/drafts?userId={id}`

返回当前用户的草稿订单列表。

### 3. 更新草稿

`PUT /boss/order/{id}/draft`

请求体：BossOrder 更新字段。

### 4. 首页统计

`GET /boss/stats?userId={id}`

返回：`{ "totalOrders": 10, "recruitingCount": 3, "applicantCount": 25, "settledAmount": 5000 }`

### 5. 岗位分类树（公开）

`GET /job-categories/tree`

返回启用的行业、企业类型和工种三级树，均按 `sortNo`、`id` 升序排列。免登录。

返回 `IndustryItem` 列表（每个行业含 `enterpriseTypes`，每个企业类型含 `jobs`）。

### 5a. 热门工种（公开）

`GET /job-categories/hot`

返回启用的热门工种快捷入口。免登录。

### 5b. 搜索工种（公开）

`GET /job-categories/search?keyword=&size=20`

按工种名称或描述关键词模糊搜索。免登录。

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | string | 是 | - | 搜索关键词 |
| `size` | int | 否 | `20` | 返回条数，限制 1~100 |

### 5c. 新增企业类型（老板）

`POST /boss/job-categories/enterprise-types`

在指定行业下新增企业类型；名称在同一行业内不可重复。身份从 JWT 获取，须为老板。

**请求体** `CreateEnterpriseTypeRequest`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `industryId` | long | 是 | 行业 ID |
| `name` | string | 是 | 企业类型名称 |
| `sortNo` | int | 否 | 排序号 |

**错误：** `500 当前登录账号不是有效的老板账号`、`400 industryId 不能为空`

### 5d. 新增工种（老板）

`POST /boss/job-categories/jobs`

在指定行业的企业类型下新增工种；同一企业类型内名称不可重复。身份从 JWT 获取，须为老板。

**请求体** `CreateJobRequest`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `industryId` | long | 是 | 行业 ID |
| `enterpriseTypeId` | long | 是 | 企业类型 ID |
| `name` | string | 是 | 工种名称 |
| `description` | string | 否 | 工种描述 |
| `sortNo` | int | 否 | 排序号 |

**错误：** `500 当前登录账号不是有效的老板账号`、`400 industryId 和 enterpriseTypeId 不能为空`

### 6. 招工地址列表

`GET /boss/addresses?userId={id}`

返回 `BossAddress` 列表（`id`、`name`、`detail`、`lat`、`lng`、`isDefault`）。

### 7. 新增地址

`POST /boss/addresses`

请求体：`{ "userId": 1, "name": "松江厂区", "detail": "上海市松江区...", "lat": 31.0, "lng": 121.2, "isDefault": true }`

### 8. 删除地址

`DELETE /boss/addresses/{id}`

### 9. 设默认地址

`PUT /boss/addresses/{id}/default`

先清空该老板其它默认标记，再置当前地址为默认；地址不存在或非本人返回错误。

### 9a. 编辑地址

`PUT /boss/addresses/{id}`

按 id 局部更新（字段非空才覆盖：`name`/`detail`/`lat`/`lng`/`isDefault`）；`isDefault` 置 `true` 时先清空其它默认；地址不存在或非本人返回错误。

### 9b. 获取默认地址

`GET /boss/addresses/default?userId={id}`

返回老板默认地址；未设置默认时 `data` 为 `null`。

### 9c. 选择当前使用地址

`PUT /boss/addresses/{id}/use`

与默认地址语义统一：清空其它默认后置当前地址为默认（不另设 currentAddress 状态）。

### 9d. 搜索我的地址

`GET /boss/addresses/search?userId={id}&keyword={keyword}`

在已保存地址中按名称/详情忽略大小写模糊匹配；`keyword` 必填；不代替地图 POI 搜索。

**错误：** `400 keyword 不能为空`

### 10. 联系人列表

`GET /boss/contacts?userId={id}`

返回 `BossContact` 列表（`id`、`name`、`phone`、`isDefault`）。

### 11. 新增联系人

`POST /boss/contacts`

请求体：`{ "userId": 1, "name": "张三", "phone": "138...", "isDefault": true }`

### 12. 删除联系人

`DELETE /boss/contacts/{id}`

### 13. 设默认联系人

`PUT /boss/contacts/{id}/default`

### 14. 搜索零工

`GET /talent/search?keyword=&skill=&city=&page=0&size=20`

返回 `User`（role=USER）分页列表。`keyword` 模糊匹配昵称/手机号/用户名；`skill` 匹配技能标签；`city` 匹配城市。

> 注意：`skill`/`city` 过滤在内存中执行，返回的 `total` 为当前页过滤后的实际数量，非全局总数。

### 15. 收藏人才列表

`GET /talent/favorites?bossId={id}`

返回收藏记录列表，每项含收藏 ID 与零工详情：`[{ "favoriteId": 1, "worker": { "id": 2, "nickname": "...", "phone": "...", "skills": "..." } }]`

### 16. 收藏零工

`POST /talent/favorites`

请求体：`{ "bossId": 1, "workerId": 2 }`

### 17. 取消收藏

`DELETE /talent/favorites/{id}`

### 18. 历史合作零工

`GET /talent/history?bossId={id}`

返回历史合作过的零工列表（来自 `BaseOrderItem` 关联 `BossOrder`）。

### 19. 邀请零工

`POST /talent/invite`

请求体：`{ "bossId": 1, "workerId": 2, "orderId": 3 }`

创建 `Message`（type=BOSS_INVITE）通知零工。

### 20. 老板黑名单

`GET /talent/blacklist?bossId={id}`

返回老板名下的黑名单列表。

---

## 九、零工端岗位与订单

### 1a. 零工公开岗位列表

`GET /jobs`

按类型、城市、薪资、标签等条件查询 **招工中** 的公开岗位（不按老板归属过滤）。查询参数同 `BossOrderQuery`（`type`/`city`/`salaryMin`/`salaryMax`/`tag`/`page`/`size` 等）。

返回分页 `BossOrder` 列表（`page` 从 0 开始）。

### 1b. 零工岗位详情

`GET /jobs/{id}`

查询招工中的公开岗位详情；岗位不存在或非「招工中」状态返回 404。

**错误：** `404 岗位不存在: {id}`、`404 岗位不存在或已停止招工`

### 1c. 零工报名岗位

`POST /jobs/{orderId}/apply`

当前登录零工报名公开岗位，**用户身份从 JWT 获取**（不接受前端传入 `userId`）。请求体可选。

**请求体**（可选）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `remark` | string | 否 | 报名备注 |
| `trial` | boolean | 否 | “我要试工”标记，仅月结(`month`)订单可传 `true` |

返回 `BaseOrderItem`。报名状态初始为 `已报名`。

**错误：** `403 当前登录账号不是零工账号`、`400 该订单当前不可报名`、`400 您已报名过该订单`、`400 该订单已招满`、`400 仅月结订单可选择试工`

### 1. 高级筛选岗位

`GET /boss/order/filter?city=&salaryMin=&salaryMax=&tag=&type=&duration=&page=0&size=20`

扩展 `BossOrder` 查询参数，支持城市、薪资范围、标签筛选。

### 2. 月结订单列表

`GET /boss/order/monthly?userId={id}`

返回零工的月结类型订单列表（`BaseOrderItem` 关联 `BossOrder` where `type=month`）。

### 3. 压薪订单列表

`GET /boss/order/press-salary?userId={id}`

返回零工的压薪类型订单列表（`type=heldBack`）。

### 4. 收藏岗位列表

`GET /jobs/favorites?userId={id}`

返回 `JobFavorite` 列表（关联 `BossOrder` 详情）。

### 5. 收藏岗位

`POST /jobs/favorites`

请求体：`{ "userId": 1, "orderId": 2 }`

### 6. 取消收藏

`DELETE /jobs/favorites/{id}`

### 7. 检查收藏状态

`GET /jobs/favorites/check?userId={id}&orderId={id}`

返回：`{ "favorited": true }`

### 8. 浏览记录列表

`GET /jobs/history?userId={id}&page=0&size=20`

返回 `BrowseHistory` 分页列表（关联 `BossOrder` 详情）。

### 9. 记录浏览

`POST /jobs/history`

请求体：`{ "userId": 1, "orderId": 2 }`

### 10. 清空浏览记录

`DELETE /jobs/history?userId={id}`

### 11. 结算详情

`GET /settle/{id}/detail`

返回 `Settlement` 完整信息（含工资构成、工作天数、扣款、实发金额）。

---

## 十、财务系统

### 积分 `/points`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/points/{userId}` | 积分余额 |
| `GET` | `/points/{userId}/flows?page=0&size=20` | 积分明细 |

### 奖励 `/rewards`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/rewards` | 奖励列表 |
| `GET` | `/rewards/{id}` | 奖励详情 |
| `POST` | `/rewards/{id}/exchange?userId=` | 积分兑换 |
| `GET` | `/rewards/exchanges?userId=` | 兑换记录 |

### 优惠券 `/coupons`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/coupons?userId=&status=` | 优惠券列表 |
| `POST` | `/coupons/{id}/claim?userId=` | 领取优惠券 |

### 押金 `/deposits`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/deposits/{userId}` | 押金记录 |
| `POST` | `/deposits` | 缴纳押金（body: `userId`、`amount`） |
| `POST` | `/deposits/{id}/refund` | 申请退还 |

### 邀请 `/invite`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/invite/code?userId=` | 邀请码（自动生成） |
| `GET` | `/invite/poster?userId=` | 海报数据（码+邀请人数+海报 URL） |
| `GET` | `/invite/relations?userId=` | 邀请记录（`InviteRelation` 列表） |

**返回示例**：

- `/invite/code` → `{ "code": "ABC12345" }`
- `/invite/poster` → `{ "code": "ABC12345", "invitedCount": 3, "posterUrl": "" }`

### 子账号 `/boss/sub-accounts`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/boss/sub-accounts?parentId=` | 子账号列表 |
| `POST` | `/boss/sub-accounts` | 创建子账号（同步创建 sys_user） |
| `DELETE` | `/boss/sub-accounts/{id}` | 删除子账号（同步冻结底层用户） |
| `PUT` | `/boss/sub-accounts/{id}/role?role=` | 修改角色（同步更新底层用户 subRole） |

**创建子账号请求体**：

- 方式一：绑定已有用户 `{ "parentId": 1, "userId": 5, "role": "FINANCE" }`
- 方式二：自动新建用户 `{ "parentId": 1, "username": "sub001", "nickname": "财务小王", "phone": "138...", "role": "FINANCE" }`

未传 `userId` 时自动创建 `sys_user`（role=BOSS，parentUserId=parentId，subRole=role，状态正常），并生成随机密码。

### 勋章 `/badges`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/badges` | 勋章目录 |
| `GET` | `/badges/user/{userId}` | 已获得勋章 |

### 星级 `/star-level`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/star-level/{userId}` | 当前星级+进度 |

### 费用/支付明细

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/expenses?userId=&page=0&size=20` | 费用明细（`WalletFlow` 筛选） |
| `GET` | `/payments?userId=&page=0&size=20` | 支付明细（`WalletFlow` 筛选） |

---

## 十一、消息与通知

### 1. 消息详情

`GET /message/{id}`

返回单条 `Message` 完整信息。

### 2. 系统通知

`GET /message/system?userId={id}&page=0&size=20`

返回 `Message`（type=SYSTEM_NOTICE）分页列表。

### 3. 通知设置 - 查询

`GET /notification-settings/{userId}`

返回 `NotificationSetting`（`orderNotif`、`activityNotif`、`systemNotif`、`sound`、`vibrate`）。
不存在时自动创建默认设置。

### 4. 通知设置 - 更新

`PUT /notification-settings/{userId}`

请求体：`NotificationSetting` 字段。

### 5. 未接来电列表

`GET /missed-calls?userId={id}&page=0&size=20`

返回 `MissedCall` 分页列表（`fromUserId`、`callTime`、`duration`、`isRead`）。

### 6. 未接来电标记已读

`PUT /missed-calls/{id}/read`

### 7. 开始客服会话

`POST /chat/sessions`

请求体：`{ "userId": 1 }`

逻辑：若该用户已有 OPEN 状态的会话，直接返回该会话；否则创建新会话（固定 `agentId=1`）。

返回 `ChatSession`（含 `id`、`userId`、`agentId`、`status`）。

### 8. 获取用户会话列表

`GET /chat/sessions?userId=1`

返回该用户的所有会话列表（按时间倒序）。

### 9. 会话消息列表

`GET /chat/sessions/{sessionId}/messages?page=0&size=50`

返回 `ChatMessage` 列表（时间正序，`fromId`、`fromType`、`content`、`contentType`）。

### 10. 发送消息（HTTP 降级）

`POST /chat/sessions/{sessionId}/messages`

请求体：`{ "fromId": 1, "content": "你好" }`

> 优先使用 WebSocket 实时通信，WebSocket 不可用时降级使用此接口。消息会持久化并更新会话时间。

返回 `ChatMessage`。

### 11. 关闭会话

`PUT /chat/sessions/{sessionId}/close`

将会话状态设为 `CLOSED`。

返回更新后的 `ChatSession`。

### 12. 公告列表

`GET /notices?scope=&page=0&size=20`

返回已发布公告列表（按 `scope` 筛选：全部/零工/雇主）。

---

## 十一A、WebSocket 实时通信 `/ws/chat`

> 客服聊天实时通道，支持用户端(USER)与客服端(AGENT)双向消息推送。

### 连接方式

```
ws://{host}/api/ws/chat?token={JWT}&userId={用户ID}&type={USER|AGENT}
```

- `token`：JWT Token（URL 参数传递）
- `userId`：当前用户/客服 ID
- `type`：`USER`（零工/老板）或 `AGENT`（客服）

### 客户端→服务端消息

```json
{
  "type": "JOIN | MESSAGE | TYPING | READ | CLOSE | PING",
  "sessionId": 123,
  "content": "消息内容",
  "contentType": "TEXT | IMAGE"
}
```

| type | 说明 | 额外字段 |
| --- | --- | --- |
| `PING` | 心跳（30秒一次） | 无 |
| `JOIN` | 加入会话 | `sessionId` |
| `MESSAGE` | 发送消息 | `sessionId`、`content`、`contentType` |
| `TYPING` | 正在输入 | `sessionId` |
| `READ` | 消息已读 | `sessionId` |
| `CLOSE` | 关闭会话 | `sessionId` |

### 服务端→客户端推送

```json
{
  "type": "MESSAGE | HISTORY | TYPING | READ | AGENT_JOINED | SESSION_CLOSED | ERROR",
  "sessionId": 123,
  "messageId": 789,
  "content": "消息内容",
  "fromType": "USER | AGENT",
  "fromId": 456,
  "contentType": "TEXT | IMAGE",
  "timestamp": "2026-09-05T10:00:00",
  "message": "错误信息（ERROR 类型时）"
}
```

### 消息持久化

WebSocket `MESSAGE` 类型消息会自动持久化到 `chat_message` 表，并推送双方。`TYPING`/`READ` 仅实时推送不持久化。

### 心跳与重连

- 客户端每 30 秒发送 `PING`，服务端静默处理
- 断线后客户端自动重连，最多 5 次，间隔递增

---

## 十一B、后台客服管理 `/admin/service`

> 客服管理端接口，需 ADMIN 鉴权。

### 1. 客服统计

`GET /admin/service/stats`

返回：
```json
{
  "openSessions": 5,
  "closedSessions": 23,
  "totalQuickReplies": 12,
  "totalFaqs": 8
}
```

### 2. 会话列表（分页）

`GET /admin/service/sessions?status=&page=0&size=20`

- `status`：可选，`OPEN`/`CLOSED`，不传则返回全部
- 按会话更新时间倒序

返回 `ChatSession` 分页列表。

### 3. 会话详情

`GET /admin/service/sessions/{id}`

返回单个 `ChatSession`。

### 4. 会话消息列表

`GET /admin/service/sessions/{id}/messages?page=0&size=50`

返回 `ChatMessage` 分页列表（时间正序）。

### 5. 客服发送消息

`POST /admin/service/sessions/{id}/messages`

请求体：`{ "content": "您好，请问有什么可以帮您？" }`

> 优先使用 WebSocket 发送，此接口为 HTTP 降级方案。`fromType` 固定为 `AGENT`，`fromId` 固定为 `1`。

返回 `ChatMessage`。

### 6. 关闭会话

`PUT /admin/service/sessions/{id}/close`

返回更新后的 `ChatSession`。

### 7. 快捷回复 - 列表

`GET /admin/service/quick-replies`

返回全部 `QuickReply` 列表。

### 8. 快捷回复 - 新增

`POST /admin/service/quick-replies`

请求体：
```json
{
  "content": "您好，客服正在为您查询，请稍候。",
  "category": "通用",
  "sortOrder": 0,
  "enabled": true
}
```

返回创建后的 `QuickReply`。

### 9. 快捷回复 - 更新

`PUT /admin/service/quick-replies/{id}`

请求体同新增，按 id 更新。

返回更新后的 `QuickReply`。

### 10. 快捷回复 - 删除

`DELETE /admin/service/quick-replies/{id}`

### 11. FAQ - 列表

`GET /admin/service/faqs`

返回全部 `Faq` 列表（含未启用）。

### 12. FAQ - 新增

`POST /admin/service/faqs`

请求体：
```json
{
  "question": "提现多久到账？",
  "answer": "一般 1-3 个工作日到账，具体以银行处理时间为准。",
  "category": "钱包",
  "sortOrder": 0,
  "enabled": true
}
```

返回创建后的 `Faq`。

### 13. FAQ - 更新

`PUT /admin/service/faqs/{id}`

请求体同新增，按 id 更新。

返回更新后的 `Faq`。

### 14. FAQ - 删除

`DELETE /admin/service/faqs/{id}`

### 15. FAQ 公开接口（小程序端）

`GET /service/faqs?category=`

> 免登录，仅返回 `enabled=true` 的 FAQ，按 `sortOrder` 正序。

返回 `Faq` 列表。

---

## 十二、学习中心与规则

### 1. 课程大厅

`GET /courses?category=&page=0&size=20`

返回 `Course` 分页列表（`title`、`category`、`coverUrl`、`intro`）。

### 2. 课程详情

`GET /courses/{id}`

返回课程信息与视频列表：`{ "course": { "id": 1, "title": "...", "category": "...", "coverUrl": "...", "intro": "..." }, "videos": [{ "id": 1, "title": "...", "videoUrl": "...", "duration": 600, "sortOrder": 1 }] }`

### 3. 课程视频列表

`GET /courses/{id}/videos`

返回 `CourseVideo` 列表（`title`、`videoUrl`、`duration`、`sortOrder`）。

### 4. 获取考试

`GET /exams/{courseId}`

返回 `Exam` + 关联 `ExamQuestion` 列表（`content`、`options`、`score`）。

### 5. 提交考试

`POST /exams/{courseId}/submit`

请求体：`{ "userId": 1, "answers": { "1": "A", "2": "B" } }`

自动判分，返回 `ExamResult`（`score`、`passed`）。

### 6. 考试结果

`GET /exams/result?userId={id}&examId={id}`

返回 `ExamResult`。

### 7. 培训任务列表

`GET /training-tasks?userId={id}`

返回 `TrainingTask` 列表（`courseId`、`status`、`dueDate`）。

### 8. 分配培训任务

`POST /training-tasks`

请求体：`{ "userId": 1, "courseId": 2 }`

### 9. 完成培训任务

`PUT /training-tasks/{id}/complete`

### 10. 规则列表

`GET /rules?category=`

返回已发布规则列表。

### 11. 规则详情

`GET /rules/{id}`

返回 `Rules` 完整信息。

---

## 十三、保险/合同/社群/客服

### 1. 保险记录列表

`GET /insurance?userId={id}`

返回 `Insurance` 列表（`type`、`amount`、`premium`、`status`、`startTime`、`endTime`）。

### 2. 购买保险

`POST /insurance`

请求体：`{ "userId": 1, "userType": "WORKER", "orderId": 2, "type": "意外险", "amount": 100, "premium": 5 }`

### 3. 保险详情

`GET /insurance/{id}`

### 4. 合同列表

`GET /boss/contracts?bossId={id}`

返回 `Contract` 列表（`bossId`、`workerId`、`orderId`、`status`、`signedAt`）。

### 5. 创建合同

`POST /boss/contracts`

请求体：`{ "bossId": 1, "workerId": 2, "orderId": 3, "content": "..." }`

### 6. 社群列表

`GET /social-groups`

返回 `SocialGroup` 列表（`name`、`category`、`qrcodeUrl`、`memberCount`）。

### 7. 常见问题

| 接口 | 说明 |
| --- | --- |
| `GET /faq?category=` | 旧接口，返回全部 FAQ（含未启用） |
| `GET /service/faqs?category=` | 新公开接口，仅返回 `enabled=true` 的 FAQ，按 `sortOrder` 正序 |

返回 `Faq` 列表（`question`、`answer`、`category`、`sortOrder`、`enabled`）。

---

## 实体补充

### Phase 1 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `CreditFlow` | `credit_flow` | `userId`、`delta`、`reason`、`bizType` |

`User` 扩展字段：`idCard`、`realName`、`licenseNo`、`legalRep`、`parentUserId`、`subRole`、`city`

### Phase 2 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `BossAddress` | `boss_address` | `userId`、`name`、`detail`、`lat`、`lng`、`isDefault` |
| `BossContact` | `boss_contact` | `userId`、`name`、`phone`、`isDefault` |
| `TalentFavorite` | `talent_favorite` | `bossId`、`workerId` |

### Phase 3 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `JobFavorite` | `job_favorite` | `userId`、`orderId` |
| `BrowseHistory` | `browse_history` | `userId`、`orderId`、`viewedAt` |

### Phase 4 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `PointsAccount` | `points_account` | `userId`、`balance` |
| `PointsFlow` | `points_flow` | `userId`、`delta`、`bizType`、`remark` |
| `Reward` | `reward` | `title`、`description`、`pointsCost`、`stock`、`status` |
| `RewardExchange` | `reward_exchange` | `userId`、`rewardId`、`status` |
| `Coupon` | `coupon` | `title`、`type`、`amount`、`minSpend`、`validDays` |
| `UserCoupon` | `user_coupon` | `userId`、`couponId`、`status`、`expireAt` |
| `InviteCode` | `invite_code` | `userId`、`code` |
| `InviteRelation` | `invite_relation` | `inviterId`、`inviteeId`、`rewardStatus` |
| `Deposit` | `deposit` | `userId`、`amount`、`status`、`payTime`、`refundTime` |
| `SubAccount` | `sub_account` | `parentId`、`userId`、`role`、`status` |
| `Badge` | `badge` | `code`、`title`、`iconUrl`、`description`、`rule` |
| `UserBadge` | `user_badge` | `userId`、`badgeId`、`unlockedAt` |
| `UserStarLevel` | `user_star_level` | `userId`、`level`、`progress` |

### Phase 5 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `MissedCall` | `missed_call` | `fromUserId`、`toUserId`、`callTime`、`duration`、`isRead` |
| `ChatSession` | `chat_session` | `userId`、`agentId`、`status` |
| `ChatMessage` | `chat_message` | `sessionId`、`fromId`、`fromType`、`content`、`contentType` |
| `NotificationSetting` | `notification_setting` | `userId`、`orderNotif`、`activityNotif`、`systemNotif`、`sound`、`vibrate` |

### Phase 6 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `Course` | `course` | `title`、`category`、`coverUrl`、`intro`、`status` |
| `CourseVideo` | `course_video` | `courseId`、`title`、`videoUrl`、`duration` |
| `Exam` | `course_exam` | `courseId`、`title`、`passScore` |
| `ExamQuestion` | `course_exam_question` | `examId`、`content`、`options`、`answer`、`score` |
| `ExamResult` | `course_exam_result` | `userId`、`examId`、`score`、`passed` |
| `TrainingTask` | `course_training_task` | `userId`、`courseId`、`status`、`dueDate`、`completedAt` |

### Phase 7 新增实体

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `Insurance` | `insurance` | `userId`、`userType`、`orderId`、`type`、`amount`、`premium`、`status` |
| `Contract` | `contract` | `bossId`、`workerId`、`orderId`、`status`、`signedAt` |
| `SocialGroup` | `social_group` | `name`、`category`、`qrcodeUrl`、`memberCount` |
| `Faq` | `faq` | `question`、`answer`、`category`、`sortOrder`、`enabled` |

### Phase 8 新增实体（IM 客服）

| 实体 | 表名 | 关键字段 |
| --- | --- | --- |
| `QuickReply` | `quick_reply` | `content`、`category`、`sortOrder`、`enabled` |
| `MessageTemplate` | `message_template` | `name`、`event`、`scene`、`channel`、`content`、`status`（enabled/disabled）、`sendWay`、`sendTime`、`freqLimit`、`lastUsed` |

> `ChatSession` 扩展：`agentId`（客服ID，默认 1）
> `ChatMessage` 扩展：`fromType`（USER/AGENT）、`contentType`（TEXT/IMAGE）
