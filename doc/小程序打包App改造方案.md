# 快马日结：小程序打包 App 改造方案

> 版本：v1.0（2026-10-17）
> 状态：待决策（见「一、待决策事项」）
> 适用范围：`kuaima-uniapp`（小程序 / App 共用代码）、`kuaima_backend`（登录、支付、提现相关接口）
>
> 使用方式：动工前先确认第一节的决策；之后按第三、四节逐项完成并勾选；每完成一个阶段，更新第七节的进度表。

---

## 目录

1. 待决策事项
2. 现状与结论
3. 外部资质与账号清单
4. 改造清单（按模块）
5. 测试清单
6. 上架发布清单
7. 排期与进度
8. 风险与应对
9. 附录：涉及文件索引

---

## 一、待决策事项

动工前必须确定以下几项，结论写在「决策结果」一列。「影响阶段」一列标明这项决策最晚要在哪个阶段之前定下来；不影响阶段 1（打出测试安装包）的，可以边开发边定。

| # | 事项 | 可选方案 | 建议 | 影响阶段 | 决策结果 | 决策人 / 日期 |
|---|---|---|---|---|---|---|
| D1 | 首发平台 | 仅 Android ／ Android + iOS 同时 | 先 Android，跑通后再上 iOS | 阶段 1 | **已定：先 Android，跑通后再上 iOS** | 2026-10-17 |
| D2 | App 登录方式 | 短信验证码 ／ 微信登录 ／ 运营商一键登录（收费） | 短信验证码为主，微信登录为辅 | 阶段 2 | **已定：短信验证码；现阶段使用固定验证码（限制见下方说明）** | 2026-10-17 |
| D3 | 小程序和 App 账号是否打通 | 打通（同一账号、同一钱包） ／ 不打通 | 打通 | 阶段 2 | 待定（D2 选短信登录后，按手机号登录天然就会打通，建议确认为「打通」） | |
| D4 | 账号合并依据（D3 选打通时） | 手机号 ／ 微信 unionid ／ 两者都用 | 手机号为主，unionid 作为补充 | 阶段 2 | 待定（建议：手机号） | |
| D5 | App 用户提现方式 | 用 App 的 AppID 转账 ／ 要求先在小程序登录、用小程序 openid 转账 | 用 App 的 AppID 转账 | 阶段 3 | | |
| D6 | 打包方式 | HBuilderX 云打包 ／ Android Studio、Xcode 离线打包 | HBuilderX 云打包（团队已在用 HBuilderX 命令行） | **阶段 1（直接影响打包）** | **已定：HBuilderX 云打包** | 2026-10-17 |
| D7 | 是否接入消息推送 | 接入 uni-push ／ 暂不接入 | 接入，用于报名、录用、结算通知 | 阶段 4（接入时需要重新打包） | | |
| D8 | iOS 积分购买 | 继续用微信支付 ／ 改用苹果内购 ／ iOS 隐藏积分购买 | 上 iOS 前再定（见风险 R3） | 上 iOS 前 | | |

**D2 固定验证码的使用限制（必须遵守）**

固定验证码只是为了在接入正式短信之前方便联调和内部试用，等于「知道手机号就能登录任何人的账号」，所以：

1. 只能在开发、测试环境开启，后端用配置开关控制（例如 `kuaima.auth.sms.fixed-code`），**正式环境默认关闭**，并且正式环境的配置检查里要确认它是关闭的。
2. 固定验证码本身也放在配置里，不写死在代码中，更不能写进前端代码。
3. 开启时，后端启动日志打印一条警告，便于发现误开。
4. **提交应用市场审核前必须换成真实短信**。审核人员会用自己的手机号测试登录；如果用的是测试账号，也只能给审核专用账号单独放行，不能全局开启。
5. 第三节的「短信模板：登录验证码」仍然要提前申请，避免卡住上架。

---

## 二、现状与结论

### 2.1 项目现状

