package org.justalk.inapppurchase.googleplay

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import org.justalk.inapppurchase.IAPConfig
import org.justalk.inapppurchase.IAPLog
import org.justalk.inapppurchase.IAPManager
import org.justalk.inapppurchase.IAPPlatform
import org.justalk.inapppurchase.IAPProductInfo
import org.justalk.inapppurchase.IAPProductType
import org.justalk.inapppurchase.IAPPurchaseInfo
import org.justalk.inapppurchase.IAPResultCode
import java.util.concurrent.atomic.AtomicInteger

// https://developer.android.com/google/play/billing/integrate
class IAPManagerGooglePlay(context: Context) : IAPManager(), PurchasesUpdatedListener, BillingClientStateListener {

    private val queryProductListenerMap = mutableMapOf<Int, (IAPResultCode, Map<String, IAPProductInfo>?) -> Unit>()
    private val queryPurchaseListenerMap = mutableMapOf<Int, (IAPResultCode, Map<String, IAPPurchaseInfo>?) -> Unit>()
    private val purchaseListenerMap = mutableMapOf<Int, (IAPResultCode, IAPPurchaseInfo?) -> Unit>()
    private val acknowledgeListenerMap = mutableMapOf<Int, (IAPResultCode, Boolean) -> Unit>()
    private val consumeListenerMap = mutableMapOf<Int, (IAPResultCode, Boolean) -> Unit>()
    private val purchaseAutoUpdateListenerList = mutableListOf<(IAPPurchaseInfo) -> Unit>()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private val purchaseActionsMap = mutableMapOf<String, MutableList<Int>>()
    private val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()
    private var iapLog: IAPLog? = null

    init {
        billingClient.startConnection(this)
    }

