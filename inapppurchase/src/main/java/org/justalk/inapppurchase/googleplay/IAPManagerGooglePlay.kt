package org.justalk.inapppurchase.googleplay

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ConnectionState
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.justalk.inapppurchase.IAPConfig
import org.justalk.inapppurchase.IAPLog
import org.justalk.inapppurchase.IAPManager
import org.justalk.inapppurchase.IAPPlatform
import org.justalk.inapppurchase.IAPProductInfo
import org.justalk.inapppurchase.IAPProductType
import org.justalk.inapppurchase.IAPPurchaseInfo
import org.justalk.inapppurchase.IAPResultCode
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

// https://developer.android.com/google/play/billing/integrate
class IAPManagerGooglePlay(context: Context) : IAPManager(), PurchasesUpdatedListener, BillingClientStateListener {

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private val purchaseListenerMap = mutableMapOf<String, (BillingResult, Purchase?) -> Unit>()
    private val purchaseAutoUpdateListenerList = mutableListOf<(IAPPurchaseInfo) -> Unit>()
    private val setupListeners = mutableListOf<(Boolean) -> Unit>()
    private val setupTimeoutHandler by lazy {
        Handler(Looper.getMainLooper())
    }
    private val setupTimeoutRunnable by lazy {
        Runnable {
            val connected = billingClient.connectionState == ConnectionState.CONNECTED
            printLog { "setupTimeout, connected:$connected" }
            onSetupEnd(connected)
        }
    }
    private val billingClient by lazy {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }
    private var iapLog: IAPLog? = null

