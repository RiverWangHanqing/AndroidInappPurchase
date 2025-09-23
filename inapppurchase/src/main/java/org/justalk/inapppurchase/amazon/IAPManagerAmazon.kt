package org.justalk.inapppurchase.amazon

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.RequestId
import com.amazon.device.iap.model.UserDataResponse
import org.justalk.inapppurchase.IAPConfig
import org.justalk.inapppurchase.IAPLog
import org.justalk.inapppurchase.IAPManager
import org.justalk.inapppurchase.IAPProductInfo
import org.justalk.inapppurchase.IAPProductType
import org.justalk.inapppurchase.IAPPurchaseInfo
import org.justalk.inapppurchase.IAPResultCode
import org.justalk.inapppurchase.IAPPlatform
import java.text.NumberFormat

// https://developer.amazon.com/zh/docs/in-app-purchasing/iap-overview.html
class IAPManagerAmazon(context: Context) : IAPManager(), PurchasingListener {

    private val queryProductListenerMap = mutableMapOf<RequestId, (IAPResultCode, Map<String, IAPProductInfo>?) -> Unit>()
    private val queryPurchaseListenerMap = mutableMapOf<RequestId, (IAPResultCode, Map<String, IAPPurchaseInfo>?) -> Unit>()
    private val purchaseListenerMap = mutableMapOf<RequestId, (IAPResultCode, IAPPurchaseInfo?) -> Unit>()
    private val userDataListenerMap = mutableMapOf<RequestId, (IAPResultCode, String?) -> Unit>()
    private val purchaseAutoUpdateListenerList = mutableListOf<(IAPPurchaseInfo) -> Unit>()

