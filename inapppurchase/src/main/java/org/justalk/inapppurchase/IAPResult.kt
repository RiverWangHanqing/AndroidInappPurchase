package org.justalk.inapppurchase

/**
 * 内购操作结果。
 * @property resultCode 统一的内购结果状态
 * @property subResultCode Google Play 原始购买子状态码，默认值为 0，表示没有适用的子状态；Amazon 或本地校验结果为 null
 * @property debugMessage Google Play 原始调试消息；Amazon 或本地校验结果为空字符串
 */
data class IAPResult(
    val resultCode: IAPResultCode,
    val subResultCode: Int? = null,
    val debugMessage: String = ""
)