- 技术栈：uni-app 3（Vue 3.4 + Vite 5），CLI 方式构建；目前只有 `h5` 和 `mp-weixin` 两个打包目标。
- `src/manifest.json` 中的 App 配置 `app-plus` **为空**，项目从未按 App 打包过。
- 页面数量：`pages.json` 中约 190 个页面（含分包）。
- 条件编译：12 个文件用到了 `#ifdef MP-WEIXIN`，只有 1 处 `#ifdef APP`。

### 2.2 能力评估

| 能力 | 小程序现状 | App 中的问题 | 改造量 | 对应章节 |
|---|---|---|---|---|
| 登录 | `uni.login` 获取小程序 code，加「手机号快捷登录」按钮 | App 中两者都不可用 | 大（前后端） | 4.2 |
| 用户识别 | 只保存小程序 `openid`，**没有 `unionid`** | 同一个人在两端会变成两个用户 | 大（后端） | 4.3 |
| 微信支付 | JSAPI 支付，6 个页面，全部包在 `#ifdef MP-WEIXIN` 中 | App 端会**跳过支付这一步**；App 支付的接口和参数都不同 | 中（前后端） | 4.4 |
| 提现到微信零钱 | 商家转账，收款方用小程序 openid | App 用户没有小程序 openid | 中（后端 + 商户平台） | 4.5 |
| 定位 / 选点 | `getLocation` / `chooseLocation`，`wx.getPrivacySetting` 隐私授权 | 需要配置地图 SDK；`wx.*` 接口在 App 中不存在 | 小 | 4.6 |
| 图片 / 视频上传 | `chooseImage` / `chooseMedia` / `uploadFile` | 可以直接用，需要声明相机和相册权限 | 很小 | 4.1 |
| 客服聊天 | `uni.connectSocket` | 可以直接用 | 无 | — |
| 实名认证 | `chooseImage` 拍照，后端用阿里云人脸比对 | 可以直接用 | 无 | — |
| 拨打电话 | `uni.makePhoneCall` | 可以直接用 | 无 | — |

**结论**：页面和大部分业务代码可以复用。工作量集中在登录、账号打通、支付、提现这四块，都需要后端配合；外部资质的审核时间最长，需要立即并行办理。

---

## 三、外部资质与账号清单

> 资质办理和开发同时进行。每项都要填写负责人；完成后勾选，并记录关键信息的保存位置（**不要把密钥、证书写进本文档或代码仓库**）。

| ✓ | 事项 | 用途 | 依赖 | 预计耗时 | 负责人 | 进度 / 备注 |
|---|---|---|---|---|---|---|
| [ ] | 确定 App 名称、Android 包名、iOS Bundle ID | 后面所有申请都要用 | — | 1 天 | | **Android 包名已定：`xyz.taifang.ke`**（2026-10-18，按域名 `ke.taifang.xyz` 倒序），已写入 `manifest.json`，上架后不能修改；待定：App 显示名称（是否改为「Dola招工」）；iOS Bundle ID 上 iOS 前再定（建议与包名一致） |
| [x] | 生成 Android 签名证书（keystore） | 打包；App 备案和开放平台都需要签名指纹 | — | 1 天 | | 2026-10-18 已生成，别名 `kuaima`，有效期 100 年；文件和密码暂存在生成人电脑 `~/kuaima-android-signing/`（**待：做两份离线备份并指定保管人**）。公开指纹 SHA1 `70:23:62:56:5F:5A:B9:6F:93:DD:D5:67:51:CF:E4:05:FF:3A:64:C0`；微信开放平台应用签名 `a75d601c33aba292b2167e8019bbca9b`。仓库 `.gitignore` 已禁止提交证书文件 |
| [ ] | App 备案（工信部 ICP） | 国内应用市场上架的硬性要求 | 包名、签名指纹 | 1–3 周 | | 在服务器所在云厂商提交 |
| [ ] | 软件著作权 | 华为、小米等应用市场要求 | — | 1–2 个月（可加急） | | |
| [ ] | 微信开放平台开发者认证 | 创建移动应用的前提 | 企业资质 | 约 1 周 | | 300 元/年 |
| [ ] | 开放平台创建「移动应用」 | App 微信登录、App 支付、`unionid` | 认证、包名、签名 | 约 1 周 | | 拿到 App 的 AppID 和 AppSecret |
| [ ] | 小程序绑定到开放平台 | 让小程序和 App 拿到同一个 `unionid` | 开放平台账号 | 1 天 | | 小程序 AppID：`wx4c163632a2e19ea5` |
| [ ] | 商户号关联 App 的 AppID | App 支付、向 App 用户转账 | 移动应用审核通过 | 1–3 天 | | 微信支付商户平台 → 产品中心 → AppID 账号管理 |
| [ ] | 商家转账：开通转账场景（佣金报酬 1005） | 提现 | — | 1–3 天 | | 详见之前的提现报错处理说明 |
| [ ] | 商家转账：配置接口 IP 白名单 | 提现 | 后端公网出口 IP | 1 天 | | |
| [ ] | 申请地图 Key（高德或腾讯） | App 定位、选点 | 包名、签名（iOS 需要 Bundle ID） | 1 天 | | Android、iOS 要分别申请 |
| [ ] | 短信模板：登录验证码 | App 短信登录 | 短信服务商 | 1–3 天 | | 确认现有短信签名和模板能否用于登录 |
| [ ] | 更新隐私政策、用户协议 | 上架审核 | — | 2–3 天 | | 补充 App 收集的信息、第三方 SDK 清单、注销方式 |
| [ ] | （上 iOS 时）Apple 开发者账号 | iOS 打包、上架 | 企业邓白氏编码 | 1–2 周 | | 688 元/年 |
| [ ] | （上 iOS 时）iOS 证书和描述文件 | iOS 打包 | 开发者账号 | 1 天 | | |
| [ ] | （接入推送时）uni-push 和各厂商推送通道 | 消息推送 | DCloud 账号；华为、小米等厂商开发者账号 | 1–2 周 | | |

