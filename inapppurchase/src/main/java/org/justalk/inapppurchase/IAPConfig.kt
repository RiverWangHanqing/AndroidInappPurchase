package org.justalk.inapppurchase

object IAPConfig {

    /**
     * 是否开启内部的内购相关接口日志
     */
    var debugLogEnable = true

    /**
     * 可以通过设置该变量来修改日志输出的方式
     */
    var logClass: Class<out IAPLog?>? = null

    fun getLogInstance(): IAPLog {
        return logClass?.let { logClass ->
            try {
                logClass.getDeclaredConstructor().newInstance()
            } catch (ignored: Throwable) {
                null
            }
        } ?: IAPLogDefault()
    }

}