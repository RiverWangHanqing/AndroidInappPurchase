package org.justalk.inapppurchase

/**
 * 支持实现自定义日志输出方式的接口类
 */
interface IAPLog {
    fun d(tag: String, msg: String)
}