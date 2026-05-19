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
        BillingResponseCode.OK -> IAPResultCode.Ok
        BillingResponseCode.USER_CANCELED -> IAPResultCode.UserCanceled
        BillingResponseCode.ITEM_ALREADY_OWNED -> IAPResultCode.ItemAlreadyOwned
        BillingResponseCode.ITEM_NOT_OWNED -> IAPResultCode.ItemNotOwned
        BillingResponseCode.SERVICE_TIMEOUT,
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.NETWORK_ERROR -> IAPResultCode.NotConnected
        BillingResponseCode.FEATURE_NOT_SUPPORTED,
        BillingResponseCode.BILLING_UNAVAILABLE,
        BillingResponseCode.ITEM_UNAVAILABLE,
        BillingResponseCode.DEVELOPER_ERROR,
        BillingResponseCode.ERROR -> IAPResultCode.Unavailable
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