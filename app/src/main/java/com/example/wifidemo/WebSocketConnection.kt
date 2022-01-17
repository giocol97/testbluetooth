package com.example.wifidemo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.*
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

//lidar data packets constants
private const val END_LINE=0x0A // '\n'
private const val DATA_SEPARATOR=0x3B //';'
private const val ZERO_SIGNAL=0x5A //'Z'

//status change constants
private const val DEVICE_FOUND=0
private const val CONNECTION_START=1
private const val CONNECTION_ERROR=2
private const val CONNECTION_COMPLETE=3
private const val LIDAR_CHARACTERISTIC_CHANGED=4
private const val LIDAR_CHARACTERISTIC_READ=5
private const val LIDAR_CHARACTERISTIC_PARSED=6
private const val LIDAR_CHARACTERISTIC_DRAWABLE=7

open class WebSocketConnection(val address:String, val port:String) {

    private val webServicesProvider=WebServicesProvider()

    @ExperimentalCoroutinesApi
    fun subscribeToSocketEvents() {
        val ioScope= CoroutineScope(Dispatchers.IO)
        ioScope.launch {
            try {
                webServicesProvider.startSocket(address,port).consumeEach {

                    val timeInMillis = measureTimeMillis {
                        val pingTest=InetAddress.getByName(address).isReachable(1000)

                    }
                    Log.d("ASD","ASDI ping time: $timeInMillis ")

                    if (it.exception == null && it.text!=null) {
                        if(it.text=="bytestring"){
                            processMessage(it.byteString!!)
                        }else{
                            processMessage(it.text)
                        }

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
    public open fun processMessage(message:ByteString){}

    companion object{

        //wheelchair data
        val lidarPointArray=Array(360){LidarPoint(-1,-1,-1)}
        var currentDirection=0
        var currentSpeed=0f
        var currentTime=0
        var currentBrakeStatus=0
        //var lastHeader=""

        //debug variables TODO cleanup
        var numChanges=0
        var numRead=0
        var numParsed=0
        //var saveString= mutableListOf<String>()


        //input stringa hex (es 5A109061), scorre per byte dove ogni byte sono due char della stringa
        fun parseFixedLidarData(data:String){
            numParsed++
            var temp: String
            var i=0


            //parsing header
            temp=data[i].toString()+data[i+1]
            //control char = 1 byte
            if(temp.toInt(16).toChar() != 'H'){
                Log.e("parseLidarDataError","Header line does not contain control character")
                return
            }
            i+=2


            //millis = 4 bytes
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]+data[i+4]+data[i+5]+data[i+6]+data[i+7]
            currentTime=temp.toIntLittleEndian()
            i+=8

            //angle = 2 bytes
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
            var angle=temp.toIntLittleEndian()
            i+=4

            //sterzo = 2 bytes
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
            currentDirection=temp.toIntLittleEndian()
            i+=4

            //speed = 2 bytes
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
            currentSpeed=temp.toIntLittleEndian().toFloat()/10f
            i+=4

            //brake = 1 byte
            temp=data[i].toString()+data[i+1]
            currentBrakeStatus=temp.toIntLittleEndian()
            i+=2


            //parsing angoli

            var tempIntensity=0
            var tempDistance=0

            while(i<data.length){//TODO check arrayoutofbounds

                if(i+4>data.length){
                    Log.e("parseLidarDataError","Wrong lidar packet data size")
                    return
                }

                //distance = 2 bytes
                temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
                tempDistance=temp.toIntLittleEndian()
                i+=4

                //intensity = 2 bytes
                temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
                tempIntensity=temp.toIntLittleEndian()
                i+=4

                //se distanza è zero saltare un numero di linee pari al valore di intensity (ogni linea sono 4 byte=8 char)
                if(tempDistance==0){
                    i+=8*tempIntensity
                }else{
                    val point= (LidarPoint(angle++,tempDistance,tempIntensity))

                    if(angle==360) {
                        lidarPointArray[0]=point.copy()
                    }else{
                        lidarPointArray[point.angle]=point.copy()
                    }
                }
            }

        }


      /*  fun parseFixedLidarData(data:ByteArray){
            numParsed++

            data.forEach { Log.d("ASD","ASD ${it}") }



            //parsing header

            //control char = 1 byte
            /*if(data[0] != 'H'.code.toByte()){
                Log.e("parseLidarDataError","Header line does not contain control character")
                return
            }*/

            val temp=mutableListOf<Byte>()
            var i=1

            //millis = 4 bytes
            temp.add(data[i++])
            temp.add(data[i++])
            temp.add(data[i++])
            temp.add(data[i++])
            currentTime=temp.toIntLittleEndian()
            temp.clear()

            //angle = 2 bytes
            temp.add(data[i++])
            temp.add(data[i++])
            var angle=temp.toIntLittleEndian()
            temp.clear()

            //sterzo = 2 bytes
            temp.add(data[i++])
            temp.add(data[i++])
            currentDirection=temp.toIntLittleEndian()
            temp.clear()

            //speed = 2 bytes
            temp.add(data[i++])
            temp.add(data[i++])
            currentSpeed=temp.toIntLittleEndian().toFloat()/10f
            temp.clear()

            //brake = 1 byte
            temp.add(data[i++])
            currentBrakeStatus=temp.toIntLittleEndian()
            temp.clear()


            //parsing angoli

            var tempIntensity=0
            var tempDistance=0


            Log.d("ASD","ASD $i ${data.size}")
            while(i<data.size){//TODO check arrayoutofbounds
                //distance = 2 bytes
                temp.add(data[i++])
                temp.add(data[i++])
                tempDistance=temp.toIntLittleEndian()
                temp.clear()

                //intensity = 2 bytes
                temp.add(data[i++])
                temp.add(data[i++])
                tempIntensity=temp.toIntLittleEndian()
                temp.clear()

                //se distanza è zero saltare un numero di linee pari al valore di intensity (ogni linea sono 4 byte)
                if(tempDistance==0){
                    i+=4*tempIntensity
                }else{
                    val point= (LidarPoint(angle++,tempDistance,tempIntensity))

                    if(angle==360) {
                        lidarPointArray[0]=point.copy()
                    }else{
                        lidarPointArray[point.angle]=point.copy()
                    }
                }
            }
        }

*/
        fun Array<Byte>.toIntLittleEndian(): Int {
            var result = 0
            for (i in this.indices) {
                result = result or (this[i].toInt() shl 8 * i)
            }
            return result
        }

        fun Array<Int>.toIntLittleEndian(): Int {
            var result = 0
            for (i in this.indices) {
                result = result or (this[i] shl 8 * i)
            }
            return result
        }


        fun String.toIntLittleEndian():Int{

            val arr=Array<Int>(this.length/2){0}
            var j=0
            for(i in 0..this.length-1 step 2){
                arr[j]=(this[i].toString()+this[i+1]).toInt(16)
                j++
            }

            return arr.toIntLittleEndian()
        }

        fun parseLidarData(data:ByteArray){
            numParsed++

            //check if header char is correct
            if(data[0]!='H'.toByte()){
                Log.e("parseLidarDataError","Header line does not contain control character")
                return
            }

            //lastHeader="H;"

            val temp=mutableListOf<Char>()
            var i=2

            //get current time from start for packet
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=DATA_SEPARATOR.toByte())

            currentTime=temp.joinToString("").toInt()
            //lastHeader+=temp.joinToString("")+DATA_SEPARATOR.toChar()
            temp.clear()
            i++

            //TODO trasformare tempo trasferito in timestamp

            //get starting angle for packet
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=DATA_SEPARATOR.toByte())

            var angle=temp.joinToString("").toInt()
            //lastHeader+=temp.joinToString("")+DATA_SEPARATOR.toChar()
            temp.clear()
            i++

            //saveString.add("Startangle $angle - data: ${data.toHexString()}")


            //get current direction
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=DATA_SEPARATOR.toByte())

            currentDirection=temp.joinToString("").toInt()
            //lastHeader+=temp.joinToString("")+DATA_SEPARATOR.toChar()
            temp.clear()
            i++

            //get current speed
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=DATA_SEPARATOR.toByte())

            currentSpeed=temp.joinToString("").toFloat()
            currentSpeed/=10f
            //lastHeader+=temp.joinToString("")+DATA_SEPARATOR.toChar()
            temp.clear()
            i++


            //get current brake status
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=END_LINE.toByte())

