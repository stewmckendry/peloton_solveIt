package com.stewart.pelotonsolveit

import android.util.Log
import android.webkit.JavascriptInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SolveItJSBridge {
    var dlgName by mutableStateOf("")
        private set
    var msgId by mutableStateOf("")
        private set

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
