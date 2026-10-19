package org.justalk.inapppurchase

enum class IAPUnfetchedProductCode {
    /** 未知原因 */
    Unknown,
    /** 商品 ID 格式无效 */
    InvalidProductIdFormat,
    /** 未找到商品 */
    ProductNotFound,
    /** 商品没有符合资格的优惠 */
    NoEligibleOffer,
    /** 商品数据不可用 */
    ProductUnavailable
}