---

## 四、改造清单（按模块）

> 每一项都写了**验收标准**，开发自测和测试验收都以此为准。

### 4.1 构建与基础配置

| ✓ | 任务 | 涉及文件 | 验收标准 |
|---|---|---|---|
| [x] | 新增 App 构建依赖和脚本：安装 `@dcloudio/uni-app-plus`；新增 `dev:app`、`build:app`、`build:app:test`、`build:app:prod` | `kuaima-uniapp/package.json` | 本地能生成 App 资源包 ✅ 2026-10-17 已通过 `npm run build:app:test` 验证 |
| [ ] | 补全 `manifest.json` 中的 `app-plus`：应用名称、版本号、图标、启动图 | `src/manifest.json` | HBuilderX 打开项目后 App 配置完整，没有报错。**进度**：基础配置已加；待补：App 显示名称（待确认是否改为「Dola招工」）；图标已提供**模拟图标** `kuaima-uniapp/app-resources/icon-1024.png`（内部测试用，用法见该目录 README），正式图标和启动图上架前替换 |
| [ ] | 配置模块：Payment（微信支付）、OAuth（微信登录）、Geolocation、Maps | `src/manifest.json` | 云打包成功。**进度**：已加 Geolocation、Maps（高德）、VideoPlayer；高德 Key 留空待填；Payment 放到阶段 3 拿到开放平台 AppID 后再加（缺配置会导致云打包失败）；D2 定为短信登录，暂不需要 OAuth |
| [x] | 声明权限：定位、相机、相册、网络；只申请实际用到的权限 | `src/manifest.json` | 应用市场的权限检测不报「多余权限」。已声明网络、定位、相机、读取图片和视频，以及仅 Android 9 及以下申请的写入存储（保存二维码到相册用）；拨打电话只是打开拨号盘，不需要权限 |
| [x] | 新增 App 环境的后端地址；确认正式域名 HTTPS 证书有效 | `src/api/http.js`、`.env.test`、`.env.production` | App 能正常调用接口；证书校验通过。已修复：原来只有小程序使用完整后端地址，App 会落到相对路径 `/api` 导致所有接口失败。**证书和域名仍需在发版前确认**（见风险 R4） |
| [ ] | 新增 App 打包脚本，或者在现有 HBuilderX 命令行脚本里加上 App 打包步骤 | 仓库根目录脚本 | 一条命令生成测试安装包。待：拿到包名和签名证书后补充 |
| [x] | 检查所有 `#ifdef MP-WEIXIN`：App 端是否缺少对应的处理分支 | 见附录 9.1 | 每处都确认过，并写明 App 端的处理方式。结果见附录 9.5 |

