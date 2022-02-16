package com.example.wifidemo
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.*
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

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

    //wheelchair data
    val lidarPointArray=Array(360){LidarPoint(-1,-1f,-1)}
    var currentDirection=0
    var currentSpeed=0f
    var currentTime=0
    var currentBrakeStatus=0

    private val webServicesProvider=WebServicesProvider()

    @ExperimentalCoroutinesApi
    fun subscribeToSocketEvents() {
        val ioScope= CoroutineScope(Dispatchers.IO)
        ioScope.launch {
            try {
                webServicesProvider.startSocket(address,port).consumeEach {
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
            } catch (ex: Exception) {
                onSocketError(ex)
            }
        }
    }

    open fun onSocketError(ex: Throwable) {}

    @ExperimentalCoroutinesApi
    fun stopSocket() {
        webServicesProvider.stopSocket()
    }

    fun sendMessage(message:String){
        webServicesProvider.sendMessage(message)
    }

    open fun processMessage(message:String){}
    open fun processMessage(message:ByteString){}

    //funzione per resettare l'array di punti lidar

    fun resetLidarPointArray(){
        for (i in lidarPointArray.indices){
            lidarPointArray[i]=LidarPoint(-1,-1f,-1)
        }
    }

    //input stringa hex (es 5A109061), scorre per byte dove ogni byte sono due char della stringa
    fun parseFixedLidarData(data:String):PacketParsed?{
        numParsed++
        var temp: String
        var i=0

        //salvo una copia dei punti ricevuti nel pacchetto precedente, li posso usare per vedere se ho ricevuto dei dati nuovi
        val lastLidarData=lidarPointArray.clone()

        //se il pacchetto precedente ricevuto ha avuto dei problemi possono rimanere dei punti sbagliati nell'array
        resetLidarPointArray()

        //parsing header
        temp=data[i].toString()+data[i+1]
        //control char = 1 byte
        if(temp.toInt(16).toChar() != 'H'){
            Log.e("parseLidarDataError","Header line does not contain control character")
            return null
        }
        i+=2


        //millis = 4 bytes
        temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]+data[i+4]+data[i+5]+data[i+6]+data[i+7]
        currentTime=temp.toIntLittleEndian()
        i+=8

        //angle = 2 bytes
        temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
        val startingAngle=temp.toIntLittleEndian()
        var angle=startingAngle
        i+=4

        if(angle!=0){
            Log.e("ASD","ASD $angle")
        }

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


        //tengo traccia di quanti punti sono uguali rispetto al pacchetto precedente, se il numero è oltre un possibile errore statistico allora il pacchetto lidar è duplicato e non va considerato
        var puntiUguali=0

        while(i<data.length){//TODO check arrayoutofbounds

            if(i+4>=data.length){
                Log.e("parseLidarDataError","Wrong lidar packet data size")
                return null
            }

            //distance = 2 bytes
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
            tempDistance=temp.toIntLittleEndian()
            i+=4

            if(i+4>=data.length){
                Log.e("parseLidarDataError","Wrong lidar packet data size")
                return null
            }

            //derivata distanza = 2 bytes TODO al momento c'è il calcolo di rischio in un int 0-100
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
            val tempDerivata=temp.toIntLittleEndian() //TODO andrà inserita in un campo di LidarPoint se si decide di usarlo, momentaneamente inutilizzato
            i+=4

            if(i+4>data.length){
                Log.e("parseLidarDataError","Wrong lidar packet data size")
                return null
            }

            //intensity = 2 bytes
            temp=data[i].toString()+data[i+1]+data[i+2]+data[i+3]
            tempIntensity=temp.toIntLittleEndian()
            i+=4

            //se distanza è zero saltare un numero di angoli pari al valore di derivata (ogni linea sono 4 byte=8 char)
            if(tempDistance==0){

                //se anche derivata è zero il pacchetto utile è finito o ha un problema di formato
                if(tempDerivata==0){
                    Log.e("parseLidarDataError","Lidar packet has no more data after ${i/2} bytes")
                    return null
                }
                angle+=tempDerivata

                if(angle>360){
                    Log.e("parseLidarDataError","Error in zero angle row, angles would be more than 360")
                    return null
                }
            }else{
                val point= (LidarPoint(angle,tempDistance.toFloat(),tempIntensity))//TODO rimettere l'intensità
                angle++

                if(angle>=360 || point.angle>=360){
                    Log.e("parseLidarDataError","Lidar packet has more than 360 degrees")
                    return null
                }

                //Se il punto che ricevo è uguale al pacchetto precedente incremento il contatore di punti uguali
                if(point.distance==lastLidarData[point.angle].distance && point.intensity==lastLidarData[point.angle].intensity){
                    puntiUguali++
                }

                lidarPointArray[point.angle]=point.copy()
            }
        }

        //se c'è una quantità superiore ad un possibile errore di punti uguali lo segnalo
        if(puntiUguali>10){
            Log.d("parseLidarDataWarning","Lidar packet has $puntiUguali repeated points compared to previous packet")
            return PacketParsed(currentTime,currentDirection,startingAngle,angle,currentSpeed,currentBrakeStatus,lidarPointArray.clone(),true)
        }

        return PacketParsed(currentTime,currentDirection,startingAngle,angle,currentSpeed,currentBrakeStatus,lidarPointArray.clone())
    }

    data class PacketParsed(val time:Int=0,val direction:Int=0, val startAngle:Int=0, val endAngle:Int=0, val speed:Float=0f, val brakeStatus:Int=0, val lidarPoints:Array<LidarPoint>, val isLidarDataDuplicated:Boolean=false)

    class TimeSynchronization(val ws:WebSocketConnection){

        val synchronizationMessage="TIME_SYNCHRO0"
        var lastSentTimestamp=0L
        val numMeasurements=10
        var curr=0
        val measurementDelay=200
        var millisReceived=Array<Long>(numMeasurements){0}
        var rtssComputed=Array<Long>(numMeasurements){0}
        var packetsSentTime=Array<Long>(numMeasurements){0}

        var ts=-1L


        fun startSynchronization(){
            curr=0
            lastSentTimestamp=System.currentTimeMillis()
            ws.sendMessage(synchronizationMessage)
        }

        fun continueSynchronization(millis:Int){
            rtssComputed[curr]=(System.currentTimeMillis()-lastSentTimestamp)
            packetsSentTime[curr]=lastSentTimestamp
            millisReceived[curr]=millis.toLong()
            curr++
            if(curr<numMeasurements){
                Handler(Looper.getMainLooper()).postDelayed({
                    lastSentTimestamp=System.currentTimeMillis()
                    ws.sendMessage(synchronizationMessage)
                }, measurementDelay.toLong())
            }else{
                ts=computeSynchronizeTime()
            }
        }

        fun computeSynchronizeTime():Long{
            for (i in millisReceived.indices){
                millisReceived[i]=-millisReceived[i]+packetsSentTime[i]+(rtssComputed[i]/2)
            }

            return millisReceived.sum()/millisReceived.size
        }

    }

    companion object{

        //debug variables TODO cleanup
        var numChanges=0
        var numRead=0
        var numParsed=0

        fun parseTimeSynchronizationMessage(message:String):Int{
            var temp=""
            for(i in 15 until message.length){
                temp+=message[i].toString()
            }

            return temp.toInt()
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