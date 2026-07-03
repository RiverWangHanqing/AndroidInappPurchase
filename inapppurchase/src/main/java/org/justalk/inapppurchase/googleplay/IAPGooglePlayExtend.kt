package org.justalk.inapppurchase.googleplay

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.OnPurchasesUpdatedSubResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import org.justalk.inapppurchase.IAPProductInfo
import org.justalk.inapppurchase.IAPProductType
import org.justalk.inapppurchase.IAPPurchaseInfo
import org.justalk.inapppurchase.IAPPurchaseState
import org.justalk.inapppurchase.IAPResultCode

fun IAPProductType.toBillingProductType(): String {
    return if (this == IAPProductType.Subs) {
        ProductType.SUBS
    } else {
        ProductType.INAPP
    }
}

fun ProductDetails.toProductInfo(): IAPProductInfo? {
    return if (productType == ProductType.SUBS) {
        subscriptionOfferDetails?.get(0)?.pricingPhases?.pricingPhaseList?.let { pricingPhaseList ->
            pricingPhaseList.firstOrNull {
                it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
            }?.let { pricingPhase ->
                IAPProductInfo(
                    IAPProductType.Subs,
                    productId,
                    pricingPhase.formattedPrice,
                    pricingPhase.priceAmountMicros,
                    pricingPhase.priceCurrencyCode
                ).also { productInfo ->
                    pricingPhaseList.firstOrNull {
                        it.priceAmountMicros > 0L && (it.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING || it.recurrenceMode == ProductDetails.RecurrenceMode.NON_RECURRING)
                    }?.also {
                        productInfo.offerFormattedPrice = it.formattedPrice
                        productInfo.offerAmountMicros = it.priceAmountMicros
                        productInfo.offerCurrencyCode = it.priceCurrencyCode
                    }
                    productInfo.freeTrial = pricingPhaseList.any {
                        it.priceAmountMicros == 0L && (it.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING || it.recurrenceMode == ProductDetails.RecurrenceMode.NON_RECURRING)
                    }
                }
            }
        }
    } else {
        oneTimePurchaseOfferDetails?.let { details ->
            IAPProductInfo(
                IAPProductType.Inapp,
                productId,
                details.formattedPrice,
                details.priceAmountMicros,
                details.priceCurrencyCode
            )
        }
    }
}

fun Purchase.toPurchaseInfo(productType: IAPProductType?): IAPPurchaseInfo? {
    val productId = products.firstOrNull() ?: return null
    val purchaseState = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> IAPPurchaseState.Purchased
        else -> IAPPurchaseState.Pending
    }
    return IAPPurchaseInfo(
        productType,
        productId,
        orderId ?: "",
        purchaseToken,
        purchaseTime,
        purchaseState,
        isAutoRenewing,
        isAcknowledged,
        accountIdentifiers?.obfuscatedAccountId
    )
}

fun BillingResult.isSuccess(): Boolean {
    return responseCode == BillingResponseCode.OK
}

fun BillingResult.toResultCode(): IAPResultCode {
    return when (responseCode) {
        // 请求已成功处理。这是唯一表示操作顺利完成的返回码。
        BillingResponseCode.OK -> IAPResultCode.Ok
        // 用户在购买流程中主动点击了“取消”或按了设备的“返回”键。
        BillingResponseCode.USER_CANCELED -> IAPResultCode.UserCanceled
        // 购买失败，因为用户已经拥有此商品。注意：如果是一次性消耗品（如游戏金币），你必须先调用 consumeAsync() 消耗掉它，用户才能再次购买。
        BillingResponseCode.ITEM_ALREADY_OWNED -> IAPResultCode.ItemAlreadyOwned
        // 尝试消耗 (Consume) 或确认 (Acknowledge) 一个商品失败，因为当前账号并没有购买过该商品。
        BillingResponseCode.ITEM_NOT_OWNED -> IAPResultCode.ItemNotOwned
        // 请求在得到 Google Play 响应之前已达到最大超时时间。通常是由于网络极度卡顿或 Play 服务无响应导致。
        BillingResponseCode.SERVICE_TIMEOUT -> IAPResultCode.ServiceTimeout
        // 你的应用与 Google Play 商店服务的连接已断开。通常发生在 Play 商店在后台更新或被系统强制关闭时。建议：调用 startConnection() 重新连接。
        BillingResponseCode.SERVICE_DISCONNECTED -> IAPResultCode.ServiceDisconnected
        // 设备当前没有网络连接，或者 Google Play 商店服务暂时不可用（例如服务器宕机）。
        BillingResponseCode.SERVICE_UNAVAILABLE -> IAPResultCode.ServiceUnavailable
        // 操作因客户端与 Google 之间的网络连接问题而失败。这是在较新版本的 Billing Library 中新增的专门针对网络异常的错误码。
        BillingResponseCode.NETWORK_ERROR -> IAPResultCode.NetworkError
        // 当前设备上的 Google Play 商店应用版本过低，不支持你正在调用的特定结算功能（例如订阅、VR 购买等）。
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> IAPResultCode.FeatureNotSupport
        // 用户所在国家/地区不支持 Google Play 结算，或者用户账号受限（如企业设备配置限制），导致无法进行购买。
        BillingResponseCode.BILLING_UNAVAILABLE -> IAPResultCode.BillingUnavailable
        // 请求购买的商品 ID (SKU) 不存在，或者该商品在 Google Play 管理中心处于“未激活”状态。
        BillingResponseCode.ITEM_UNAVAILABLE -> IAPResultCode.ItemUnavailable
        // 提供给 API 的参数无效。常见原因：未在 AndroidManifest 中添加结算权限、应用未在 Play 管理中心发布（测试轨道）、签名或包名与 Play 管理中心不匹配。
        BillingResponseCode.DEVELOPER_ERROR -> IAPResultCode.DeveloperError
        // 在执行 API 操作期间发生了意外的内部错误。这通常是 Google Play 客户端内部的问题。
        BillingResponseCode.ERROR -> IAPResultCode.Error
        else -> IAPResultCode.Unknown
    }
}

fun BillingResult.logMsg(): String {
    return "responseCode($responseCode - ${responseCodeString()}) - subResponseCode($onPurchasesUpdatedSubResponseCode - ${subResponseCodeString()}) - debugMessage(${debugMessage.ifEmpty { null }})"
}

private fun BillingResult.responseCodeString(): String {
    return when (responseCode) {
        BillingResponseCode.OK -> "OK"
        BillingResponseCode.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
        BillingResponseCode.SERVICE_TIMEOUT -> "SERVICE_TIMEOUT"
        BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
        BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR"
        BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
        BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
        BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
        BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
        BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
        BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
        BillingResponseCode.ERROR -> "ERROR"
        else -> "UNKNOWN"
    }
}

private fun BillingResult.subResponseCodeString(): String {
    return when (onPurchasesUpdatedSubResponseCode) {
        OnPurchasesUpdatedSubResponseCode.NO_APPLICABLE_SUB_RESPONSE_CODE -> "NO_APPLICABLE_SUB_RESPONSE_CODE"
        OnPurchasesUpdatedSubResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS -> "PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS"
        OnPurchasesUpdatedSubResponseCode.USER_INELIGIBLE -> "USER_INELIGIBLE"
        else -> "UNKNOWN"
    }
}