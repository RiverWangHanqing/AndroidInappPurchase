package org.justalk.inapppurchase

class IAPProductInfo(
    /** 商品类型 */
    val productType: IAPProductType,
    /** 商品ID */
    var productId: String,
    /** 商品价格，包含货币符号 */
    var formattedPrice: String,
    /**
     * 商品的微单位价格，1,000,000微单位等于货币的一个单位，eg. 39990000 表示 39.99
     * GooglePlay 有标准 api 会返回
     * Amazon 通过程序手动转换而来，有可能换转失败(查询出来的价格与本地语言不符)
     */
    var amountMicros: Long = 0,
    /**
     * 商品价格的ISO 4217货币代码，eg. USD
     * GooglePlay 有标准 api 会返回
     * Amazon 通过程序手动转换而来，有可能换转失败(查询出来的价格与本地语言不符)
     */
    var currencyCode: String = "",
    /** 是否可免费试用 */
    var freeTrial: Boolean = false,
) {
    override fun toString(): String {
        return "IAPProductInfo(productType=${productType}, productId='$productId', formattedPrice='$formattedPrice', amountMicros=$amountMicros, currencyCode='$currencyCode', freeTrial=$freeTrial)"
    }
}