    override fun queryProduct(
        productType: IAPProductType,
        productIdList: List<String>,
        listener: (Map<String, IAPProductInfo>?) -> Unit
    ) {
        checkConnection { connected ->
            if (!connected) {
                listener(null)
                return@checkConnection
            }
            val billingProductType = productType.toBillingProductType()
            val productList = productIdList.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductType(billingProductType)
                    .setProductId(productId)
                    .build()
            }
            val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()
            val logId = getNewLogId()
            printLog { "queryProductDetail start:$logId, productType:$billingProductType, productIdList(${productIdList.size}):${productIdList.toTypedArray().contentToString()}" }
            billingClient.queryProductDetailsAsync(queryProductDetailsParams) { result: BillingResult, productDetailList: List<ProductDetails> ->
                printLog { "queryProductDetail end:$logId, result:${result.logMsg()}, productDetailList(${productDetailList.size}):${productDetailList.toTypedArray().contentToString()}" }
                if (!result.isSuccess() || productDetailList.size < productIdList.size) {
                    listener(null)
                    return@queryProductDetailsAsync
                }
                val productInfoMap = mutableMapOf<String, IAPProductInfo>()
                productIdList.forEach { productId ->
                    val productDetail = productDetailList.firstOrNull { it.productId == productId } ?: run {
                        listener(null)
                        return@queryProductDetailsAsync
                    }
                    val productInfo = productDetail.toProductInfo() ?: run {
                        listener(null)
                        return@queryProductDetailsAsync
                    }
                    productDetailsMap[productId] = productDetail
                    productInfoMap[productId] = productInfo
                }
                listener(productInfoMap)
            }
        }
    }

    override fun queryPurchase(
        productType: IAPProductType?,
        listener: (Map<String, IAPPurchaseInfo>?) -> Unit
    ) {
        checkConnection { connected ->
            if (!connected) {
                listener(null)
                return@checkConnection
            }
            productType?.also { type ->
                queryPurchaseImpl(type, listener)
            } ?: run {
                queryPurchaseImpl(IAPProductType.Subs) { subsMap ->
                    queryPurchaseImpl(IAPProductType.Inapp) { inappMap ->
                        val purchaseInfoMap = when {
                            inappMap != null -> inappMap.toMutableMap().also { map ->
                                map.putAll(subsMap ?: mapOf())
                            }
                            subsMap != null -> subsMap
                            else -> null
                        }
                        listener(purchaseInfoMap)
                    }
                }
            }
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        extraParamsMap: Map<String, Any>?,
        listener: (IAPResultCode, IAPPurchaseInfo?) -> Unit
    ) {
        val weakActivity = WeakReference(activity)
        checkConnection { connected ->
            if (!connected) {
                listener(IAPResultCode.NotConnected, null)
                return@checkConnection
            }
            val productDetails = productDetailsMap[productId] ?: run {
                listener(IAPResultCode.NoValidProductDetail, null)
                return@checkConnection
            }
            val activity1 = weakActivity.get() ?: run {
                listener(IAPResultCode.Unknown, null)
                return@checkConnection
            }
            val detailsParams = ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .also { paramsBuilder ->
                    productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken?.also { offerToken ->
                        if (offerToken.isNotEmpty()) {
                            paramsBuilder.setOfferToken(offerToken)
                        }
                    }
                }
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(arrayListOf(detailsParams))
                .setIsOfferPersonalized(true)
                .also { flowParamsBuilder ->
                    (extraParamsMap?.get(PURCHASE_ARG_ACCOUNT_ID) as? String)?.also { accountId ->
                        flowParamsBuilder.setObfuscatedAccountId(accountId)
                    }
                }
                .build()
            val launchResult: BillingResult = billingClient.launchBillingFlow(activity1, flowParams)
            val logId = getNewLogId()
            printLog { "launchPurchase start:$logId, result:${launchResult.logMsg()}, productId:$productId, extraParamsMap(${extraParamsMap?.size ?: -1}):$extraParamsMap, productDetails:$productDetails" }
            if (!launchResult.isSuccess()) {
                listener(launchResult.toResultCode(), null)
                return@checkConnection
            }
            purchaseListenerMap[productId] = listener@{ result, purchase ->
                printLog { "launchPurchase end:$logId, result:${result.logMsg()}, purchase:$purchase" }
                if (!result.isSuccess()) {
                    listener(result.toResultCode(), null)
                    return@listener
                }
                purchase?.toPurchaseInfo(toProductType(productDetails.productType))?.also { purchaseInfo ->
                    listener(IAPResultCode.Ok, purchaseInfo)
                } ?: run {
                    listener(IAPResultCode.Unknown, null)
                }
            }
        }
    }

    override fun acknowledge(purchaseInfo: IAPPurchaseInfo, listener: (Boolean) -> Unit) {
        checkConnection { connected ->
            if (!connected) {
                listener(false)
                return@checkConnection
            }
            val logId = getNewLogId()
            printLog { "acknowledgePurchase start:$logId, purchaseInfo:$purchaseInfo" }
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseInfo.purchaseToken).build()
            billingClient.acknowledgePurchase(params) { result: BillingResult ->
                printLog { "acknowledgePurchase end$:$logId, result:${result.logMsg()}" }
                listener(result.isSuccess())
            }
        }
    }

    override fun consume(purchaseInfo: IAPPurchaseInfo, listener: (Boolean) -> Unit) {
        checkConnection { connected ->
            if (!connected) {
                listener(false)
                return@checkConnection
            }
            val logId = getNewLogId()
            printLog { "consumeProduct start:$logId, purchaseInfo:$purchaseInfo" }
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseInfo.purchaseToken).build()
            billingClient.consumeAsync(params) { result: BillingResult, purchaseToken: String ->
                printLog { "consumeProduct end:$logId, result:${result.logMsg()}, purchaseToken:$purchaseToken" }
                listener(result.isSuccess())
            }
        }
    }

    override fun addPurchaseAutoUpdateListener(listener: (IAPPurchaseInfo) -> Unit) {
        purchaseAutoUpdateListenerList.add(listener)
    }

    override fun destroy() {
        productDetailsMap.clear()
        purchaseListenerMap.clear()
        purchaseAutoUpdateListenerList.clear()
        setupTimeoutHandler.removeCallbacksAndMessages(null)
        setupListeners.clear()
        billingClient.endConnection()
    }

    override fun platform() = IAPPlatform.GooglePlay

    override fun getAmazonUserId(listener: (String?) -> Unit) {
        listener(null)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchaseList: List<Purchase>?) {
        printLog { "onPurchasesUpdated:${result.logMsg()}, purchaseListenerMap(${purchaseListenerMap.size}):${purchaseListenerMap.keys.toTypedArray().contentToString()}, purchaseList(${purchaseList?.size ?: -1}):${purchaseList?.toTypedArray().contentToString()}" }
        purchaseList?.forEach { purchase ->
            purchase.products.firstOrNull()?.also { productId ->
                purchaseListenerMap[productId]?.also { listener ->
                    listener(result, purchase)
                    purchaseListenerMap.remove(productId)
                } ?: run {
                    purchaseAutoUpdateListenerList.forEach { listener ->
                        purchase.toPurchaseInfo(null)?.also { purchaseInfo ->
                            listener(purchaseInfo)
                        }
                    }
                }
            }
        } ?: run {
            purchaseListenerMap.forEach { entry ->
                entry.value(result, null)
            }
            purchaseListenerMap.clear()
        }
    }

    override fun onBillingServiceDisconnected() {
        printLog { "onBillingServiceDisconnected" }
        onSetupEnd(false)
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        val connected = billingClient.connectionState == ConnectionState.CONNECTED
        printLog { "onBillingSetupFinished:${result.logMsg()}, connected:$connected" }
        onSetupEnd(connected)
    }

    private fun checkConnection(listener: (Boolean) -> Unit) {
        when (billingClient.connectionState) {
            ConnectionState.CONNECTED -> listener(true)
            ConnectionState.CONNECTING -> setupListeners.add(listener)
            else -> {
                setupListeners.add(listener)
                setupTimeoutHandler.postDelayed(setupTimeoutRunnable, 30000)
                billingClient.startConnection(this)
            }
        }
    }

    private fun onSetupEnd(connected: Boolean) {
        setupTimeoutHandler.removeCallbacks(setupTimeoutRunnable)
        setupListeners.forEach { listener ->
            listener(connected)
        }
        setupListeners.clear()
    }

    private fun queryPurchaseImpl(
        productType: IAPProductType,
        listener: (Map<String, IAPPurchaseInfo>?) -> Unit
    ) {
        val logId = getNewLogId()
        printLog { "queryPurchaseImpl start:$logId, productType:$productType" }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType.toBillingProductType())
            .build()
        billingClient.queryPurchasesAsync(params) { result: BillingResult, purchaseList: List<Purchase> ->
            printLog { "queryPurchaseImpl end:$logId, result:${result.logMsg()}, purchaseList(${purchaseList.size}):${purchaseList.toTypedArray().contentToString()}" }
            if (!result.isSuccess() || purchaseList.isEmpty()) {
                listener(null)
                return@queryPurchasesAsync
            }
            val purchaseInfoMap = mutableMapOf<String, IAPPurchaseInfo>()
            for (purchase in purchaseList) {
                val purchaseInfo = purchase.toPurchaseInfo(productType) ?: continue
                purchaseInfoMap[purchaseInfo.productId] = purchaseInfo
            }
            listener(purchaseInfoMap.ifEmpty { null })
        }
    }

    private fun toProductType(billingProductType: String): IAPProductType {
        return if (billingProductType == ProductType.SUBS) {
            IAPProductType.Subs
        } else {
            IAPProductType.Inapp
        }
    }

    private fun printLog(msg: () -> String) {
        if (IAPConfig.debugLogEnable) {
            (iapLog ?: IAPConfig.getLogInstance().also { iapLog = it }).d("IAPManagerGooglePlay", msg())
        }
    }

    companion object {

        private val logId: AtomicInteger = AtomicInteger(1)

        private fun getNewLogId() = logId.getAndIncrement()

    }

}