            currentBrakeStatus=temp.joinToString("").toInt()
            //lastHeader+=temp.joinToString("")
            temp.clear()
            i++



            var tempIntensity=0
            var tempDistance=0

            while(i<data.size){//TODO check arrayoutofbounds
                do{
                    temp.add(data[i].toChar())
                    i++
                    if(i==data.size){
                        Log.e("parseLidarDataError","Error in packet format")
                        return
                    }
                }while(data[i]!=DATA_SEPARATOR.toByte())
                if(data[i-1]!= ZERO_SIGNAL.toByte()){//normal angle
                    tempDistance=temp.joinToString("").toInt()
                    temp.clear()
                    i++

                    do{
                        temp.add(data[i].toChar())
                        i++
                    }while(i!=data.size && data[i]!=END_LINE.toByte())

                    tempIntensity=temp.joinToString("").toInt()
                    temp.clear()
                    i++

                    val point= (LidarPoint(angle++,tempDistance,tempIntensity))

                    if(angle==360) {
                        lidarPointArray[0]=point.copy()
                    }else{
                        lidarPointArray[point.angle]=point.copy()
                    }
                }else{//sequence of n zero valued angles
                    temp.clear()
                    i++

                    do{
                        temp.add(data[i].toChar())
                        i++
                    }while(i!=data.size && data[i]!=END_LINE.toByte())

                    angle+=temp.joinToString("").toInt()
                    temp.clear()
                    i++
                }

            }

            //onConnectionStatusChange(LIDAR_CHARACTERISTIC_PARSED)

            if(angle>359){
                //onConnectionStatusChange(LIDAR_CHARACTERISTIC_DRAWABLE)
            }

        }
    }


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

    override fun onMessage(webSocket: WebSocket, text: ByteString) {
        GlobalScope.launch {
            socketEventChannel.send(SocketUpdate("bytestring",text))
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