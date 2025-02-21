package org.justalk.inapppurchase

enum class IAPResultCode {
    /** 成功 */
    Ok,
    /** 服务未连接 */
    NotConnected,
    /** 用户取消购买 */
    UserCanceled,
    /** 已拥有所购买商品 */
    ItemAlreadyOwned,
    /** 未拥有该商品 */
    ItemNotOwned,
    /** 没有有效的商品详情信息 */
    NoValidProductDetail,
    /** 不会自动恢复的异常 */
    Unavailable,
    /** 未知异常 */
    Unknown,
}