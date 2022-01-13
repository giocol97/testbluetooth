package com.example.wifidemo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.*
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

open class WebSocketConnection(val address:String, val port:String) {

    private val webServicesProvider=WebServicesProvider()

    @ExperimentalCoroutinesApi
    fun subscribeToSocketEvents() {
        val ioScope= CoroutineScope(Dispatchers.IO)
        ioScope.launch {
            try {
                webServicesProvider.startSocket(address,port).consumeEach {
                    if (it.exception == null && it.text!=null) {
                        processMessage(it.text)
                    } else {
                        onSocketError(it.exception!!)
                    }
                }
            } catch (ex: java.lang.Exception) {
                onSocketError(ex)
            }
        }
    }

    private fun onSocketError(ex: Throwable) {
        Log.d("ASD","ASD Error occurred : ${ex.message}")
    }

    @ExperimentalCoroutinesApi
    fun stopSocket() {
        webServicesProvider.stopSocket()
    }

    fun sendMessage(message:String){
        webServicesProvider.sendMessage(message)
    }

    public open fun processMessage(message:String){}

}



@ExperimentalCoroutinesApi
class WebSocketListener : WebSocketListener() {

    val socketEventChannel: Channel<SocketUpdate> = Channel(10)

    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send("Connection Started Phone")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        GlobalScope.launch {
            socketEventChannel.send(SocketUpdate(text))
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        GlobalScope.launch {
            socketEventChannel.send(SocketUpdate(exception = SocketAbortedException()))
        }
        webSocket.close(WebServicesProvider.NORMAL_CLOSURE_STATUS, null)
        socketEventChannel.close()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        GlobalScope.launch {
            socketEventChannel.send(SocketUpdate(exception = t))
        }
    }

}

class SocketAbortedException : Exception()

data class SocketUpdate(
    val text: String? = null,
    val byteString: ByteString? = null,
    val exception: Throwable? = null
)

class WebServicesProvider {

    private var _webSocket: WebSocket? = null

    private val socketOkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(39, TimeUnit.SECONDS)
        .hostnameVerifier { _, _ -> true }
        .build()

    @ExperimentalCoroutinesApi
    private var _webSocketListener: com.example.wifidemo.WebSocketListener? = null

    @ExperimentalCoroutinesApi
    fun startSocket( address:String, port:String): Channel<SocketUpdate> =
        with(com.example.wifidemo.WebSocketListener()) {
            startSocket(this, address, port)
            this@with.socketEventChannel
        }

    @ExperimentalCoroutinesApi
    fun startSocket(webSocketListener: com.example.wifidemo.WebSocketListener, address:String, port:String) {
        _webSocketListener = webSocketListener
        _webSocket = socketOkHttpClient.newWebSocket(
            //Request.Builder().url("ws://192.168.124.119:80/").build(),
            //Request.Builder().url("ws:/10.42.0.54:9090/").build(),
            Request.Builder().url("ws:/$address:$port/").build(),
            webSocketListener
        )

        socketOkHttpClient.dispatcher.executorService.shutdown()
    }

    @ExperimentalCoroutinesApi
    fun stopSocket() {
        try {
            _webSocket?.close(NORMAL_CLOSURE_STATUS, null)
            _webSocket = null
            _webSocketListener?.socketEventChannel?.close()
            _webSocketListener = null
        } catch (ex: Exception) {
        }
    }

    fun sendMessage(message:String){
        if(_webSocket!=null){
            _webSocket!!.send(message)
        }

    }

    companion object {
        const val NORMAL_CLOSURE_STATUS = 1000
    }
}

/*
class MainInteractor constructor(private val repository: MainRepository) {

    @ExperimentalCoroutinesApi
    fun stopSocket() {
        repository.closeSocket()
    }

    @ExperimentalCoroutinesApi
    fun startSocket(): Channel<SocketUpdate> = repository.startSocket()

}

class MainRepository constructor(private val webServicesProvider: WebServicesProvider) {

    @ExperimentalCoroutinesApi
    fun startSocket(): Channel<SocketUpdate> =
        webServicesProvider.startSocket()

    @ExperimentalCoroutinesApi
    fun closeSocket() {
        webServicesProvider.stopSocket()
    }
}*/