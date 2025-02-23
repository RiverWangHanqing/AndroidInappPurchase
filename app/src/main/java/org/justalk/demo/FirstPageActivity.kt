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
import java.lang.ref.WeakReference

class FirstPageActivity : AppCompatActivity() {

    var iapManager: IAPManager? = null
    var productType = IAPProductType.Subs // or IAPProductType.Inapp
    var productId = "" // 你的商品 ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_page)
        initView()
        iapManager = IAPManager.preferredIAPManager(this)

        iapManager?.also { iapManager ->
            val weakActivity = WeakReference(this)
            iapManager.addPurchaseAutoUpdateListener { info ->
                // 处理不是通过 launchPurchase 产生的订单
                // ……

                // 更新界面显示用户权益
                weakActivity.get()?.updatePurchaseInfoView(info)

                // 订单处理完后，执行完成订单的操作
                if (productType == IAPProductType.Subs) {
                    iapManager.acknowledge(info) {
                    }
                } else {
                    iapManager.consume(info) {
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 如果 iapManager 作为单例保存，可以不调用 destroy()
        iapManager?.destroy()
        iapManager = null
    }

    private fun initView() {
        findViewById<View>(R.id.tv_query_product_info).setOnClickListener {
            val iapManager = iapManager ?: return@setOnClickListener
            val weakActivity = WeakReference(this)
            iapManager.queryProduct(productType, listOf(productId)) { map ->
                if (map === null) {
                    // 查询商品信息失败
                    return@queryProduct
                }
                // 更新界面显示的商品信息
                weakActivity.get()?.updateProductInfoView(map)
            }
        }

        findViewById<View>(R.id.tv_query_purchase).setOnClickListener {
            val iapManager = iapManager ?: return@setOnClickListener
            val weakActivity = WeakReference(this)
            iapManager.queryPurchase(productType) { map ->
                if (map === null) {
                    // 没有待处理的订单
                    return@queryPurchase
                }

                map.forEach { entry ->
                    // 处理订单，为用户提供相应的权益
                    // ……

                    // 更新界面显示用户权益
                    weakActivity.get()?.updatePurchaseInfoView(entry.value)

                    // 订单处理完后，执行完成订单的操作
                    if (entry.value.productType == IAPProductType.Subs) {
                        iapManager.acknowledge(entry.value) {
                        }
                    } else {
                        iapManager.consume(entry.value) {
                        }
                    }
                }
            }
        }

        findViewById<View>(R.id.tv_launch_purchase).setOnClickListener {
            val iapManager = iapManager ?: return@setOnClickListener
            val weakActivity = WeakReference(this)
            iapManager.launchPurchase(this, productId) { code, info ->
                if (code != IAPResultCode.Ok) {
                    // 处理购买商品失败的结果
                    return@launchPurchase
                }

                // 处理订单，为用户提供相应的权益
                // ……

                // 更新界面显示用户权益
                weakActivity.get()?.updatePurchaseInfoView(info!!)

                // 订单处理完后，执行完成订单的操作
                if (info!!.productType == IAPProductType.Subs) {
                    iapManager.acknowledge(info) {
                    }
                } else {
                    iapManager.consume(info) {
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