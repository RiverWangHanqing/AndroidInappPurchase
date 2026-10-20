# Google Play BillingResult 返回数据整理

- 整理日期：2026-10-20
- SDK 核对版本：Google Play Billing Library 9.1.0
- 范围：`responseCode`、购买子状态码和 `debugMessage` 的定义、已知组合及版本差异。
- 证据类型：Google 官方文档、官方 SDK 制品中的实现、社区公开原始日志。

> 三个字段不是一一对应关系。同一条消息可能对应不同主错误码；失败结果的消息也可能为空。本文是已知组合的整理，不是 Google 服务端全部返回内容的穷举。SDK 内置结果属于特定版本的实现证据，社区日志属于特定环境的观察结果，均不代表未来版本不变的协议。

## 1. 字段及读取方式

| 字段 | Java getter | Kotlin 属性 | 含义 |
|---|---|---|---|
| 主状态码 | `getResponseCode()` | `responseCode` | 操作的主要结果 |
| 购买子状态码 | `getOnPurchasesUpdatedSubResponseCode()` | `onPurchasesUpdatedSubResponseCode` | 部分购买失败场景的进一步说明 |
| 调试消息 | `getDebugMessage()` | `debugMessage` | 补充诊断文本，可能为空 |

本文表格中的 `subResponseCode` 是购买子状态码的简称，并非实际 getter 名称。

官方 API 将子状态定义为对 `onPurchasesUpdated()` 错误的进一步说明；8.0 发布说明也提到了 `launchBillingFlow()` 返回结果中的子状态字段。接入时应保留相应入口实际返回的字段，不根据入口名称自行推断值。