### 4.2 登录

**目标**：App 端支持「手机号 + 短信验证码」登录（D2），可选「微信登录」；小程序端保持现有方式。

| ✓ | 任务 | 端 | 涉及文件 | 验收标准 |
|---|---|---|---|---|
| [x] | 后端新增短信登录接口：校验验证码后签发登录令牌，复用现有的令牌和身份（老板 / 零工）逻辑 | 后端 | `controller/auth/AuthController.java` | 正确的验证码能拿到令牌；错误或过期的验证码被拒绝。2026-10-18 完成：`POST /auth/sms/login/send`、`POST /auth/sms/login`；同手机号已有账号则登录该账号，老板端可登录老板授权的员工子账号，否则按零工注册；注销或禁用账号拒绝登录 |
| [x] | 短信发送限流：同一手机号 60 秒 1 次、每天不超过 N 次；同一 IP 也要限流 | 后端 | `AuthController`、短信服务 | 超出频率时被拒绝，并有明确提示。手机号维度沿用原有限制；新增单 IP 每日 30 次；同一验证码输错 5 次即作废 |
| [x] | 现有 `/auth/sms/verify` 会把实名状态改成「已通过」，**短信登录不能沿用这个行为**（短信验证不能等同于实名认证） | 后端 | `AuthController.java` | 短信登录后实名状态不变。短信登录使用独立接口，不调用 `/auth/sms/verify`，已有测试覆盖 |
| [x] | **固定验证码安全限制（D2）** | 后端 | `SmsService.java`、`application-dev.yml`、`application-test.yml` | 复用已有的固定验证码模式（`aliyun.sms.mock-enabled`）；**固定验证码模式下只有白名单测试手机号可以短信登录**（`aliyun.sms.mock-login-phones` / 环境变量 `KUAIMA_SMS_MOCK_LOGIN_PHONES`），白名单为空时拒绝所有短信登录；开启时启动日志打印警告。原因：服务器以 `dev` 配置启动、固定验证码处于开启状态，不加白名单等于任何人可用 `000000` 登录真实用户账号 |
| [ ] | 后端新增 App 微信登录接口：用开放平台 AppID 和 Secret 换取 openid 和 unionid | 后端 | `wechat/service/WechatService.java` | 能拿到 unionid，并按 4.3 的规则关联用户。D2 定为短信登录，暂缓 |
| [x] | 登录页 App 分支：显示手机号、验证码输入框和倒计时；小程序端保持「手机号快捷登录」 | 前端 | `pages/login/login.vue` | 两端各自显示对应的登录方式。App 和 H5 都使用短信登录；已通过编译，待真机验证 |
| [x] | 切换身份页的 App 分支 | 前端 | `pages/worker/switch-identity.vue` | App 端切换身份正常。原代码在 App 中点击后一直停在「切换中」，改为调用 `/auth/switch-role`；待真机验证 |
| [x] | 协议勾选、首次注册选择身份，两端保持一致 | 前端 | `pages/login/login.vue` | 不勾选协议不能登录。短信登录沿用同一个协议勾选和身份选择 |

### 4.3 账号打通与数据迁移（D3、D4）

| ✓ | 任务 | 端 | 验收标准 |
|---|---|---|---|
| [ ] | 用户表新增 `unionid`、`app_openid` 字段，并加索引；附带数据库迁移脚本 | 后端 | 迁移脚本可以重复执行，执行后结果一样 |
| [ ] | 小程序登录时开始保存 `unionid`（前提：小程序已绑定开放平台）；老用户在下次登录时补齐 | 后端 | 老用户再次登录后 unionid 有值 |
| [ ] | 写成文档的合并规则：同一手机号视为同一个人；unionid 相同也视为同一个人；两者冲突时的处理方式 | 产品 + 后端 | 规则写入本文档附录 |
| [ ] | 钱包、订单、实名认证、信用分等全部挂在同一个用户 ID 上，不按端拆分 | 后端 | 在 App 里能看到小程序里的余额和订单 |
| [ ] | 上线前统计：同一手机号对应多个账号的数量，以及处理方案 | 后端 | 有统计结果和处理记录 |

