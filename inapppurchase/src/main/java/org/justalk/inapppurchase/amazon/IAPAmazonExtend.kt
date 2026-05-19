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
        PurchaseResponse.RequestStatus.SUCCESSFUL -> IAPResultCode.Ok
        // https://developer.amazon.com/zh/docs/in-app-purchasing/iap-implement-iap.html
        // Note that a PurchaseResponse.RequestStatus result of FAILED can simply mean that the user canceled the purchase before completion.
        // 目前亚马逊 API 未区分用户取消购买的错误码，官方建议将 FAILED 作为用户取消购买的错误码
        PurchaseResponse.RequestStatus.FAILED -> IAPResultCode.UserCanceled
        PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> IAPResultCode.ItemAlreadyOwned
        PurchaseResponse.RequestStatus.INVALID_SKU,
        PurchaseResponse.RequestStatus.NOT_SUPPORTED -> IAPResultCode.Unavailable
        else -> IAPResultCode.Unknown
    }
}