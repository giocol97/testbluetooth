package com.example.wifidemo

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.*
import android.text.Layout
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ByteString
import kotlin.collections.*
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin


//useful android constants
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

//ESP32 connection constants
private const val ESP_DEFAULT_CONNECTION_TYPE="WEBSOCKET"
private const val ESP_ADDRESS="192.168.1.1"
private const val ESP_PORT="1337" //1337

//TODO per ora non è ancora usato
private const val  WIFI_SSD = "LIDAR_WIFI"
private const val  WIFI_PWD = "123456789"

//radar view constants
private const val MAX_DISTANCE=5000
private const val ANGLE_OFFSET=90//TODO prova 180

//BLE status change constants
private const val DEVICE_FOUND=0
private const val CONNECTION_START=1
private const val CONNECTION_ERROR=2
private const val CONNECTION_COMPLETE=3
private const val LIDAR_CHARACTERISTIC_CHANGED=4
private const val LIDAR_CHARACTERISTIC_READ=5
private const val LIDAR_CHARACTERISTIC_PARSED=6
private const val LIDAR_CHARACTERISTIC_DRAWABLE=7

class MainActivity : AppCompatActivity() {

    private val bluetoothManager by lazy{
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private lateinit var bleLidarConnection: BLELidarConnection

    private lateinit var webSocketConnection:WebSocketConnection

    private val timeSyncronization:WebSocketConnection.TimeSynchronization by lazy{
        WebSocketConnection.TimeSynchronization(webSocketConnection)
    }

    private var espConnectionType= ESP_DEFAULT_CONNECTION_TYPE

    //get bluetooth adapter manager to check bluetooth status
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    //variable to check if fine location permission has been granted
    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    //ui components
    private val scanButton: Button by lazy{
        findViewById(R.id.scan_button)
    }

    private val radarView: SurfaceView by lazy{
        findViewById(R.id.radar_view)
    }

    private val speedView: TextView by lazy{
        findViewById(R.id.speed_view)
    }

    private val directionView: TextView by lazy{
        findViewById(R.id.direction_view)
    }

    private val timeView: TextView by lazy{
        findViewById(R.id.time_view)
    }

    /*private val progressBar: ProgressBar by lazy{
        findViewById(R.id.progressBar)
    }*/

    private val connectionTypeSwitch:Switch by lazy{
        findViewById(R.id.connectionTypeSwitch)
    }

    private val joystickSwitch:Switch by lazy{
        findViewById(R.id.joystickSwitch)
    }

    //elementi ui per invio comandi
    private val headerTextView: TextView by lazy{
        findViewById(R.id.headerTextView)
    }
    private val commandButton: Button by lazy{
        findViewById(R.id.commandButton)
    }
    private val commandBox: EditText by lazy{
        findViewById(R.id.commandBox)
    }

    private val brakeOnButton: Button by lazy{
        findViewById(R.id.brakeOnButton)
    }
    private val brakeOffButton: Button by lazy{
        findViewById(R.id.brakeOffButton)
    }

    private val joystickContainer:TableLayout by lazy{
        findViewById(R.id.joystickContainer)
    }


    //joystick controls

    private val leftButton: Button by lazy{
        findViewById(R.id.leftButton)
    }

    private val upButton: Button by lazy{
        findViewById(R.id.upButton)
    }

    private val rightButton: Button by lazy{
        findViewById(R.id.rightButton)
    }

    private val downButton: Button by lazy{
        findViewById(R.id.downButton)
    }

    private val centerButton: Button by lazy{
        findViewById(R.id.centerButton)
    }

    var lastTimeStamp=0L


    private var isRadarDrawing=false

    //check if we have a certain permission
    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun polarToCanvas(distance:Int,angle:Int,width:Int,height:Int):Pair<Float,Float>{
        //transform polar->cartesian coordinates
        //MAX_DISTANCE defines a saturation point for distance representation
        //ANGLE_OFFSET defines where the angles start from, 0 is x axis
        val angleRads = (angle + ANGLE_OFFSET) * Math.PI / 180

        var x=(min(distance, MAX_DISTANCE)*cos(angleRads)).toFloat()
        var y=(min(distance,MAX_DISTANCE)*sin(angleRads)).toFloat()

        //fit in the canvas
        x=x*(width/2)/MAX_DISTANCE
        y=y*(height/2)/MAX_DISTANCE

        //center in the middle of the canvas
        x+=(width/2)
        y+=(height/2)

        return Pair(x,y)
    }

    private var isDrawing=false

    private var connectionComplete=false

    fun directionToRadAngle(dir:Int):Float{//TODO
        return 0f
    }

    fun degToRad(angle:Int):Float{
        return ( angle.rem(360)* Math.PI/180).toFloat()
    }

    fun degToShiftedRad(angle:Int):Float{
        val shiftedAngle=if(angle<=180) angle else angle-360
        return ( shiftedAngle * Math.PI/180).toFloat()
    }

    fun riskPerPoint(vel:Float,dir:Int,angle:Int,dist:Int):Float{

        val dirAngle=directionToRadAngle(dir)
        val angleRads = degToShiftedRad(angle + ANGLE_OFFSET)
        val distMeters=dist/1000

        val mult=exp(-0.5*((angleRads-dirAngle).pow(2)))

        //val velNon0=if(vel==0f)0.1f else vel
        val velNon0=1f
        val risk=2f.pow((-distMeters/velNon0)).toDouble()

        return (mult*risk).toFloat()
    }


    private fun drawLidarData(lidarPointList: List<LidarPoint>?, currentSpeed:Float,currentDirection:Int){
        //code is inside synchronized block to avoid trying to draw on it twice
        if(!isDrawing && radarView.visibility==View.VISIBLE){
            isDrawing=true
            synchronized(radarView.holder){
                //get the canvas from the radarView surface
                val canvas: Canvas =radarView.holder.lockCanvas()
                val green=Paint()
                val red=Paint()
                val orange=Paint()
                val yellow=Paint()
                green.color=Color.GREEN
                red.color=Color.RED
                orange.color=Color.rgb(255,165,0)
                yellow.color=Color.YELLOW
                var coords=Pair(0f,0f)

                var chosenColor: Paint

                canvas.drawColor(Color.BLACK)

                if (!lidarPointList.isNullOrEmpty()) {
                    //draw each point on the canvas
                    lidarPointList.forEach{ point->
                        //don't draw null points
                        if(point.distance>0 /*&& point.intensity>0*/){//TODO controllare perchè alcuni punti rimangono anche se arriva un pacchetto nuovo //TODO rimettere check intensità

                            coords=polarToCanvas(point.distance.toInt(),point.angle,canvas.width,canvas.height)

                            //val risk=riskPerPoint(currentSpeed,currentDirection,point.angle,point.distance)
                            val risk=point.intensity/100f

                            /*if(point.distance<1000){
                                chosenColor=red
                            }else if(point.distance<1500){
                                chosenColor=orange
                            }else if(point.distance<2500){
                                chosenColor=yellow
                            }else{
                                chosenColor=green
                            }*/

                            //Log.d("ASD","ASD risk: $risk - ${bleLidarConnection.currentSpeed} ${bleLidarConnection.currentDirection},${degToRad(point.angle)},${point.distance} }")

                            if(risk>0.6f){
                                chosenColor=red
                            }else if(risk>0.4f){
                                chosenColor=orange
                            }else if(risk>0.2f){
                                chosenColor=yellow
                            }else{
                                chosenColor=green
                            }


                            if(point.angle>180) {//TODO ???
                                canvas.drawCircle(coords.first, coords.second, 4f, chosenColor)
                            }else{
                                canvas.drawCircle(coords.first, coords.second, 4f, chosenColor)
                            }
                        }
                    }

                    //point "forward" test
                    val coordsStart=polarToCanvas(0,0,canvas.width,canvas.height)
                    val coordsFinish=polarToCanvas(10000,0,canvas.width,canvas.height)

                    canvas.drawLine(coordsStart.first,coordsStart.second,coordsFinish.first,coordsFinish.second, red)

                }else{
                    //if there is no lidar data draw 4 circles as an example
                    canvas.drawCircle(100f,100f,5f, green)
                    canvas.drawCircle(400f,100f,5f, green)
                    canvas.drawCircle(100f,400f,5f, green)
                    canvas.drawCircle(400f,400f,5f, green)
                }

                //release the canvas
                radarView.holder.unlockCanvasAndPost(canvas)
                isDrawing=false
            }
        }

    }

    private fun sendBLECommand(str:String=""){
        if(str!=""){
            bleLidarConnection.writeMessageToLidarCharacteristic(str)
        }else{
            val command=commandBox.text.toString()//TODO indagare bug spannable
            if(command != ""){
                bleLidarConnection.writeMessageToLidarCharacteristic(command)
            }
            commandBox.setText("")
        }
    }

    private fun sendWSCommand(str:String=""){
        if(str!=""){
            webSocketConnection.sendMessage(str)
        }else{
            val command=commandBox.text.toString()//TODO indagare bug spannable
            if(command != ""){
                webSocketConnection.sendMessage(command)
            }
            commandBox.setText("")
        }
    }

    private fun min(a:Int,b:Int): Int {
        return if(a>b) b else a
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if(espConnectionType=="BLE"){
            bleLidarConnection=object : BLELidarConnection(bluetoothManager,this){
                override fun onConnectionStatusChange(status:Int){
                    //TODO gestire tutte le risposte dalla classe
                    Log.d("BLELidarConnection","Status: $status")
                    when(status){
                        LIDAR_CHARACTERISTIC_PARSED->{
                            //startRadarLoop()

                            drawLidarData(bleLidarConnection.returnLidarPointList(),bleLidarConnection.currentSpeed,bleLidarConnection.currentDirection)
                            runOnUiThread {
                                speedView.text=bleLidarConnection.currentSpeed.toString()
                                directionView.text=bleLidarConnection.currentDirection.toString()
                                headerTextView.text=bleLidarConnection.lastHeader
                            }

                        }

                        LIDAR_CHARACTERISTIC_DRAWABLE->{
                            drawLidarData(bleLidarConnection.returnLidarPointList(),bleLidarConnection.currentSpeed,bleLidarConnection.currentDirection)
                        }

                        CONNECTION_COMPLETE->{
                            connectionComplete=true
                            runOnUiThread {
                                scanButton.text="Connected"
                            }
                        }
                        else->{
                            null
                        }
                    }
                }
            }
        }else if(espConnectionType=="WEBSOCKET"){
            webSocketConnection = object : WebSocketConnection(ESP_ADDRESS, ESP_PORT){
                //i pacchetti in formato stringa non dovrebbero essere processati
                override fun processMessage(message:String){
                    //runOnUiThread {
                        //headerTextView.text=message

                        //parseLidarData(message.toByteArray())
                        if( message!="connection accepted"){

                           //TODO
                            Log.d("ASD","ASD $message")


                            //parseFixedLidarData(message.toByteArray())
                            //Log.d("ASD","ASD $currentTime")

                            //drawLidarData(lidarPointArray.toList(), currentSpeed, currentDirection)
                        }

                        //messaggio di sincronizzazione orologio
                        if("TIME_SYNCHRO1" in message){
                            timeSyncronization.continueSynchronization(parseTimeSynchronizationMessage(message))
                            runOnUiThread {
                                timeView.text=timeSyncronization.ts.toString()
                            }
                            //computeSynchronizeTime(message)
                        }
                   // }
                }


                //i pacchetti dati del lidar saranno sempre in formato bytestring
                override fun processMessage(message: ByteString){
                    val hex=message.hex()
                    val packetParsed=parseFixedLidarData(hex)
                    /*if(packetParsed!=null){
                        val alarm=computeAlarmLevel(packetParsed)
                    }*/

                    if(packetParsed==null){
                        return
                    }

                    lastTimeStamp++


                    runOnUiThread {
                        headerTextView.text="H;$currentTime,\$angle,$currentDirection,$currentSpeed,$currentBrakeStatus \n Alarm level: ${computeAlarmLevel(packetParsed!!)} \n"
                        speedView.text="Speed: $currentSpeed km/h"
                        directionView.text="Direction $currentDirection"
                        drawLidarData(lidarPointArray.toList(), currentSpeed, currentDirection)
                    }


                }

                override fun onSocketError(ex: Throwable) {
                    Log.d("ASD","ASD Error occurred : ${ex.message} - ${ex.stackTrace} - ${ex.cause}")
                }
            }
        }

        if(ESP_DEFAULT_CONNECTION_TYPE=="WEBSOCKET"){
            runOnUiThread {
                connectionTypeSwitch.isChecked = true
            }
        }

        connectionTypeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                espConnectionType="WEBSOCKET"
            } else {
                espConnectionType="BLE"
            }
        }
        joystickSwitch.visibility=View.GONE
        joystickContainer.visibility=View.GONE
        radarView.visibility=View.VISIBLE
        joystickSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                joystickContainer.visibility=View.VISIBLE
                radarView.visibility=View.GONE
            } else {
                joystickContainer.visibility=View.GONE
                radarView.visibility=View.VISIBLE
            }
        }


        //setup listener on the scan button
        scanButton.setOnClickListener {
            if(!connectionComplete){
                startConnection()
            }else{
                showCounters()
                showString()
            }

            //dopo che la connessione è stata aperta nascondo il pulsante e lo switch per cambiare tipo connessione
            scanButton.visibility= View.GONE
            connectionTypeSwitch.visibility=View.GONE

            //poi rendo visibile il controllo per mostrare il joystick se la modalità è websocket
            if(espConnectionType=="WEBSOCKET"){
                joystickSwitch.visibility=View.VISIBLE
            }

        }


        commandButton.setOnClickListener {
            if(espConnectionType=="BLE" && connectionComplete){
                sendBLECommand()
            }else if(espConnectionType=="WEBSOCKET"){
                sendWSCommand()
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio ${commandBox.text}")
            }
        }
        brakeOnButton.setOnClickListener {
            if(espConnectionType=="BLE" && connectionComplete){
                sendBLECommand("BRAKE_ON")
            }else if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("BRAKE_ON")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio BRAKE_ON")
            }
        }

        brakeOffButton.setOnClickListener {
            if(espConnectionType=="BLE" && connectionComplete){
                sendBLECommand("BRAKE_OFF")
            }else if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("BRAKE_OFF")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio BRAKE_OFF")
            }
        }

        //joystick listeners

        leftButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("WS_JOY_LEFT")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio WS_JOY_LEFT")
            }
        }

        upButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("WS_JOY_UP")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio WS_JOY_UP")
            }
        }

        rightButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("WS_JOY_RIGHT")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio WS_JOY_RIGHT")
            }
        }

        downButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("WS_JOY_DOWN")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio WS_JOY_DOWN")
            }
        }

        centerButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("WS_JOY_CENTER")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio WS_JOY_CENTER")
            }
        }


        //after create completes or when the app is reopened we check if bluetooth is enabled and prompt to enable it
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }

        if (!isLocationPermissionGranted) {
            requestLocationPermission()
        }

        //getPacketFrequency()

    }


    //funzione per salvare nei log il numero di pacchetti websocket ricevuti ogni secondo
    fun getPacketFrequency(){
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if(lastTimeStamp!=0L){
                    Log.d("ASD","ASD frequency: ${lastTimeStamp}")
                    lastTimeStamp=0L
                }

                getPacketFrequency()
            },
            1000
        )
    }

    override fun onResume(){
        super.onResume()

        //after create completes or when the app is reopened we check if bluetooth is enabled and prompt to enable it
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }


    override fun onDestroy() {

        //dato che sono in chiusura app non importa se cerco di chiudere una connessione inesistente
        //TODO bloccare connessione ble?
        webSocketConnection.stopSocket()

        super.onDestroy()
    }

    fun computeAlarmLevel(lidarPacket:WebSocketConnection.PacketParsed):String{
        val shift=180
        val targetDistance=lidarPacket.speed.pow(2)/200f+0.5f+0.3f
        var pointsInArea=0
        var dangerousPointsInArea=0
        for(i in -18..18){
            if(lidarPacket.lidarPoints[i+shift].distance/1000f<targetDistance){
                pointsInArea++
                dangerousPointsInArea++
            }else{
                pointsInArea++
            }
        }

        return if(dangerousPointsInArea/pointsInArea>0.3f){
            "DANGER"
        }else{
            "CLEAR"
        }
    }

    private fun startRadarLoop(){
        if(!isRadarDrawing){
            isRadarDrawing=true
            while(true){//TODO enable manual stopping
                drawLidarData(null,0f,0)
            }
        }
    }


    //if bluetooth is not enabled prompt on the screen to enable it
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    //if user did not enable the bluetooth in the prompt show it again
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }


    private fun startConnection(){//TODO check isscanning and give feedback

        if(espConnectionType=="BLE"){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLocationPermissionGranted && bluetoothAdapter.isEnabled) {
                bleLidarConnection.startBleScan()
            }else {
                Log.e("StartConnectionError","Location permission not granted or bluetooth off")
            }
        }else if(espConnectionType=="WEBSOCKET"){
            runBlocking {
                launch{
                    withContext(Dispatchers.IO) {
                        webSocketConnection.subscribeToSocketEvents()
                        connectionComplete=true

                        Handler(Looper.getMainLooper()).postDelayed({
                            timeSyncronization.startSynchronization()
                        }, 100)
                    }
                }
            }
        }



    }
    
    private fun showCounters(){
        Log.d("counters","Characteristic changed/read/parsed: ${bleLidarConnection.numChanges} / ${bleLidarConnection.numRead} / ${bleLidarConnection.numParsed}")
    }

    //show hex of received packets
    private fun showString(){
        var i=0
        bleLidarConnection.saveString.forEach{
            Log.d("showString","Pacchetto ${i++}: $it")
        }
        //reset saved packets
        bleLidarConnection.saveString = mutableListOf<String>()
    }

    //to scan for devices the fine location permission is required, prompt the user to give location permission
    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Location permission required")
                .setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices.")
                .setPositiveButton(android.R.string.ok){ _: DialogInterface, _: Int ->
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .show()
        }
    }

    //if user did not give location permission in the prompt show it again else start the scan
    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    //TODO start scan
                }
            }
        }
    }

    //request a specified permission
    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

}

//class that contains a single lidar data point
data class LidarPoint(val angle: Int, val distance: Float, val intensity: Int)