来源：[BillingResult API](https://developer.android.com/reference/com/android/billingclient/api/BillingResult)、[购买子状态码 API](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.OnPurchasesUpdatedSubResponseCode)、[8.0 发布说明](https://developer.android.com/google/play/billing/release-notes#8-0-0)。

## 2. 官方定义的 responseCode

共 13 个，包括已经废弃的超时码。

| 数值 | 名称 | 含义及版本说明 |
|---:|---|---|
| -3 | `SERVICE_TIMEOUT` | 服务处理超时；6.0 起不再返回，改用 `SERVICE_UNAVAILABLE` |
| -2 | `FEATURE_NOT_SUPPORTED` | 当前 Play Store 不支持请求的功能 |
| -1 | `SERVICE_DISCONNECTED` | Billing 服务连接已断开 |
| 0 | `OK` | 操作成功 |
| 1 | `USER_CANCELED` | 用户取消购买流程 |
| 2 | `SERVICE_UNAVAILABLE` | 服务暂时不可用，也包括处理超时 |
| 3 | `BILLING_UNAVAILABLE` | 当前设备、用户或环境无法使用 Billing |
| 4 | `ITEM_UNAVAILABLE` | 请求的商品无法购买 |
| 5 | `DEVELOPER_ERROR` | API 使用、参数或配置错误 |
| 6 | `ERROR` | 执行操作期间发生内部错误 |
| 7 | `ITEM_ALREADY_OWNED` | 商品已经拥有 |
| 8 | `ITEM_NOT_OWNED` | 尝试操作的商品并未拥有 |
| 12 | `NETWORK_ERROR` | 网络连接错误；6.0 起新增 |

来源：[BillingResponseCode API](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.BillingResponseCode)、[官方错误处理指南](https://developer.android.com/google/play/billing/errors)。

## 3. 官方定义的 subResponseCode

目前共 3 个。

| 数值 | 名称 | 含义 |
|---:|---|---|
| 0 | `NO_APPLICABLE_SUB_RESPONSE_CODE` | 默认值，没有适用的子状态 |
| 1 | `PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS` | 用户资金不足 |
| 2 | `USER_INELIGIBLE` | 用户不符合订阅优惠的资格要求 |

Google 没有给出所有子状态与主错误码、消息的固定配对表，不能直接假定子码 1 或 2 必然对应某个主码和固定消息。

来源：[OnPurchasesUpdatedSubResponseCode API](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.OnPurchasesUpdatedSubResponseCode)。

## 4. Billing 9.1.0 SDK 中核对到的组合

### 4.1 证据范围

只读检查了本地缓存的官方制品 `com.android.billingclient:billing:9.1.0`，通过 JVM 字节码和临时诊断程序核对结果字段，没有构建或修改项目。

- 在内部类 `com.android.billingclient.api.zzdh` 中核对到 37 个预构造 `BillingResult`，子状态均为 0。
- 另外检查了 `ProxyBillingActivity` 的被阻止处理路径，确认 `(3, 0, "Play Store is blocked.")`。
- 下表保留原始拼写和标点。
- 内部类名称可能随版本变化；预构造结果清单不等于全部运行时返回结果。

制品入口：[Google Maven — Billing 9.1.0](https://maven.google.com/web/index.html#com.android.billingclient:billing:9.1.0)。以下字符串来自本地制品核对，该网页本身没有列出这些内部常量。

### 4.2 通用结果及 Play Store 被阻止结果

下表包含 15 个通用预构造结果，以及单独核对的被阻止结果。

| responseCode | subResponseCode | debugMessage 原文 |
|---:|---:|---|
| 0 | 0 | `""` |
| -1 | 0 | `Service connection is disconnected.` |
| 2 | 0 | `Billing service unavailable on device.` |
| 2 | 0 | `Timeout communicating with service.` |
| 3 | 0 | `Billing service unavailable on device.` |
| 3 | 0 | `Google Play In-app Billing API version is less than 9` |
| 3 | 0 | `Play Store is blocked.` |
| 4 | 0 | `Item is unavailable for purchase.` |
| 5 | 0 | `Client is already in the process of connecting to billing service.` |
| 5 | 0 | `Product type can't be empty.` |
| 5 | 0 | `Invalid purchase token.` |
| 5 | 0 | `Unknown feature` |
| 5 | 0 | `To use this API you must specify a PurchasesUpdateListener when initializing a BillingClient.` |
| 5 | 0 | `A DeveloperProvidedBillingListener must be provided when initializing the BillingClient in order to use multiple payment options for this billing program.` |
| 6 | 0 | `An internal error occurred.` |
| 6 | 0 | `An error occurred while retrieving billing override.` |

注意：

- `Billing service unavailable on device.` 同时存在于主码 2 和 3 的内置结果中。
- SDK 9.1.0 中被阻止消息为 `Play Store is blocked.`，末尾带句号；官方发布说明的描述没有体现该标点。
- 这些预构造结果的子状态为 0，不能据此推断服务端返回的所有结果也都是 0。

### 4.3 功能不支持结果

另外 22 个预构造结果都属于 `responseCode = -2`、`subResponseCode = 0`，消息如下。

| debugMessage 原文 |
|---|
| `Client does not support extra params.` |
| `Client does not support subscriptions.` |
| `Client does not support subscriptions update.` |
| `Client does not support price change confirmation.` |
| `Client does not support multi-item purchases.` |
| `Client does not support offer_id_token.` |
| `Client does not support ProductDetails.` |
| `Client does not support in-app messages.` |
| `Play Store version installed does not support cross selling products.` |
| `Play Store version installed does not support gift code purchase.` |
| `Play Store version installed does not support external offer.` |
| `Play Store version installed does not support multi-item purchases with season pass in one cart.` |
| `Play Store version installed does not support querying AutoPay plan purchase.` |
| `Play Store version installed does not support including suspended subscriptions.` |
| `Play Store version installed does not support get billing config.` |
| `Query product details with serialized docid is not supported.` |
| `Query product details with developer specified account is not supported.` |
| `Play Store version installed does not support alternative billing only.` |
| `Play Store version installed does not support the provided billing program.` |
| `Play Store version installed does not support launching external links.` |
| `Play Store version installed does not support show billing program information dialog.` |
| `Play Store version installed does not support get billing choice info.` |

上述清单包括内部保留的能力检查结果，不能仅凭字符串断言每项都能由当前公开 API 触发。

## 5. 官方记录的版本差异

| 版本 | 场景 | 已确认的变化 |
|---|---|---|
| 6.0 起 | 服务处理超时 | 主码由 `-3` 改为 `2` |
| 6.0 起 | 网络连接错误 | 新增主码 `12`，此前归入 `2` |
| 8.0 起 | 购买失败的进一步分类 | 新增购买子状态字段 |
| 8.3 起 | 跳转外部支付应用后放弃交易、未返回购买流程 | 主码 `1`，消息 `BILLING_ACTIVITY_PAUSED`；此前主码为 `6`，子状态未说明 |
| 9.0 起 | Play Store Activity 被系统阻止，例如 OEM 儿童模式 | 主码由 `6` 改为 `3`，增加 blocked 消息；此能力要求 AndroidX Core ≥ 1.9 |

来源：[Google Play Billing Library 官方发布说明](https://developer.android.com/google/play/billing/release-notes)。

其中，被阻止场景的 `(3, 0, "Play Store is blocked.")` 已通过本地 9.1.0 SDK 进一步核对。对于旧版同场景，发布说明确认了原主码为 6，但不能据此推断旧版也返回相同消息。

## 6. 社区公开原始日志中的组合

“未记录”表示帖子没有提供子状态；部分日志来自该字段出现之前的版本。不得将缺失值自行补成 0。第三方 SDK 的封装错误信息也不等于 Google 的 `debugMessage`。

| responseCode | subResponseCode | debugMessage | 场景与来源 |
|---:|---|---|---|
| 6 | 未记录 | `An internal error occurred.` | 2024 年商品详情查询失败，[Stack Overflow 原帖](https://stackoverflow.com/questions/78834570/billingclient-queryproductdetailsasync-returns-error-statuses-2-6-12-many) |
| 12 | 未记录 | `An internal error occurred.` | 同一原帖报告的网络错误 |
| 2 | 未记录 | `Timeout communicating with service.` | 同一原帖报告的服务超时 |
| -3 | 旧版未提供 | `Timeout communicating with service.` | 2022 年，Billing 4.1.0 购买超时，[Stack Overflow 原帖](https://stackoverflow.com/questions/71577289/google-play-billing-lib-timeout-communicating-with-service-when-subscribing) |
| 3 | 旧版未提供 | `Google Play In-app Billing API version is less than 3` | 2020 年建立 Billing 连接失败，[Stack Overflow 原帖](https://stackoverflow.com/questions/64631501/google-play-in-app-billing-api-version-is-less-than-3-with-updated-play-store) |
| 8 | 未记录 | `Item is not owned by the user.` | 2023 年恢复购买流程中的日志，[RevenueCat 社区](https://community.revenuecat.com/sdks-51/google-error-when-restoring-purchase-2777) |
| 1 | 未记录 | `""` | 2025 年 Android 实体设备取消购买，[RevenueCat 社区](https://community.revenuecat.com/sdks-51/platformexception-on-cancelled-purchase-android-physical-device-6104) |
| 0 | 旧版未提供 | `""` | 2019 年购买回调，[react-native-iap 原始 issue](https://github.com/hyochan/react-native-iap/issues/888) |
| 5 | 旧版未提供 | `""` | 同一 issue 中的开发者错误 |
| 7 | 旧版未提供 | `""` | 同一 issue 中的已拥有商品错误 |

从这些日志能够确认：

- `An internal error occurred.` 至少被观察到搭配主码 6、12。
- 超时消息在旧版搭配 -3，新版搭配 2。
- 失败时 `debugMessage` 可以为空。
- 诸如 `E_ALREADY_OWNED`、`Purchase was cancelled.` 可能来自第三方 SDK 的封装字段，不能当作 Google 原始状态码或消息。
- 这些案例证明组合曾被观察到，不代表帖子中对根因的推测已经得到验证。

### 非零子状态的补充资料

[RevenueCat 的 Google Play 手册第 93 页](https://www.revenuecat.com/documents/google-play-handbook#page=93)将资金不足描述为主码 `ERROR(6)`、子码 `PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS(1)`。

这可作为参考和验证候选，但该资料没有提供固定 `debugMessage`；Google API 参考也没有把这一配对声明为完整、固定的规则。对于子码 `USER_INELIGIBLE(2)`，本次没有找到足够可靠的原始日志来确认固定的三字段组合。

## 7. 对 IAP 接口设计和日志采集的影响

以下是基于整理结果的建议，不表示已经实施接口修改。

1. **原样保留三个字段。** 只增加 `debugMessage` 仍会丢失资金不足、优惠资格等结构化原因。
2. **保留原始整数码。** 即使映射到 `IAPResultCode`，也应保留 Google 原始主码和子码，以兼容后续新增状态。
3. **用数字码处理通用逻辑，用消息补充诊断。** `debugMessage` 没有完整枚举，不适合建立穷尽的字符串映射，也不宜直接作为面向用户的提示。
4. **分别记录三个 getter。** 本地 9.1.0 的 `BillingResult.toString()` 没有输出子状态，仅记录该字符串会漏数据。
5. **区分状态层级。** `UnfetchedProduct.statusCode` 是商品级状态，项目的 `PartialSuccess` 是封装层状态，它们都不是购买子状态码。
6. **采集版本和调用入口。** 同一场景会随 Billing Library 版本改变主码；日志应能区分连接、商品查询、购买启动、购买回调、确认和消耗等入口。

对于本项目关注的 Play Store 被阻止场景，9.1.0 已核对到的数据为：

```text
responseCode = 3  // BILLING_UNAVAILABLE
onPurchasesUpdatedSubResponseCode = 0
debugMessage = "Play Store is blocked."
```

`BILLING_UNAVAILABLE` 还覆盖其他不可用场景，因此只返回映射后的主状态会丢失 blocked 这一具体原因。

## 8. 验证边界

- 本次为文档、公开日志及本地 SDK 的只读核对，没有修改项目代码。
- 没有将全部组合通过真机复现。
- SDK 内部固定消息之外，Play Store 或服务端仍可能返回其他消息。
- 未找到可靠证据的主码、子码、消息配对保持未知，不根据语义补齐。
