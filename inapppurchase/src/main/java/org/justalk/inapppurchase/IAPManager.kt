package org.justalk.inapppurchase

import android.app.Activity
import android.content.Context
import org.justalk.inapppurchase.amazon.IAPManagerAmazon
import org.justalk.inapppurchase.googleplay.IAPManagerGooglePlay

abstract class IAPManager {

    /**
     * 查询商品详情
     * 订阅商品和消耗商品的查询需要分开调用
     * @param listener 查询结果不全时返回的 Map 为 null
     */
    abstract fun queryProduct(productType: IAPProductType, productIdList: List<String>, listener: (Map<String, IAPProductInfo>?) -> Unit)

    /**
     * 查询已购买的商品订单
     * @param productType 传 null 时同时查询订阅商品订单和消耗商品订单
     * @param listener 没有查询到订单时返回的 Map 为 null
     */
    abstract fun queryPurchase(productType: IAPProductType? = null, listener: (Map<String, IAPPurchaseInfo>?) -> Unit)

    /**
     * 发起内购商品的购买操作
     */
    abstract fun launchPurchase(activity: Activity, productId: String, extraParamsMap: Map<String, Any>? = null, listener: (IAPResultCode, IAPPurchaseInfo?) -> Unit)

    /**
     * 订阅型商品的订单确认操作
     */
    abstract fun acknowledge(purchaseInfo: IAPPurchaseInfo, listener: (Boolean) -> Unit)

    /**
     * 消耗型商品的订单消耗操作
     */
    abstract fun consume(purchaseInfo: IAPPurchaseInfo, listener: (Boolean) -> Unit)

    /**
     * 不是通过 [launchPurchase] 生成的订单，可以通过这个回调来接收
     */
    abstract fun addPurchaseAutoUpdateListener(listener: (IAPPurchaseInfo) -> Unit)

    /**
     * 释放资源
     */
    abstract fun destroy()

    /**
     * 内购平台
     */
    abstract fun platform(): IAPPlatform

    /**
     * 获取支付平台的 userId
     * 仅亚马逊平台有
     */
    abstract fun getAmazonUserId(listener: (String?) -> Unit)

    companion object {

        /** 与订单绑定的账号信息，[launchPurchase] 接口的 extraParamsMap 的 key，value 类型为 [String] */
        const val PURCHASE_ARG_ACCOUNT_ID = "accountId"
        /** GooglePlay 应用商店的包名 */
        const val PKG_GOOGLE_PLAY_STORE: String = "com.android.vending"
        /** Amazon 应用商店的包名  */
        const val PKG_AMAZON_STORE: String = "com.amazon.venezia"

        /**
         * 自动选择合适的内购平台
         */
        fun preferredIAPManager(context: Context): IAPManager? {
            if (isAppInstalledFrom(context, PKG_AMAZON_STORE)) {
                return IAPManagerAmazon(context)
            }
            if (isPackageInstalled(context, PKG_GOOGLE_PLAY_STORE)) {
                return IAPManagerGooglePlay(context)
            }
            return null
        }

        fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (ignore: Throwable) {
                false
            }
        }

        fun isAppInstalledFrom(context: Context, installer: String): Boolean {
            return try {
                context.packageManager.getInstallerPackageName(context.packageName)?.contains(installer) ?: false
            } catch (ignore: Throwable) {
                false
            }
        }

    }

}