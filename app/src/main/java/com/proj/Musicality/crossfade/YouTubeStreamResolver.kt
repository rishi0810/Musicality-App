package com.proj.Musicality.crossfade

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
class StreamAwareDataSource private constructor(
    private val fileDataSource: DataSource,
    private val httpDataSource: DataSource
) : DataSource {

    companion object {
        private const val TAG = "StreamAwareDS"

        private fun isHttpUri(uri: Uri): Boolean {
            val scheme = uri.scheme
            if (scheme != null) return scheme.startsWith("http")
            val str = uri.toString()
            return str.startsWith("http://") || str.startsWith("https://")
        }
    }

    private var activeSource: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val uriStr = uri.toString()
        val isHttp = isHttpUri(uri)

        Log.d(TAG, "open: scheme=${uri.scheme} isHttp=$isHttp pos=${dataSpec.position} uri=${uriStr.take(120)}")

        val resolved = if (isHttp && uriStr.contains("range=")) {
            if (dataSpec.position > 0L) {
                val newUri = uriStr.replace(Regex("range=[^&]*"), "range=${dataSpec.position}-")
                Log.d(TAG, "rewriting range for position=${dataSpec.position}")
                dataSpec.buildUpon()
                    .setUri(Uri.parse(newUri))
                    .setPosition(0)
                    .setUriPositionOffset(0)
                    .build()
            } else {
                dataSpec
            }
        } else {
            dataSpec
        }

        activeSource = if (isHttpUri(resolved.uri)) {
            httpDataSource
        } else {
            fileDataSource
        }

        return activeSource!!.open(resolved)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeSource?.read(buffer, offset, length) ?: -1
    }

    override fun addTransferListener(transferListener: TransferListener) {
        fileDataSource.addTransferListener(transferListener)
        httpDataSource.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? {
        val result = activeSource?.uri
        Log.d(TAG, "getUri: ${result?.toString()?.take(120) ?: "null"}")
        return result
    }

    override fun close() {
        activeSource?.close()
        activeSource = null
    }

    class Factory(context: Context) : DataSource.Factory {
        private val fileFactory = DefaultDataSource.Factory(context)
        private val httpFactory = OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        )

        override fun createDataSource(): DataSource {
            return StreamAwareDataSource(
                fileDataSource = fileFactory.createDataSource(),
                httpDataSource = httpFactory.createDataSource()
            )
        }
    }
}
