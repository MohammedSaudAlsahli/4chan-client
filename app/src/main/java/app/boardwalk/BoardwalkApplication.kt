package app.boardwalk

import android.app.Application
import android.os.Build
import app.boardwalk.data.*
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val networkCleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class NetworkBundle(app: Application, proxy: ProxySettings, gate: ApiGate) {
    val client: OkHttpClient = NetworkFactory.create(proxy)
    val api = BoardApi(client, gate)
    val images = ImageLoader.Builder(app).okHttpClient(client).components {
        if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
    }.build()
    fun close() {
        networkCleanup.launch {
            images.shutdown()
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
    }
}

class BoardwalkApplication : Application() {
    val gate = ApiGate()
    val store by lazy { LocalStore(this) }
}
