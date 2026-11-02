# AndroidInappPurchase
AndroidInappPurchase 是一个提供统一接口，便于集成 Android 不同平台内购功能的开源库。目前支持集成 Google Play 和 Amazon 的应用内购。

## 集成指南
### 安装引入
在 App 模块的 build.gradle 文件里添加：

```gradle
dependencies {
    implementation 'com.github.RiverWangHanqing:AndroidInappPurchase:1.0.4.3'
}
```

### 内购平台 SDK 的引入
无需引入所有内购平台的 SDK，可以按需引入：

  1. 需要集成 Google Play 内购功能时，在 App 模块的 build.gradle 文件里添加:

  ```gradle
  dependencies {
      implementation 'com.android.billingclient:billing:9.1.0'
  }
  ```

  Billing 9.1.0 要求 `minSdk >= 23`；其传递依赖 `androidx.core:core:1.15.0` 要求接入方使用 `compileSdk >= 35`。

  2. 需要集成 Amazon 内购功能时，将 in-app-purchasing-2.0.76.jar 文件拷贝到 App 模块的 libs 文件夹下，并在 App 模块的 build.gradle 文件里添加:
  
  ```gradle
  dependencies {
      api files('libs/in-app-purchasing-2.0.76.jar')
  }
  ```

### 快速上手
#### IAPManager 对象的创建
`IAPManager` 有两个实现类：

  1.Google Play 内购平台的实现类 `IAPManagerGooglePlay`；
  2.Amazon 内购平台的实现类 `IAPManagerAmazon`。

  * `IAPManager` 提供了 `fun preferredIAPManager(context: Context): IAPManager?` 接口来根据应用的安装来源和内购平台 App 的安装情况，自动选择最佳的内购平台实现类。**使用该方式创建 `IAPManager` 对象时，需要引入所有本库支持的内购平台 SDK。**

  * 如果仅需引入单个内购平台 SDK 时，可以直接创建对应内购平台的实现类来创建 `IAPManager` 对象。

#### IAPManager 对象的保存
`IAPManager` 对象可以作为全局单例对象使用，也可以仅在某个 `Activity` 内使用。

  1. `IAPManager` 对象作为全局单例对象使用时，可以保存在 `Application` 里，也可以保存在自定义单例对象里：

  ```kotlin
  override fun onCreate() {
      super.onCreate()
      sIapManager = preferredIAPManager(this)
      //……
  }
  ```

  2. 仅在某个 `Activity` 内使用时，用完可以立即释放 `IAPManager` 对象：

  ```kotlin
  override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      iapManager = IAPManager.preferredIAPManager(this)
  }

  override fun onDestroy() {
      super.onDestroy()
      iapManager?.destroy()
      iapManager = null
  }
  ```

#### IAPManager 接口的使用
直接调用 `IAPManager` 的接口执行商品信息查询、发起购买等操作即可，内部会自动判断内购平台服务的连接状态，确保先连接上内购平台的服务后，再发起相应的内购业务。

* **注意：`IAPManager` 的内购相关接口，除了 `addPurchaseAutoUpdateListener` 接口传入的 `listener` 需要调用处自行处理内存泄漏问题，其他接口只要传了 `lifecycleOwner`，内部会处理好 `listener` 的内存泄漏问题，`listener` 内可以正常调用 `Activity` 或 `Fragment` 对象的方法或变量。**

下面列举几个 `IAPManager` 的接口使用示例，其他接口的使用示例请查看 demo App 中的 `FirstPageActivity`。

带结果状态的回调第一个参数为不可变的 `IAPResult`：`resultCode` 使用现有的 `IAPResultCode`；`subResultCode` 和 `debugMessage` 分别保留 Google Play 原始购买子状态码和调试消息。Amazon 和本地校验结果的这两个字段分别为 `null` 和空字符串。原先直接比较回调参数的调用处需要改为比较 `result.resultCode`。

Google Play 同时查询订阅和消耗品订单时，任一查询成功即返回 `Ok`，诊断信息取成功一侧（两侧均成功时取订阅）；两侧均失败时返回 `Unknown`，诊断信息取订阅查询。

#### Google Play 订阅提醒

此入口需使用包含本次改动的库版本，原 `1.0.4.7` 及更早版本不支持。

通过统一接口 `IAPManager.showInAppMessages(activity, listener)` 请求提醒，无需转换为 Google Play 实现类。Google Play 复用已有支付连接请求 `TRANSACTIONAL` 消息，断线时由已启用自动重连的 Billing SDK 处理，不在外层按 `isReady` 提前返回。Amazon 通过 listener 返回 `IAPResultCode.FeatureNotSupport`，不发起请求。

调用方必须在主线程、Activity 已恢复、窗口已附着并具有有效 window token 时调用。可在现有 `queryPurchase` 成功回调后调用，即使没有当前订单也可请求提醒。首次支付连接仍需完成初始化；冷启动次数限制由 App 管理，不应因热启动、Activity 重建或回前台重复调用，调用失败不主动重试。

接口没有返回值，通过 `listener(IAPResult, purchaseToken)` 返回调用失败或 Google 提醒处理结果，不暴露 Google SDK 的结果或监听器类型。Google Play 调用失败时复用 `BillingResult.toResultCode()` 映射错误，保留 `debugMessage` 并回调，token 为 `null`；调用成功不立即回调，等待 Google 的处理结果。

