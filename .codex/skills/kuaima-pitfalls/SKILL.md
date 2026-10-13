---
name: kuaima-pitfalls
description: 快马项目高频踩坑清单。涉及 OSS 上传、私有文件访问、数据库加列或唯一索引变更、业务状态多表同步、列表/详情字段展示时先读取，避免重复已知错误。纯新业务功能且不涉及上述范围时不必使用。
---

 # 快马项目防重复错误

 本清单来自项目实际反复发生的故障，修改相关链路前逐条对照，完成后按验证节执行。

 ## OSS 上传与私有文件（最高频，已发生 4 次）

 Bucket `resume-storage-20260923` 为私有读写，任何"上传后打不开/无法预览"基本都源于此。

 1. **新增上传功能前先查目录授权**：读取 `kuaima_backend/src/main/resources/db/aliyun_oss_upload_policy.json`，当前已授权前缀只有 `academy/*`、`enterprise/license/*`、`insurance/*`。新上传目录必须落在已授权前缀下（推荐 `insurance/<业务名>/`），否则先请用户在阿里云 RAM 添加资源并更新该 JSON，再写代码。
 2. **上传接口返回两份地址**：`objectKey` 用于落库和提交，`url` 必须是 `ossStorageService.playableUrl(objectKey)` 生成的限时签名地址用于页面回显。绝不把 `objectUrl()` 的原始地址直接给前端展示。
 3. **数据库和提交参数只存 objectKey**：带 `Expires/Signature` 的签名地址过期后失效，落库即脏数据。前端上传后保存 objectKey，提交业务时传 objectKey；后端接收端同时兼容 objectKey 与完整 URL。
 4. **任何展示出参都要签名**：列表/详情接口返回图片字段前调用 `playableUrl()`；给第三方服务（如人脸比对）传图前同样先签名。
 5. **不要改坏 `OssStorageService.objectKey()`**：它必须同时接受相对对象键（`insurance/realname/a.png`）和完整 URL；相对键不能走 `path.substring(1)`（会截掉首字符）。改动后运行 `OssStorageServiceObjectKeyTests`。

 ## 数据库与状态一致性

 6. **业务状态多副本必须同步**：写接口修改一个状态前，先列出该状态的所有存储位置并全部更新。已知副本：企业认证状态 = `sys_user.enterprise_status/cert_status` + `admin_certification.status`；只改一处就是"操作后页面仍显示旧状态"。
 7. **实体加 NOT NULL 列必须配回填 SQL**：Hibernate `ddl-auto=update` 加列不带默认值，MySQL 给存量行填 0/false，会破坏业务（如 `allow_login=0` 锁死超管）。必须同时提交 `db/` 下幂等回填 SQL 并提醒用户在每个环境执行一次。
 8. **唯一索引变更要删旧建新**：`ddl-auto=update` 只加新索引不删旧索引。参照 `db/reward_account_user_role_index.sql` 写幂等 SQL（information_schema 判断 + PREPARE）。
 9. **实名口径**：已实名 = 人脸比对通过（`realnameStatus=APPROVED`）且身份证照、人脸照两份 objectKey 齐全；旧流程（姓名/证号人工审核）的 APPROVED 无照片一律视为未认证。发布招工资格用同一口径。

 ## 前端与进程

0. **状态/枚举字面量必须以常量类为准**：统计或筛选业务状态时，先读对应常量类（如 `BossStatus`）再写状态列表，禁止凭印象写字面量。已知坑：报名有效状态是 `已报名/已录用/已到岗/待结算/已完成`，写错会导致统计恒为 0 且无报错。
10. **移除拦截/校验必须全局搜索整条链路**：小程序业务多为多页流程（如发布招工 = home → select-job → publish-info → recruit-demand），同一校验可能散落多个页面。删除时必须 `rg` 全局搜索校验函数名和跳转目标（如 `checkBossPublishEligibility`、`boss/realname`）确认零残留，而不是只改用户报错的那个页面；验证输出列出所有曾匹配文件。
11. **列表/详情字段先判空**：日期类函数第一行 `if (!value) return '-'`，严禁对可能为 null 的时间直接 `getFullYear()`；金额/文本展示统一 `xx ?? '-'` 兜底。
12. **改完必须重启对应进程再让用户验证**：后端改动重启 jar（报 `No static resource` 优先怀疑旧进程）；uniapp 改静态资源、移动文件或让组件新增依赖模块后重启 dev 并按需清 `node_modules/.vite`。微信端报 `module 'xxx.js' is not defined` 但源码/产物文件存在时，优先怀疑旧增量包未注册新模块，重启 dev 重新生成 `dist/dev/mp-weixin` 并在微信开发者工具清缓存编译。
13. **"本地正常、用户不行"优先怀疑版本未更新**：小程序改动需重新 build → 开发者工具上传 → 后台设为体验版/提审，用户端删小程序重进；确认版本链路后再查代码。
14. **权限按钮**：页面操作按钮接 `v-permission="'模块.页面.按钮'"`，key 必须与 AdminUserForm 三层权限树一致；路由/侧边栏用页面级 key，新旧 key 映射在 `admin-web/src/utils/permission.js` 维护。

 ## 验证

 - 涉及上传/文件访问：跑受影响模块测试 + `mvnw -DskipTests compile`；前端跑对应 `npm run build`；可用 curl 实测运行中后端的列表/详情出参是否带签名 URL。
 - 涉及状态同步：为每个存储副本写断言（通过/拒绝两条路径）。
 - 涉及 SQL：确认幂等（重复执行不报错、不改坏数据），并在交付说明列出需要用户执行的环境。
