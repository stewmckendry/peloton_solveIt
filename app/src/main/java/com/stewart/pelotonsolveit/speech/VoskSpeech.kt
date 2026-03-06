package com.stewart.pelotonsolveit

import android.content.Context
import android.util.Log
import org.vosk.Model
import java.io.File

fun loadVoskModel(context: Context): Model {
    val modelDir = File(context.filesDir, "vosk-model")
    if (!modelDir.exists()) {
        modelDir.mkdirs()
        copyAssets(context, "vosk-model-small-en-us-0.15", modelDir)
    }
    return Model(modelDir.absolutePath)
}

fun copyAssets(context: Context, srcPath: String, destDir: File, depth: Int = 0) {
    val items = context.assets.list(srcPath)
    Log.d("PelotonSolveIt", "Copying: $srcPath to $destDir")
    if (items == null || items.isEmpty()) {
        // it's a file, copy it
        context.assets.open(srcPath).use { input ->
            File(destDir, srcPath.substringAfterLast("/")).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } else {
        // it's a folder
        val targetDir = if (depth == 0) destDir else {
            File(destDir, srcPath.substringAfterLast("/")).also { it.mkdirs() }
        }
        items.forEach { item ->
            copyAssets(context, "$srcPath/$item", targetDir, depth + 1)
        }
    }
}