    private val queryProductImplListenerMap = mutableMapOf<RequestId, (ProductDataResponse) -> Unit>()
    private val queryPurchaseImplListenerMap = mutableMapOf<RequestId, (PurchaseUpdatesResponse) -> Unit>()
    private val purchaseImplListenerMap = mutableMapOf<RequestId, (PurchaseResponse) -> Unit>()
    private val userDataImplListenerMap = mutableMapOf<RequestId, (UserDataResponse) -> Unit>()
    private val currencyFormat by lazy {
        NumberFormat.getCurrencyInstance()
    }
    private val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }
    private var iapLog: IAPLog? = null

    init {
        PurchasingService.registerListener(context, this)
    }

    override fun queryProduct(
        productType: IAPProductType,
        productIdList: List<String>,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Map<String, IAPProductInfo>?) -> Unit
    ) {
        val requestId = PurchasingService.getProductData(productIdList.toSet())
        queryProductListenerMap[requestId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                queryProductListenerMap[requestId]?.also {
                    printLog { "queryProduct end:$requestId, lifecycleOwner DESTROYED" }
                    queryProductListenerMap.remove(requestId)
                }
            }
        })

        printLog { "queryProduct start, requestId:$requestId, productType:$productType, productIdList(${productIdList.size}):${productIdList.toTypedArray().contentToString()}" }
        queryProductImplListenerMap[requestId] = listener@{ response ->
            val listenerCache = queryProductListenerMap[requestId]
            queryProductListenerMap.remove(requestId)
            printLog { "queryProduct end, requestId:$requestId, listenerCache:${listenerCache != null}, response:$response" }
            if (listenerCache == null) {
                return@listener
            }
            if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL) {
                listenerCache(IAPResultCode.Unknown, null)
                return@listener
            }
            if (response.productData.size < productIdList.size) {
                listenerCache(IAPResultCode.Unknown, null)
                return@listener
            }
            val productInfoMap = mutableMapOf<String, IAPProductInfo>()
            productIdList.forEach { productId ->
                val product = response.productData[productId] ?: run {
                    listenerCache(IAPResultCode.Unknown, null)
                    return@listener
                }
                productInfoMap[productId] = product.toProductInfo(currencyFormat)
            }
            listenerCache(IAPResultCode.Ok, productInfoMap)
        }
    }

    override fun queryPurchase(
        productType: IAPProductType?,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Map<String, IAPPurchaseInfo>?) -> Unit
    ) {
        val requestId = PurchasingService.getPurchaseUpdates(false)
        queryPurchaseListenerMap[requestId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                queryPurchaseListenerMap[requestId]?.also {
                    printLog { "queryPurchase end:$requestId, lifecycleOwner DESTROYED" }
                    queryPurchaseListenerMap.remove(requestId)
                }
            }
        })

        printLog { "queryPurchase start, requestId:$requestId, productType:$productType" }
        queryPurchaseImplListenerMap[requestId] = listener@{ response ->
            val listenerCache = queryPurchaseListenerMap[requestId]
            queryPurchaseListenerMap.remove(requestId)
            printLog { "queryPurchase end, requestId:$requestId, listenerCache:${listenerCache != null}, response:$response" }
            if (listenerCache == null) {
                return@listener
            }
            if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
                listenerCache(IAPResultCode.Unknown, null)
                return@listener
            }
            val productInfoMap = mutableMapOf<String, IAPPurchaseInfo>()
            response.receipts.forEach { receipt ->
                if (productType == null || receipt.productType.toProductType() == productType) {
                    productInfoMap[receipt.sku] = receipt.toPurchaseInfo(response.userData)
                }
            }
            listenerCache(IAPResultCode.Ok, productInfoMap.ifEmpty { null })
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        extraParamsMap: Map<String, Any>?,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, IAPPurchaseInfo?) -> Unit
    ) {
        val requestId = PurchasingService.purchase(productId)
        purchaseListenerMap[requestId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                purchaseListenerMap[requestId]?.also {
                    printLog { "launchPurchase end:$requestId, lifecycleOwner DESTROYED" }
                    purchaseListenerMap.remove(requestId)
                }
            }
        })

        printLog { "launchPurchase start, requestId:$requestId, productId:$productId, extraParamsMap(${extraParamsMap?.size ?: -1}):$extraParamsMap" }
        purchaseImplListenerMap[requestId] = listener@{ response ->
            val listenerCache = purchaseListenerMap[requestId]
            purchaseListenerMap.remove(requestId)
            printLog { "launchPurchase end, requestId:$requestId, listenerCache:${listenerCache != null}, response:$response" }
            if (listenerCache == null) {
                return@listener
            }
            if (response.requestStatus != PurchaseResponse.RequestStatus.SUCCESSFUL) {
                listenerCache(response.requestStatus.toResultCode(), null)
                return@listener
            }
            listenerCache(IAPResultCode.Ok, response.toPurchaseInfo())
        }
    }

    override fun acknowledge(
        purchaseInfo: IAPPurchaseInfo,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Boolean) -> Unit
    ) {
        PurchasingService.notifyFulfillment(purchaseInfo.orderId, FulfillmentResult.FULFILLED)
        listener(IAPResultCode.Ok, true)
    }

    override fun consume(
        purchaseInfo: IAPPurchaseInfo,
        lifecycleOwner: LifecycleOwner,
        listener: (IAPResultCode, Boolean) -> Unit
    ) {
        PurchasingService.notifyFulfillment(purchaseInfo.orderId, FulfillmentResult.FULFILLED)
        listener(IAPResultCode.Ok, true)
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
        userDataListenerMap.clear()
        purchaseAutoUpdateListenerList.clear()
        
        // 清理Amazon特定的监听器Map
        queryProductImplListenerMap.clear()
        queryPurchaseImplListenerMap.clear()
        purchaseImplListenerMap.clear()
        userDataImplListenerMap.clear()
    }

    override fun platform() = IAPPlatform.Amazon

    override fun getAmazonUserId(lifecycleOwner: LifecycleOwner, listener: (IAPResultCode, String?) -> Unit) {
        val requestId = PurchasingService.getUserData()
        userDataListenerMap[requestId] = listener
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                userDataListenerMap[requestId]?.also {
                    printLog { "getAmazonUserId end:$requestId, lifecycleOwner DESTROYED" }
                    userDataListenerMap.remove(requestId)
                }
            }
        })

        printLog { "getAmazonUserId start, requestId:$requestId" }
        userDataImplListenerMap[requestId] = listener@{ response ->
            val listenerCache = userDataListenerMap[requestId]
            userDataListenerMap.remove(requestId)
            printLog { "getAmazonUserId end, requestId:$requestId, listenerCache:${listenerCache != null}, response:$response" }
            if (listenerCache == null) {
                return@listener
            }
            if (response.requestStatus != UserDataResponse.RequestStatus.SUCCESSFUL) {
                listenerCache(IAPResultCode.Unknown, null)
                return@listener
            }
            listenerCache(IAPResultCode.Ok, response.userData.userId.takeIf { !it.isNullOrEmpty() })
        }
    }

    override fun onUserDataResponse(userDataResponse: UserDataResponse) {
        printLog { "onUserDataResponse:$userDataResponse" }
        mainHandler.post {
            userDataImplListenerMap[userDataResponse.requestId]?.also { listener ->
                listener(userDataResponse)
                userDataImplListenerMap.remove(userDataResponse.requestId)
            }
        }
    }

    override fun onProductDataResponse(productDataResponse: ProductDataResponse) {
        printLog { "onProductDataResponse:$productDataResponse" }
        mainHandler.post {
            queryProductImplListenerMap[productDataResponse.requestId]?.also { listener ->
                listener(productDataResponse)
                queryProductImplListenerMap.remove(productDataResponse.requestId)
            }
        }
    }

    override fun onPurchaseResponse(purchaseResponse: PurchaseResponse) {
        printLog { "onPurchaseResponse:$purchaseResponse" }
        mainHandler.post {
            purchaseImplListenerMap[purchaseResponse.requestId]?.also { listener ->
                listener(purchaseResponse)
                purchaseImplListenerMap.remove(purchaseResponse.requestId)
            } ?: run {
                val purchaseInfo = purchaseResponse.toPurchaseInfo()
                purchaseAutoUpdateListenerList.forEach { listener ->
                    listener(purchaseInfo)
                }
            }
        }
    }

    override fun onPurchaseUpdatesResponse(purchaseUpdatesResponse: PurchaseUpdatesResponse) {
        printLog { "onPurchaseUpdatesResponse:$purchaseUpdatesResponse" }
        mainHandler.post {
            queryPurchaseImplListenerMap[purchaseUpdatesResponse.requestId]?.also { listener ->
                listener(purchaseUpdatesResponse)
                queryPurchaseImplListenerMap.remove(purchaseUpdatesResponse.requestId)
            }
        }
    }

    private fun printLog(msg: () -> String) {
        if (IAPConfig.debugLogEnable) {
            (iapLog ?: IAPConfig.getLogInstance().also { iapLog = it }).d("IAPManagerAmazon", msg())
        }
    }

}