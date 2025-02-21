package org.justalk.inapppurchase

object IAPConfig {

    var debugLogEnable = true

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