package com.stewart.pelotonsolveit

import android.webkit.JavascriptInterface

class SolveItJSBridge {
    var dlgName: String = ""
    var msgId: String = ""

    @JavascriptInterface
    fun setFocusedMessage(dlgName: String, msgId: String) {
        this.dlgName = dlgName
        this.msgId = msgId
    }
}