### 4.4 微信支付

**现状**：以下 6 个页面都把 `uni.requestPayment` 包在 `#ifdef MP-WEIXIN` 中，App 端会直接跳过支付，只显示「支付结果确认中」：

`pages/boss/recharge.vue`、`balance.vue`、`points.vue`、`reward-recharge.vue`、`reward.vue`、`settle-confirm.vue`

| ✓ | 任务 | 端 | 涉及文件 | 验收标准 |
|---|---|---|---|---|
| [ ] | 后端新增 App 下单（`/v3/pay/transactions/app`），按 App 的 AppID 下单，返回 App 支付参数（appid、partnerid、prepayid、package、noncestr、timestamp、sign） | 后端 | `wechat/service/WechatPayService.java` | App 能拉起微信支付 |
| [ ] | 下单接口增加「客户端类型」参数，后端根据它选择 JSAPI 还是 App 下单 | 后端 | 各个充值、支付下单接口 | 两端互不影响 |
| [ ] | 支付回调验签继续可用（支付单所属 AppID 不同，验签方式相同） | 后端 | 支付回调接口 | App 支付成功后，订单状态能更新 |
| [ ] | 抽出公共支付方法：内部按端区分（小程序 JSAPI ／ App 的 orderInfo），6 个页面统一调用 | 前端 | 新增 `src/utils/pay.js`；修改上面 6 个页面 | 6 个页面在两端都能完成支付 |
| [ ] | 统一处理「支付已取消」「未安装微信」 | 前端 | `src/utils/pay.js` | 未安装微信时有明确提示 |

### 4.5 提现（D5）

| ✓ | 任务 | 端 | 验收标准 |
|---|---|---|---|
| [ ] | 提现时根据用户在哪个端登录，选择对应的 AppID 和 openid 发起商家转账 | 后端 | App 用户能成功提现到微信零钱 |
| [ ] | 钱包提现和奖励提现分开配置转账场景（工资用佣金报酬 1005，奖励用现金营销 1000），各自带对应的报备信息 | 后端 | 和商户平台已开通的场景一致 |
| [ ] | 核实「用户确认收款」流程：升级版商家转账需要用户在微信内确认，**目前前端没有任何地方调用确认收款**（`requestMerchantTransfer`），需要确认小程序端现在能否完整走通 | 前端 + 后端 | 两端提现都能从发起走到到账 |
| [ ] | App 端拉起微信确认收款页（通过微信开放 SDK） | 前端 | App 用户能完成确认收款 |
| [ ] | 提现失败退回余额的逻辑两端一致 | 后端 | 失败时余额退回，并有站内消息通知 |

### 4.6 定位与隐私授权

| ✓ | 任务 | 涉及文件 | 验收标准 |
|---|---|---|---|
| [x] | `wx.getPrivacySetting`、`wx.requirePrivacyAuthorize` 用条件编译包起来，只在小程序执行 | `src/utils/location.js`、`pages/boss/recruit-address.vue` | App 端不报错。核实：两处原本就先判断接口是否存在，App 中不会调用 |
| [x] | `wx.env` 的用法改用 uni-app 的接口或加条件编译 | `pages/boss/member-invite.vue` | App 端功能正常。已为 App 单独实现「保存邀请二维码到相册」（原生 Bitmap 落盘后保存），原代码在 App 中点击会直接报错 |
| [ ] | App 首次启动弹出隐私政策同意框；用户同意前不初始化定位、推送等第三方 SDK | `src/manifest.json`（`app-plus.privacy`） | 符合应用市场隐私合规检测。**进度**：已启用 uni-app 原生隐私弹窗（同意前不初始化 SDK）；**弹窗中两个协议链接目前是占位地址 `TODO-replace-before-release`，发版前必须换成可访问的网页地址**（后端 `/public/agreements` 只返回 JSON，需要提供网页版协议） |
| [ ] | 定位、选点、打开地图在 App 中验证可用 | 用到定位的页面（附录 9.2） | Android、iOS 都能定位，并能选择招工地址。**进度**：代码已放开（`location.vue`、`recruit-address.vue` 原本在 App 中只提示「请在微信小程序中使用」）；待填高德 Key 后真机验证 |
| [x] | 拒绝定位授权后，给出引导，可以跳转到系统设置 | `pages/boss/recruit-address.vue`、`pages/worker/home.vue` | 拒绝后不影响浏览岗位等其他功能。App 改为调用 `uni.openAppAuthorizeSetting()` 跳转系统权限设置（`uni.openSetting` 只有小程序有）；待真机验证 |