Google 所有提醒结果均通过 listener 返回：原始提醒码为 `1`（订阅状态更新）时回调 `IAPResultCode.SubscriptionStatusUpdated` 和购买 token；其余提醒码（包括 `0` 无需处理）统一回调 `IAPResultCode.Unknown`，由调用方决定是否处理。提醒处理码与购买结果码含义不同，使用独立映射，仅返回统一 `resultCode`，不使用 `subResultCode` 携带原始提醒码。

App 发起调用时记录 `payInAppMessageCall`；仅 `SubscriptionStatusUpdated` 记录 `payInAppMessageResponse` 并使用 token 验单，其余结果直接忽略。移除 `payInAppMessageResult`，不再统计提醒调用结果或失败原因，也不上报原始数字 `responseCode`；不能用这两个事件计算完整的 API 成功率或返回耗时。

```kotlin
// 在支付连接和 Activity 窗口就绪后执行；App 自行保证冷启动仅尝试一次。
iapManager.showInAppMessages(activity) { response, purchaseToken ->
    // 回调在主线程执行。listener 不自动绑定页面生命周期，不应捕获 Activity。
    // App 仅处理订阅状态更新，其余结果直接忽略。
    if (response.resultCode == IAPResultCode.SubscriptionStatusUpdated) {
        // 使用 purchaseToken 向服务端验单，并以服务端结果刷新权益。
        // 可能是恢复订阅，也可能是确认价格上涨，不能直接统计为付款修复成功。
    }
}
```

是否展示由 Google Play 决定；调用成功不代表曝光，接口也不提供准确的曝光、点击和关闭统计。`listener` 不持有 Activity 时，页面销毁后仍可处理状态更新并完成验单。

以下为通用支付接口示例：

  1. 查询商品详情：

  Google Play 和 Amazon 在查询包含未获取商品时返回 `PartialSuccess` 和商品 Map，即使全部商品均未获取也会保留这些商品信息。可通过 `IAPProductInfo.isUnfetchedProduct` 判断详情是否未获取，并读取 `unfetchedCode`；Amazon 的 `unavailableSkus` 中的商品使用 `IAPUnfetchedProductCode.ProductUnavailable`，其他缺失商品使用 `Unknown`，占位商品价格为空字符串。平台整体查询失败时仍返回失败状态和 `null`。

  ```kotlin
  val productType: IAPProductType = IAPProductType.Subs // or IAPProductType.Inapp
  val productId = "" // 你的商品 ID
  iapManager?.queryProduct(productType, listOf(productId), this) { result, map ->
      if (map === null) {
          // 查询商品信息失败
          return@queryProduct
      }
      // 更新界面显示的商品信息
      updateProductInfoView(map)
  }
  ```

  2. 购买内购商品：

  ```kotlin
  val productType: IAPProductType = IAPProductType.Subs // or IAPProductType.Inapp
  val productId = "" // 你的商品 ID
  iapManager?.launchPurchase(this, productId, null, this) { result, info ->
      if (result.resultCode != IAPResultCode.Ok) {
          // 处理购买商品失败的结果
          return@launchPurchase
      }

      // 处理订单，为用户提供相应的权益
      // ……

      // 更新界面显示用户权益
      updatePurchaseInfoView(info!!)

      // 订单处理完后，执行完成订单的操作
      if (info.productType == IAPProductType.Subs) {
          iapManager?.acknowledge(info, this) { result, success ->
          }
      } else {
          iapManager?.consume(info, this) { result, success ->
          }
      }
  }
  ```

  2. 查询未处理的内购订单：

  ```kotlin
  val productType: IAPProductType = IAPProductType.Subs // or IAPProductType.Inapp or null
  iapManager?.queryPurchase(productType, this) { result, map ->
      if (map === null) {
          // 没有待处理的订单
          return@queryPurchase
      }

      map.forEach { entry ->
          // 处理订单，为用户提供相应的权益
          // ……

          // 更新界面显示用户权益
          updatePurchaseInfoView(entry.value)

          // 订单处理完后，执行完成订单的操作
          if (entry.value.productType == IAPProductType.Subs) {
              iapManager?.acknowledge(entry.value, this) { result, success ->
              }
          } else {
              iapManager?.consume(entry.value, this) { result, success ->
              }
          }
      }
  }
  ```

### 日志输出
内购平台接口的调用和调用结果都会输出相应的日志，可以通过以下方式来控制日志的输出：

  1. 通过 `IAPConfig.debugLogEnable` 来控制是否输出日志；
  2. 通过设置 `IAPConfig.logClass` 来自定义如何输出日志，`IAPConfig.logClass` 需要实现 `IAPLog` 接口类。如果不设置 `IAPConfig.logClass`，默认会通过 `android.util.Log.d(String tag, String msg)` 接口输出日志。

### 混淆

* 集成 Google Play 应用内购无需添加混淆规则。

* 集成 Amazon 应用内购需要添加以下混淆规则：

```
-dontwarn com.amazon.**
-keep class com.amazon.** {*;}
-keepattributes *Annotation*
-libraryjars libs/in-app-purchasing-2.0.76.jar
```