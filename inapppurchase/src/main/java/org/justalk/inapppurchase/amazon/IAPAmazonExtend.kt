package org.justalk.inapppurchase.amazon

import com.amazon.device.iap.model.Product
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.Receipt
import com.amazon.device.iap.model.UserData
import org.justalk.inapppurchase.IAPProductInfo
import org.justalk.inapppurchase.IAPProductType
import org.justalk.inapppurchase.IAPPurchaseInfo
import org.justalk.inapppurchase.IAPPurchaseState
import org.justalk.inapppurchase.IAPResultCode
import java.text.NumberFormat

fun ProductType.toProductType(): IAPProductType {
    return if (this == ProductType.SUBSCRIPTION) {
        IAPProductType.Subs
    } else {
        IAPProductType.Inapp
    }
}

fun Product.toProductInfo(currencyFormat: NumberFormat): IAPProductInfo {
    val productInfo = IAPProductInfo(
        productType.toProductType(),
        sku,
        price
    )
    try {
        productInfo.amountMicros = ((currencyFormat.parse(price)?.toDouble() ?: 0.0) * 1000000).toLong()
        productInfo.currencyCode = currencyFormat.currency?.currencyCode ?: ""
    } catch (ignored: Throwable) {
    }
    return productInfo
}

fun Receipt.toPurchaseInfo(userData: UserData): IAPPurchaseInfo {
    return IAPPurchaseInfo(
        productType.toProductType(),
        sku,
        receiptId,
        userData.userId,
        purchaseDate.time,
        IAPPurchaseState.Purchased,
        !isCanceled,
        false,
        userData.userId
    )
}

fun PurchaseResponse.toPurchaseInfo(): IAPPurchaseInfo {
    return IAPPurchaseInfo(
        receipt.productType.toProductType(),
        receipt.sku,
        receipt.receiptId,
        userData.userId,
        receipt.purchaseDate.time,
        IAPPurchaseState.Purchased,
        !receipt.isCanceled,
        false,
        userData.userId
    )
}

fun PurchaseResponse.RequestStatus.toResultCode(): IAPResultCode {
    return when (this) {
        // 购买流程顺利完成，付款已成功。建议：此时你应该在应用内为用户发放对应的商品、解锁功能或更新订阅状态，并确保妥善保存购买记录（通常通过 Receipt 进行验证）。
        PurchaseResponse.RequestStatus.SUCCESSFUL -> IAPResultCode.Ok
        // 购买未能成功。常见原因：用户在支付界面主动点击了取消、用户的支付方式无效（如信用卡过期）、或者在购买过程中遇到了网络中断。建议：通常不需要做特殊处理，或者可以静默处理，避免过度打扰用户。
        // https://developer.amazon.com/zh/docs/in-app-purchasing/iap-implement-iap.html
        // Note that a PurchaseResponse.RequestStatus result of FAILED can simply mean that the user canceled the purchase before completion.
        // 目前亚马逊 API 未区分用户取消购买的错误码，官方建议将 FAILED 作为用户取消购买的错误码
        PurchaseResponse.RequestStatus.FAILED -> IAPResultCode.UserCanceled
        // 用户尝试购买一个他们已经拥有且无法重复购买的商品。常见场景：用户尝试再次购买“权利类商品 (Entitlements)”（即非消耗品，如去广告功能、解锁完整版游戏）或仍在有效期内的“订阅 (Subscriptions)”。
        PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> IAPResultCode.ItemAlreadyOwned
        // 你请求购买的商品 SKU 是无效的。常见原因：传入的 SKU 字符串拼写错误，或者该商品尚未在亚马逊开发者控制台 (Amazon Developer Console) 中创建、配置或发布。
        PurchaseResponse.RequestStatus.INVALID_SKU -> IAPResultCode.ItemUnavailable
        // 当前设备或环境不支持进行应用内购买。常见原因：用户的系统版本过旧、未安装或登录亚马逊应用商店客户端、或者尝试调用当前 SDK 版本不支持的购买功能。
        PurchaseResponse.RequestStatus.NOT_SUPPORTED -> IAPResultCode.FeatureNotSupport
        else -> IAPResultCode.Unknown
    }
}