### 4.7 App 特有功能

| ✓ | 任务 | 端 | 验收标准 |
|---|---|---|---|
| [ ] | 版本更新：后端新增版本配置接口（最新版本号、下载地址、是否强制更新、更新说明）；App 启动时检查 | 前后端 | Android 能下载并安装新版本；iOS 跳转 App Store |
| [ ] | （D7）接入 uni-push：登录后上报推送标识；报名、录用、结算提醒等站内消息同时触发推送 | 前后端 | App 在后台时也能收到通知 |
| [ ] | 注销账号入口：后端已有 `/auth/cancel`，确认 App 内有入口和二次确认 | 前端 | 应用市场审核要求 |
| [ ] | Android 返回键：首页按两次退出；弹窗打开时，返回键先关闭弹窗 | 前端 | 交互符合 Android 习惯 |
| [ ] | 状态栏、刘海屏、底部安全区适配 | 前端 | 主要机型显示正常 |

---

## 五、测试清单

### 5.1 机型覆盖

| 系统 | 至少覆盖 |
|---|---|
| Android | 华为（鸿蒙）、小米、OPPO、vivo 各 1 台；Android 10 和最新版本各 1 台 |
| iOS（上 iOS 时） | iPhone 带刘海屏 1 台、非刘海屏 1 台；最新 iOS 和上一个大版本各 1 台 |

### 5.2 核心流程（两端都要测）

- [ ] 首次安装：隐私政策弹窗 → 拒绝 → 同意 → 授权定位
- [ ] 短信登录、微信登录、退出、切换身份（老板 / 零工）
- [ ] 同一个人先在小程序登录、再在 App 登录：看到的是同一个账号、同一份钱包
- [ ] 老板：实名认证 → 企业认证 → 发布岗位（含图片和视频）→ 录用 → 结算支付
- [ ] 零工：浏览岗位 → 实名认证 → 报名 → 到岗打卡 → 收到工资 → 提现到微信零钱
- [ ] 6 个支付页面：支付成功、取消支付、未安装微信
- [ ] 客服聊天：收发消息；App 切到后台再回来，连接能恢复
- [ ] 弱网、断网时的提示，以及恢复后能重试
- [ ] 版本更新：可选更新、强制更新
- [ ] 推送通知（D7）：App 在前台、后台、被杀掉三种情况
- [ ] 注销账号

### 5.3 回归

- [ ] 小程序端以上流程全部回归，确认条件编译没有影响小程序

---

## 六、上架发布清单

- [ ] 第三节所有必需的资质已完成
- [ ] 正式包使用正式签名证书；版本号和版本说明已更新
- [ ] 隐私政策、用户协议链接可以访问，内容与 App 实际行为一致
- [ ] 应用市场素材：图标、截图（各尺寸）、简介、隐私政策链接、测试账号
- [ ] 提审：华为、小米、OPPO、vivo、应用宝（各家审核规则略有不同，被驳回原因记录到附录）
- [ ] （上 iOS 时）App Store Connect：截图、审核说明（说明「余额充值」用于支付零工工资，属于实际服务）、测试账号
- [ ] 发布后观察：崩溃率、登录成功率、支付成功率、提现成功率

---

## 七、排期与进度

> 按 1 名前端加 1 名后端估算，不包括资质审核时间。资质申请在阶段 0 同时提交。

