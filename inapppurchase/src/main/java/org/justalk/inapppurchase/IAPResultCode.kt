package org.justalk.inapppurchase

enum class IAPResultCode {
    /** 成功 */
    Ok,
    /** 服务超时 */
    ServiceTimeout,
    /** 服务已断开连接 */
    ServiceDisconnected,
    /** 服务不可用 */
    ServiceUnavailable,
    /** 网络错误 */
    NetworkError,
    /** 用户取消购买 */
    UserCanceled,
    /** 已拥有该商品 */
    ItemAlreadyOwned,
    /** 未拥有该商品 */
    ItemNotOwned,
    /** 没有有效的商品详情信息 */
    NoValidProductDetail,
    /** 不支持该功能 */
    FeatureNotSupport,
    /** 结算功能不可用 */
    BillingUnavailable,
    /** 商品不可用 */
    ItemUnavailable,
    /** 开发者错误，提供给 API 的参数无效 */
    DeveloperError,
    /** 致命错误，在执行 API 操作期间发生了意外的内部错误 */
    Error,
    /** 未知异常 */
    Unknown,
}