package org.justalk.inapppurchase

class IAPPurchaseInfo(
    /** 商品类型，Google Play 不是通过主动购买收到的新订单，无法判断商品的类型 */
    val productType: IAPProductType?,
    /** 商品ID */
    val productId: String,
    /** 订单ID */
    /** 对应亚马逊支付的 receiptId */
    val orderId: String,
    /** 购买令牌 */
    /** 对应亚马逊支付的 userId */
    val purchaseToken: String,
    /** 购买时间 */
    val purchaseTime: Long,
    /** 是否自动续订 */
    val autoRenewing: Boolean,
    /** 订单是否被确认购买 */
    /** Google Play结算库2.0版开始，购买后3天内如果不确认购买，订单将被退款，亚马逊支付没有该数据 */
    val acknowledged: Boolean,
    /** 与订单绑定的用户账号信息，用于后端收到内购订单后查找用户绑定会员信息 */
    val accountId: String?,
) {
    override fun toString(): String {
        return "IAPPurchaseInfo(productType=${productType}, productId='$productId', orderId='$orderId', purchaseToken='$purchaseToken', purchaseTime=$purchaseTime, autoRenewing=$autoRenewing, acknowledged=$acknowledged, accountId=$accountId)"
    }
}