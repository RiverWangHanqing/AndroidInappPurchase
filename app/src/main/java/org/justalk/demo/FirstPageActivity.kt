package org.justalk.demo

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import org.justalk.inapppurchase.IAPManager
import org.justalk.inapppurchase.IAPProductInfo
import org.justalk.inapppurchase.IAPProductType
import org.justalk.inapppurchase.IAPPurchaseInfo
import org.justalk.inapppurchase.IAPResultCode

class FirstPageActivity : AppCompatActivity() {

    var iapManager: IAPManager? = null
    var productType = IAPProductType.Subs // or IAPProductType.Inapp
    var productId = "" // 你的商品 ID
    private var purchaseAutoUpdateListener: ((IAPPurchaseInfo) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_page)
        initView()
        iapManager = IAPManager.preferredIAPManager(applicationContext)?.also { iapManager ->
            purchaseAutoUpdateListener = { info ->
                // 处理不是通过 launchPurchase 产生的订单
                // ……

                // 更新界面显示用户权益
                updatePurchaseInfoView(info)

                // 订单处理完后，执行完成订单的操作
                if (productType == IAPProductType.Subs) {
                    iapManager.acknowledge(info, this@FirstPageActivity) {
                    }
                } else {
                    iapManager.consume(info, this@FirstPageActivity) {
                    }
                }
            }
            purchaseAutoUpdateListener?.let { listener ->
                iapManager.addPurchaseAutoUpdateListener(listener)
            }
        }
    }

    override fun onDestroy() {
        // 移除监听器以防止内存泄露
        purchaseAutoUpdateListener?.also { listener ->
            iapManager?.removePurchaseAutoUpdateListener(listener)
            purchaseAutoUpdateListener = null
        }
        
        super.onDestroy()
        // 如果 iapManager 作为单例保存，可以不调用 destroy()
        iapManager?.destroy()
        iapManager = null
    }

    private fun initView() {
        findViewById<View>(R.id.tv_query_product_info).setOnClickListener {
            iapManager?.queryProduct(productType, listOf(productId), this) { map ->
                if (map === null) {
                    // 查询商品信息失败
                    return@queryProduct
                }
                // 更新界面显示的商品信息
                updateProductInfoView(map)
            }
        }

        findViewById<View>(R.id.tv_query_purchase).setOnClickListener {
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
        }

        findViewById<View>(R.id.tv_launch_purchase).setOnClickListener {
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
        }
    }

    private fun updateProductInfoView(map: Map<String, IAPProductInfo>) {
        map[productId]?.also { info ->
            findViewById<AppCompatTextView>(R.id.tv_product_price).text = info.formattedPrice
        }
    }

    private fun updatePurchaseInfoView(info: IAPPurchaseInfo) {
    }

}