| 阶段 | 内容 | 预估 | 负责人 | 计划完成 | 实际完成 | 状态 |
|---|---|---|---|---|---|---|
| 0 | 第一节决策；第三节资质全部提交 | 1–8 周（审核时间） | | | | 未开始 |
| 1 | 4.1 构建配置、4.6 定位与隐私授权：Android 真机能打开并浏览 | 3–5 天 | | | | 进行中：代码改造完成，等包名、高德 Key、图标、协议网页地址后云打包真机验证 |
| 2 | 4.2 登录、4.3 账号打通 | 5–7 天 | | | | 进行中：4.2 短信登录已完成（待部署后端、配置测试手机号后真机验证）；4.3 按手机号登录天然打通，unionid 部分随 App 微信登录暂缓 |
| 3 | 4.4 支付、4.5 提现 | 5–7 天 | | | | 未开始 |
| 4 | 4.7 App 特有功能 | 3–5 天 | | | | 未开始 |
| 5 | 第五节测试、第六节上架 | 1–2 周 | | | | 未开始 |

阶段 1 完成后，就可以给内部人员发测试安装包试用。

---

## 八、风险与应对

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | 资质审核时间不可控（App 备案、软著） | 上架延期 | 第一周内全部提交；开发不等资质，先用测试包内部试用 |
| R2 | 账号合并规则不清楚，一个人出现两个账号、两份钱包 | 资金纠纷 | 先定规则（4.3）；上线前统计存量冲突，并逐一处理 |
| R3 | iOS 审核要求积分购买改用苹果内购 | iOS 上架受阻，或者被抽成 15%–30% | 上 iOS 前做决策 D8；准备审核说明 |
| R4 | 后端正式域名、证书不稳定（之前出现过 `ke.taifang.xyz` 解析丢失、`kuaima.aesou.com` 证书报错） | App 无法使用，而且发版后不能像小程序那样马上修好 | 发版前确认域名和证书；后端地址做成可以远程下发的配置 |
| R5 | 签名证书丢失 | App 永远无法更新 | 至少两处离线备份，并指定保管人 |
| R6 | 条件编译改漏，影响小程序 | 线上小程序出问题 | 每个阶段都回归小程序（5.3） |
| R7 | 提现流程本身就不完整（4.5 中「确认收款」未实现） | 两端提现都可能失败 | 阶段 3 一开始先核实，必要时先修小程序端 |

---

## 九、附录：涉及文件索引

> 路径相对于 `kuaima-uniapp/src/`；改造时以实际代码为准，新发现的文件请补充到这里。

### 9.1 使用 `#ifdef MP-WEIXIN` 的文件（12 个）

- `api/http.js`
- `pages/login/login.vue`
- `pages/worker/switch-identity.vue`
- `pages/boss/home.vue`、`order.vue`、`location.vue`、`recruit-address.vue`
- `pages/boss/recharge.vue`、`balance.vue`、`points.vue`、`reward.vue`、`reward-recharge.vue`

### 9.2 小程序专用接口的用法

| 接口 | 文件 |
|---|---|
| `uni.login`（小程序 code） | `pages/login/login.vue`、`pages/worker/switch-identity.vue` |
| `open-type="getPhoneNumber"` | `pages/login/login.vue` |
| `uni.requestPayment`（JSAPI） | `pages/boss/recharge.vue`、`balance.vue`、`points.vue`、`reward-recharge.vue`、`reward.vue`、`settle-confirm.vue` |
| `wx.getPrivacySetting` / `wx.requirePrivacyAuthorize` | `utils/location.js`、`pages/boss/recruit-address.vue` |
| `wx.env` | `pages/boss/member-invite.vue` |
| `uni.getLocation` / `chooseLocation` / `openLocation` | `utils/location.js`、`pages/boss/add-address.vue`、`home.vue`、`location.vue`、`order.vue`、`recruit-address.vue`、`pages/worker/home.vue` |

### 9.3 后端相关位置（`kuaima_backend/src/main/java/com/kuaima/app/`）

