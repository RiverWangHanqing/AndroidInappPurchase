package org.justalk.inapppurchase.amazon

import android.app.Activity
import android.content.Context
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

    private val queryProductDetailListenerMap = mutableMapOf<RequestId, (ProductDataResponse) -> Unit>()
    private val queryPurchaseListenerMap = mutableMapOf<RequestId, (PurchaseUpdatesResponse) -> Unit>()
    private val purchaseListenerMap = mutableMapOf<RequestId, (PurchaseResponse) -> Unit>()
    private val purchaseAutoUpdateListenerList = mutableListOf<(IAPPurchaseInfo) -> Unit>()
    private val userDataListenerMap = mutableMapOf<RequestId, (UserDataResponse) -> Unit>()
    private val currencyFormat by lazy {
        NumberFormat.getCurrencyInstance()
    }
    private var iapLog: IAPLog? = null

    init {
        PurchasingService.registerListener(context.applicationContext, this)
    }

    override fun queryProduct(
        productType: IAPProductType,
        productIdList: List<String>,
        listener: (Map<String, IAPProductInfo>?) -> Unit
    ) {
        val requestId = PurchasingService.getProductData(productIdList.toSet())
        printLog { "queryProductDetail start, requestId:$requestId, productType:$productType, productIdList(${productIdList.size}):${productIdList.toTypedArray().contentToString()}" }
        queryProductDetailListenerMap[requestId] = listener@{ response ->
            printLog { "queryProductDetail end, requestId:$requestId, response:$response" }
            if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL || response.productData.isNullOrEmpty()) {
                listener(null)
                return@listener
            }
            val productInfoMap = mutableMapOf<String, IAPProductInfo>()
            productIdList.forEach { productId ->
                val product = response.productData[productId] ?: run {
                    listener(null)
                    return@listener
                }
                productInfoMap[productId] = product.toProductInfo(currencyFormat)
            }
            listener(productInfoMap)
        }
    }

    override fun queryPurchase(
        productType: IAPProductType?,
        listener: (Map<String, IAPPurchaseInfo>?) -> Unit
    ) {
        val requestId = PurchasingService.getPurchaseUpdates(false)
        printLog { "queryPurchase start, requestId:$requestId, productType:$productType" }
        queryPurchaseListenerMap[requestId] = listener@{ response ->
            printLog { "queryPurchase end, requestId:$requestId, response:$response" }
            if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
                listener(null)
                return@listener
            }
            val productInfoMap = mutableMapOf<String, IAPPurchaseInfo>()
            response.receipts.forEach { receipt ->
                if (productType == null || receipt.productType.toProductType() == productType) {
                    productInfoMap[receipt.sku] = receipt.toPurchaseInfo(response.userData)
                }
            }
            listener(productInfoMap.ifEmpty { null })
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        extraParamsMap: Map<String, Any>?,
        listener: (IAPResultCode, IAPPurchaseInfo?) -> Unit
    ) {
        val requestId = PurchasingService.purchase(productId)
        printLog { "launchPurchase start, requestId:$requestId, productId:$productId, extraParamsMap(${extraParamsMap?.size ?: -1}):$extraParamsMap" }
        purchaseListenerMap[requestId] = listener@{ response ->
            printLog { "launchPurchase end, requestId:$requestId, response:$response" }
            if (response.requestStatus != PurchaseResponse.RequestStatus.SUCCESSFUL) {
                listener(response.requestStatus.toResultCode(), null)
                return@listener
            }
            listener(IAPResultCode.Ok, response.toPurchaseInfo())
        }
    }

    override fun acknowledge(
        purchaseInfo: IAPPurchaseInfo,
        listener: (Boolean) -> Unit
    ) {
        PurchasingService.notifyFulfillment(purchaseInfo.orderId, FulfillmentResult.FULFILLED)
        listener(true)
    }

    override fun consume(
        purchaseInfo: IAPPurchaseInfo,
        listener: (Boolean) -> Unit
    ) {
        PurchasingService.notifyFulfillment(purchaseInfo.orderId, FulfillmentResult.FULFILLED)
        listener(true)
    }

    override fun addPurchaseAutoUpdateListener(listener: (IAPPurchaseInfo) -> Unit) {
        purchaseAutoUpdateListenerList.add(listener)
    }

    override fun destroy() {
        purchaseAutoUpdateListenerList.clear()
        queryProductDetailListenerMap.clear()
        queryPurchaseListenerMap.clear()
        purchaseListenerMap.clear()
    }

    override fun platform() = IAPPlatform.Amazon

    override fun getAmazonUserId(listener: (String?) -> Unit) {
        val requestId = PurchasingService.getUserData()
        printLog { "getAmazonUserId start, requestId:$requestId" }
        userDataListenerMap[requestId] = listener@{ response ->
            printLog { "getAmazonUserId end, requestId:$requestId, response:$response" }
            if (response.requestStatus != UserDataResponse.RequestStatus.SUCCESSFUL || response.userData.userId.isNullOrEmpty()) {
                listener(null)
                return@listener
            }
            listener(response.userData.userId)
        }
    }

    override fun onUserDataResponse(userDataResponse: UserDataResponse) {
        printLog { "onUserDataResponse:$userDataResponse" }
        userDataListenerMap[userDataResponse.requestId]?.also { listener ->
            listener(userDataResponse)
            userDataListenerMap.remove(userDataResponse.requestId)
        }
    }

    override fun onProductDataResponse(productDataResponse: ProductDataResponse) {
        printLog { "onProductDataResponse:$productDataResponse" }
        queryProductDetailListenerMap[productDataResponse.requestId]?.also { listener ->
            listener(productDataResponse)
            queryProductDetailListenerMap.remove(productDataResponse.requestId)
        }
    }

    override fun onPurchaseResponse(purchaseResponse: PurchaseResponse) {
        printLog { "onPurchaseResponse:$purchaseResponse" }
        purchaseListenerMap[purchaseResponse.requestId]?.also { listener ->
            listener(purchaseResponse)
            purchaseListenerMap.remove(purchaseResponse.requestId)
        } ?: run {
            val purchaseInfo = purchaseResponse.toPurchaseInfo()
            purchaseAutoUpdateListenerList.forEach { listener ->
                listener(purchaseInfo)
            }
        }
    }

    override fun onPurchaseUpdatesResponse(purchaseUpdatesResponse: PurchaseUpdatesResponse) {
        printLog { "onPurchaseUpdatesResponse:$purchaseUpdatesResponse" }
        queryPurchaseListenerMap[purchaseUpdatesResponse.requestId]?.also { listener ->
            listener(purchaseUpdatesResponse)
            queryPurchaseListenerMap.remove(purchaseUpdatesResponse.requestId)
        }
    }

    private fun printLog(msg: () -> String) {
        if (IAPConfig.debugLogEnable) {
            (iapLog ?: IAPConfig.getLogInstance().also { iapLog = it }).d("IAPManagerAmazon", msg())
        }
    }

}