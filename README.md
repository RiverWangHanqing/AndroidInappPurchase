# AndroidInappPurchase
AndroidInappPurchase 是一个提供统一接口，便于集成 Android 不同平台内购功能的开源库。目前支持集成 Google Play 和 Amazon 的应用内购。

## 集成指南
### 安装引入
在 App 模块的 build.gradle 文件里添加：

```gradle
dependencies {
    implementation 'com.github.RiverWangHanqing:AndroidInappPurchase:v1.0'
}
```

### 内购平台 SDK 的引入
无需引入所有内购平台的 SDK，可以按需引入：

  1. 需要集成 Google Play 内购功能时，在 App 模块的 build.gradle 文件里添加:

  ```gradle
  dependencies {
      implementation 'com.android.billingclient:billing:7.0.0'
  }
  ```

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

  1. 查询商品详情：

  ```kotlin
  val productType: IAPProductType = IAPProductType.Subs // or IAPProductType.Inapp
  val productId = "" // 你的商品 ID
  iapManager?.queryProduct(productType, listOf(productId), this) { map ->
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
  iapManager?.launchPurchase(this, productId, null, this) { code, info ->
      if (code != IAPResultCode.Ok) {
          // 处理购买商品失败的结果
          return@launchPurchase
      }

      // 处理订单，为用户提供相应的权益
      // ……

      // 更新界面显示用户权益
      updatePurchaseInfoView(info!!)

      // 订单处理完后，执行完成订单的操作
      if (info.productType == IAPProductType.Subs) {
          iapManager?.acknowledge(info, this) {
          }
      } else {
          iapManager?.consume(info, this) {
          }
      }
  }
  ```

  2. 查询未处理的内购订单：

  ```kotlin
  val productType: IAPProductType = IAPProductType.Subs // or IAPProductType.Inapp or null
  iapManager?.queryPurchase(productType, this) { map ->
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
              iapManager?.acknowledge(entry.value, this) {
              }
          } else {
              iapManager?.consume(entry.value, this) {
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