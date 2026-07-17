package com.stewart.pelotonsolveit.speech.realtime

import com.stewart.pelotonsolveit.SolveItUiContext

/**
 * Dialog explicitly chosen for Realtime tool operations. This is independent
 * from the dialog currently visible in the WebView.
 */
class RealtimeWorkspace {
    @Volatile
    var workingDialogName: String? = null
        private set

    fun useCurrentDialog(uiContext: SolveItUiContext): String {
        val dialogName = uiContext.visibleDialogName
            ?: throw RealtimeWorkspaceException("No SolveIt dialog is currently visible")
        workingDialogName = dialogName
        return dialogName
    }

    fun clear() {
        workingDialogName = null
    }

    fun requireWorkingDialog(): String = workingDialogName
        ?: throw RealtimeWorkspaceException("No working SolveIt dialog is selected")

    fun snapshot(uiContext: SolveItUiContext) = RealtimeWorkspaceSnapshot(
        visibleDialogName = uiContext.visibleDialogName,
        selectedMessageId = uiContext.selectedMessageId,
        workingDialogName = workingDialogName
    )
}

data class RealtimeWorkspaceSnapshot(
    val visibleDialogName: String?,
    val selectedMessageId: String?,
    val workingDialogName: String?
)

class RealtimeWorkspaceException(message: String) : IllegalStateException(message)