    override fun queryProduct(
        productType: IAPProductType,
        productIdList: List<String>,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Map<String, IAPProductInfo>?) -> Unit
    ) {
        val actionId = getActionId()
        queryProductListenerMap[actionId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                queryProductListenerMap[actionId]?.also {
                    printLog { "queryProductDetail end:$actionId, lifecycleOwner DESTROYED" }
                    queryProductListenerMap.remove(actionId)
                }
            }
        })

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
        printLog { "queryProductDetail start:$actionId, productType:$billingProductType, productIdList(${productIdList.size}):${productIdList.toTypedArray().contentToString()}" }
        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { result: BillingResult, queryProductDetailsResult: QueryProductDetailsResult ->
            mainHandler.post {
                val listenerCache = queryProductListenerMap[actionId]
                queryProductListenerMap.remove(actionId)
                val productDetailList = queryProductDetailsResult.productDetailsList
                val unfetchedProductList = queryProductDetailsResult.unfetchedProductList
                printLog { "queryProductDetail end:$actionId, listenerCache:${listenerCache != null}, result:${result.logMsg()}, productDetailList(${productDetailList.size}):${productDetailList.toTypedArray().contentToString()}, unfetchedProductList(${unfetchedProductList.size}):${unfetchedProductList.toTypedArray().contentToString()}" }
                if (listenerCache == null) {
                    return@post
                }

                if (!result.isSuccess()) {
                    listenerCache(result.toResultCode(), null)
                    return@post
                }

                if (productDetailList.size < productIdList.size) {
                    listenerCache(IAPResultCode.Unknown, null)
                    return@post
                }

                val productInfoMap = mutableMapOf<String, IAPProductInfo>()
                productIdList.forEach { productId ->
                    val productDetail = productDetailList.firstOrNull { it.productId == productId } ?: run {
                        listenerCache(IAPResultCode.Unknown, null)
                        return@post
                    }
                    val productInfo = productDetail.toProductInfo() ?: run {
                        listenerCache(IAPResultCode.Unknown, null)
                        return@post
                    }
                    productDetailsMap[productId] = productDetail
                    productInfoMap[productId] = productInfo
                }
                listenerCache(IAPResultCode.Ok, productInfoMap)
            }
        }
    }

    override fun queryPurchase(
        productType: IAPProductType?,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Map<String, IAPPurchaseInfo>?) -> Unit
    ) {
        val actionId = getActionId()
        queryPurchaseListenerMap[actionId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                queryPurchaseListenerMap[actionId]?.also {
                    printLog { "queryPurchase end:$actionId, lifecycleOwner DESTROYED" }
                    queryPurchaseListenerMap.remove(actionId)
                }
            }
        })

        printLog { "queryPurchase start:$actionId, productType:$productType" }
        productType?.also { type ->
            queryPurchaseImpl(actionId, type) { result, map ->
                val listenerCache = queryPurchaseListenerMap[actionId]
                queryPurchaseListenerMap.remove(actionId)
                printLog { "queryPurchase end:$actionId, listenerCache:${listenerCache != null}, map(${map?.size})" }
                listenerCache?.invoke(result, map)
            }
        } ?: run {
            queryPurchaseImpl(actionId, IAPProductType.Subs) { subsResult, subsMap ->
                queryPurchaseImpl(actionId, IAPProductType.Inapp) queryPurchaseImpl2@{ inappResult, inappMap ->
                    val listenerCache = queryPurchaseListenerMap[actionId]
                    queryPurchaseListenerMap.remove(actionId)
                    printLog { "queryPurchase end:$actionId, listenerCache:${listenerCache != null}, subsMap:${subsMap?.size}, inappMap(${inappMap?.size})" }
                    if (listenerCache == null) {
                        return@queryPurchaseImpl2
                    }
                    val purchaseInfoMap = when {
                        inappMap != null -> inappMap.toMutableMap().also { map ->
                            map.putAll(subsMap ?: mapOf())
                        }
                        subsMap != null -> subsMap
                        else -> null
                    }
                    val result = if (subsResult == IAPResultCode.Ok || inappResult == IAPResultCode.Ok) IAPResultCode.Ok else IAPResultCode.Unknown
                    listenerCache(result, purchaseInfoMap)
                }
            }
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        extraParamsMap: Map<String, Any>?,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, IAPPurchaseInfo?) -> Unit
    ) {
        val actionId = getActionId()
        purchaseListenerMap[actionId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                purchaseListenerMap[actionId]?.also {
                    printLog { "launchPurchase end:$actionId, lifecycleOwner DESTROYED" }
                    purchaseListenerMap.remove(actionId)
                }
            }
        })

        printLog { "launchPurchase start1:$actionId, productId:$productId, extraParamsMap(${extraParamsMap?.size ?: -1}):$extraParamsMap" }
        val productDetails = productDetailsMap[productId] ?: run {
            printLog { "launchPurchase end:$actionId, no product detail" }
            listener(IAPResultCode.NoValidProductDetail, null)
            purchaseListenerMap.remove(actionId)
            return
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
        val launchResult: BillingResult = billingClient.launchBillingFlow(activity, flowParams)
        printLog { "launchPurchase start2:$actionId, result:${launchResult.logMsg()}, productId:$productId, extraParamsMap(${extraParamsMap?.size ?: -1}):$extraParamsMap, productDetails:$productDetails" }
        if (!launchResult.isSuccess()) {
            printLog { "launchPurchase end:$actionId, launchBillingFlow invoke fail" }
            listener(launchResult.toResultCode(), null)
            purchaseListenerMap.remove(actionId)
            return
        }

        purchaseActionsMap[productId]?.add(actionId) ?: run {
            purchaseActionsMap[productId] = mutableListOf(actionId)
        }
    }

    override fun acknowledge(
        purchaseInfo: IAPPurchaseInfo,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Boolean) -> Unit
    ) {
        val actionId = getActionId()
        acknowledgeListenerMap[actionId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                acknowledgeListenerMap[actionId]?.also {
                    printLog { "acknowledge end:$actionId, lifecycleOwner DESTROYED" }
                    acknowledgeListenerMap.remove(actionId)
                }
            }
        })

        printLog { "acknowledge start:$actionId, purchaseInfo:$purchaseInfo" }
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseInfo.purchaseToken).build()
        billingClient.acknowledgePurchase(params) { result: BillingResult ->
            mainHandler.post {
                val listenerCache = acknowledgeListenerMap[actionId]
                acknowledgeListenerMap.remove(actionId)
                printLog { "acknowledge end:$actionId, listenerCache:${listenerCache != null}, result:${result.logMsg()}" }
                listenerCache?.invoke(result.toResultCode(), result.isSuccess())
            }
        }
    }

    override fun consume(
        purchaseInfo: IAPPurchaseInfo,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Boolean) -> Unit
    ) {
        val actionId = getActionId()
        consumeListenerMap[actionId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                consumeListenerMap[actionId]?.also {
                    printLog { "consume end:$actionId, lifecycleOwner DESTROYED" }
                    consumeListenerMap.remove(actionId)
                }
            }
        })

        printLog { "consume start:$actionId, purchaseInfo:$purchaseInfo" }
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseInfo.purchaseToken).build()
        billingClient.consumeAsync(params) { result: BillingResult, purchaseToken: String ->
            mainHandler.post {
                val listenerCache = consumeListenerMap[actionId]
                consumeListenerMap.remove(actionId)
                printLog { "consume end:$actionId, listenerCache:${listenerCache != null}, result:${result.logMsg()}, purchaseToken:$purchaseToken" }
                listenerCache?.invoke(result.toResultCode(), result.isSuccess())
            }
        }
    }

    override fun addPurchaseAutoUpdateListener(listener: (IAPPurchaseInfo) -> Unit) {
        purchaseAutoUpdateListenerList.add(listener)
    }

    override fun removePurchaseAutoUpdateListener(listener: (IAPPurchaseInfo) -> Unit) {
        purchaseAutoUpdateListenerList.remove(listener)
    }

    override fun destroy() {
        // 清理Handler和所有回调
        mainHandler.removeCallbacksAndMessages(null)

        // 清理所有监听器Map
        queryProductListenerMap.clear()
        queryPurchaseListenerMap.clear()
        purchaseListenerMap.clear()
        acknowledgeListenerMap.clear()
        consumeListenerMap.clear()
        purchaseAutoUpdateListenerList.clear()

        // 清理缓存数据
        productDetailsMap.clear()
        purchaseActionsMap.clear()

        // 断开BillingClient连接
        billingClient.endConnection()
    }

    override fun platform() = IAPPlatform.GooglePlay

    override fun getAmazonUserId(lifecycleOwner: LifecycleOwner, listener: (IAPResultCode, String?) -> Unit) {
        listener(IAPResultCode.Ok, null)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchaseList: List<Purchase>?) {
        printLog { "onPurchasesUpdated:${result.logMsg()}, purchaseList(${purchaseList?.size ?: -1}):${purchaseList?.toTypedArray().contentToString()}" }
        mainHandler.post {
            purchaseList?.forEach { purchase ->
                purchase.products.firstOrNull()?.also { productId ->
                    purchaseActionsMap[productId]?.forEach { actionId ->
                        val listenerCache = purchaseListenerMap[actionId]
                        purchaseListenerMap.remove(actionId)
                        onPurchaseEnd(actionId, productId, result, purchase, listenerCache)
                        purchaseActionsMap.remove(productId)
                    } ?: run {
                        purchaseAutoUpdateListenerList.forEach { listener ->
                            val productType = productDetailsMap[productId]?.productType?.let { toProductType(it) }
                            purchase.toPurchaseInfo(productType)?.also { purchaseInfo ->
                                listener(purchaseInfo)
                            }
                        }
                    }
                }
            } ?: run {
                purchaseActionsMap.values.forEach { actionIdList ->
                    actionIdList.forEach { actionId ->
                        val listenerCache = purchaseListenerMap[actionId]
                        purchaseListenerMap.remove(actionId)
                        onPurchaseEnd(actionId, null, result, null, listenerCache)
                    }
                }
                purchaseActionsMap.clear()
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        printLog { "onBillingServiceDisconnected" }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        val connected = billingClient.connectionState == ConnectionState.CONNECTED
        printLog { "onBillingSetupFinished:${result.logMsg()}, connected:$connected" }
    }

    private fun queryPurchaseImpl(
        actionId: Int,
        productType: IAPProductType,
        listener: (IAPResultCode, Map<String, IAPPurchaseInfo>?) -> Unit
    ) {
        printLog { "queryPurchaseImpl start:$actionId, productType:$productType" }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType.toBillingProductType())
            .build()
        billingClient.queryPurchasesAsync(params) { result: BillingResult, purchaseList: List<Purchase> ->
            mainHandler.post {
                printLog { "queryPurchaseImpl end:$actionId, result:${result.logMsg()}, purchaseList(${purchaseList.size}):${purchaseList.toTypedArray().contentToString()}" }
                if (!result.isSuccess()) {
                    listener(result.toResultCode(), null)
                    return@post
                }
                if (purchaseList.isEmpty()) {
                    listener(IAPResultCode.Ok, null)
                    return@post
                }
                val purchaseInfoMap = mutableMapOf<String, IAPPurchaseInfo>()
                for (purchase in purchaseList) {
                    val purchaseInfo = purchase.toPurchaseInfo(productType) ?: continue
                    purchaseInfoMap[purchaseInfo.productId] = purchaseInfo
                }
                listener(IAPResultCode.Ok, purchaseInfoMap.ifEmpty { null })
            }
        }
    }

    private fun onPurchaseEnd(actionId: Int, productId: String?, result: BillingResult, purchase: Purchase?, listenerCache: ((IAPResultCode, IAPPurchaseInfo?) -> Unit)?) {
        printLog { "launchPurchase end:$actionId, productId:$productId, listenerCache:${listenerCache != null}, result:${result.logMsg()}, purchase:$purchase" }
        if (listenerCache == null) {
            return
        }

        if (!result.isSuccess()) {
            listenerCache(result.toResultCode(), null)
            return
        }

        val productType = productId?.let { productId1 ->
            productDetailsMap[productId1]?.productType?.let {
                toProductType(it)
            }
        }
        purchase?.toPurchaseInfo(productType)?.also { purchaseInfo ->
            listenerCache(IAPResultCode.Ok, purchaseInfo)
        } ?: run {
            listenerCache(IAPResultCode.Unknown, null)
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

        private val actionId: AtomicInteger = AtomicInteger(1)

        private fun getActionId() = actionId.getAndIncrement()

    }

}