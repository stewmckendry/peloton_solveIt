package com.stewart.pelotonsolveit

import android.util.Log
import android.webkit.JavascriptInterface

class SolveItJSBridge {
    var dlgName: String = ""
    var msgId: String = ""

    @JavascriptInterface
    fun setFocusedMessage(dlgName: String?, msgId: String?) {
        val newDlgName = dlgName ?: ""
        val newMsgId = msgId ?: ""
        if (newDlgName != this.dlgName || newMsgId != this.msgId) {
            Log.d("SolveItJSBridge", "dialog name: $newDlgName, msgID: $newMsgId")
            this.dlgName = newDlgName
            this.msgId = newMsgId
        }
    }

}
