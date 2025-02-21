package org.justalk.inapppurchase

import android.util.Log

class IAPLogDefault: IAPLog {
    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }
}