| 内容 | 文件 |
|---|---|
| 微信登录、手机号授权 | `controller/auth/AuthController.java`、`wechat/service/WechatService.java` |
| 短信发送、校验 | `controller/auth/AuthController.java`（`/auth/sms/send`、`/auth/sms/verify`） |
| 微信支付下单（JSAPI / Native） | `wechat/service/WechatPayService.java` |
| 商家转账 | `wechat/service/WechatMerchantTransferClientImpl.java`、`domain/wallet/service/WorkerWalletWechatWithdrawalService.java`、`domain/reward/service/*` |
| 用户表 | `domain/user/entity/User.java`（目前只有 `openid`） |
| 微信相关配置 | `src/main/resources/application.yml`（`wechat.*`、`wechat.pay.*`） |

### 9.5 条件编译逐项核对结果（阶段 1，2026-10-17）

| 文件 | 小程序专用代码做什么 | App 端处理 | 状态 |
|---|---|---|---|
| `api/http.js` | 后端完整地址 | 改为小程序和 App 共用完整地址 | 已改 |
| `pages/boss/home.vue`、`order.vue` | 计算小程序右上角胶囊按钮的位置 | App 不需要 | 无需改 |
| `pages/boss/location.vue` | 地图选点 | 放开给 App；H5 仍提示不支持 | 已改，待填高德 Key 后验证 |
| `pages/boss/recruit-address.vue` | 隐私授权、定位授权、地图选点 | App 直接打开地图选点；拒绝授权时跳转系统权限设置 | 已改，待验证 |
| `pages/boss/member-invite.vue` | 用 `wx.env` 保存二维码 | App 单独实现保存到相册 | 已改，待验证 |
| 11 个页面的模拟胶囊按钮（`applicants`、`member-apply-list`、`member-detail`、`member-edit`、`member-exit`、`member-invite`、`members`、`resume-import`、`resume`、`sign-code`、`workbench`） | 原条件是「非小程序都显示」，App 会出现一个点了没反应的「⋯」按钮 | 改为只在 H5 显示 | 已改 |
| `pages/worker/home.vue` | 定位被拒后跳转设置 | App 跳转系统权限设置 | 已改，待验证 |
| `pages/login/login.vue`、`pages/worker/switch-identity.vue` | 微信登录、手机号快捷登录；App 端目前提示「请在微信小程序中…」 | 阶段 2（短信登录）处理 | **阶段 2** |
| `pages/boss/recharge.vue`、`balance.vue`、`points.vue`、`reward.vue`、`reward-recharge.vue`、`settle-confirm.vue` | 微信 JSAPI 支付；App 端目前提示「请在微信小程序内完成支付」 | 阶段 3（App 支付）处理 | **阶段 3** |

> 阶段 1 完成后，App 已经可以浏览、定位、选点；**登录要等阶段 2 完成**。在此之前，测试包只能看到登录页，无法登录进入业务页面。

### 9.4 变更记录

| 日期 | 版本 | 修改内容 | 修改人 |
|---|---|---|---|
| 2026-10-17 | v1.0 | 初稿 | |
| 2026-10-17 | v1.1 | 确定 D1（先 Android）、D2（短信验证码，现阶段固定验证码）；决策表增加「影响阶段」一列；补充固定验证码使用限制 | |
| 2026-10-17 | v1.2 | 确定 D6（HBuilderX 云打包） | |
| 2026-10-17 | v1.3 | 阶段 1 代码改造：App 构建依赖和脚本、`app-plus` 配置、原生隐私弹窗、后端地址修复、定位选点放开、二维码保存、模拟胶囊按钮限定 H5；新增附录 9.5 | |
| 2026-10-18 | v1.4 | 内部测试包使用模拟图标和默认启动图；协议链接暂用占位地址（不影响内部测试，上架前必须替换） | |
| 2026-10-18 | v1.5 | 生成 Android 签名证书；确定 Android 包名 `xyz.taifang.ke` | |
| 2026-10-18 | v1.6 | 阶段 2：App / H5 短信验证码登录、固定验证码测试手机号白名单、IP 限流、验证码错误次数限制、App 切换身份 | |
