package com.example.wifibridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.wifi.WifiManager
import android.os.*
import android.text.format.Formatter
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wifibridge.common.RosConnectionManager
import com.example.wifibridge.RiskAssessment.Companion.computeLidarRiskProbability
import com.example.wifibridge.RiskAssessment.Companion.format
import com.stealthcopter.networktools.ARPInfo
import com.stealthcopter.networktools.Ping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ByteString
import wifibridge.R
import kotlin.collections.*
import kotlin.math.*
import com.stealthcopter.networktools.SubnetDevices
import com.stealthcopter.networktools.SubnetDevices.OnSubnetDeviceFound
import com.stealthcopter.networktools.ping.PingNative.ping
import com.stealthcopter.networktools.subnet.Device
import kotlinx.android.synthetic.main.activity_main.*


//useful android constants
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

//ESP32 connection constants
private const val ESP_DEFAULT_CONNECTION_TYPE="WEBSOCKET"
//private const val ESP_ADDRESS="192.168.99.57"
private const val ESP_PORT="1337" //1337

//su prototipo AIT diverse funzionalità sono disattivate
private const val IS_AIT=true

//TODO per ora non è ancora usato
private const val  WIFI_SSD = "LIDAR_WIFI"
private const val  WIFI_PWD = "123456789"
/*
#define WIFI_SSID_DEFAULT "ESP32-Access-Point"
#define WIFI_PWD_DEFAULT  "123456789"
*/
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
    /*private val headerTextView: TextView by lazy{
        findViewById(R.id.headerTextView)
    }*/
    private val commandButton: Button by lazy{
        findViewById(R.id.commandButton)
    }
    private val commandBox: EditText by lazy{
        findViewById(R.id.commandBox)
    }

    private val brakeOnButton: Button by lazy{
        findViewById(R.id.brakeOnButton)
    }
    private val steeringTestButton: Button by lazy{
        findViewById(R.id.steeringTestButton)
    }
    private val circleTestButton: Button by lazy{
        findViewById(R.id.circleTestButton)
    }

    private val squareTestButton: Button by lazy{
        findViewById(R.id.squareTestButton)
    }

    private val joystickContainer:TableLayout by lazy{
        findViewById(R.id.joystickContainer)
    }

    // Looper handler thread for compute operations.
    private var backgroundComputeThread: HandlerThread?=null

    // Looper handler for compute operations.
    private var backgroundComputeHandler: Handler?=null


    //joystick controls

    /*private val leftButton: Button by lazy{
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
    }*/

    private val buttonGear0: Button by lazy{
        findViewById(R.id.buttonGear0)
    }

    private val buttonGear1: Button by lazy{
        findViewById(R.id.buttonGear1)
    }

    private val buttonGear2: Button by lazy{
        findViewById(R.id.buttonGear2)
    }

    private val buttonGear3: Button by lazy{
        findViewById(R.id.buttonGear3)
    }

    private val buttonGear4: Button by lazy{
        findViewById(R.id.buttonGear4)
    }

    private val buttonSteeringN60:Button by lazy{
        findViewById(R.id.buttonSteeringN60)
    }
    private val buttonSteeringN20:Button by lazy{
        findViewById(R.id.buttonSteeringN20)
    }
    private val buttonSteering0:Button by lazy{
        findViewById(R.id.buttonSteering0)
    }
    private val buttonSteeringP20:Button by lazy{
        findViewById(R.id.buttonSteeringP20)
    }
    private val buttonSteeringP60:Button by lazy{
        findViewById(R.id.buttonSteeringP60)
    }

    private val twistStatusView:TextView by lazy{
        findViewById(R.id.twistConnectionStatus)
    }

    private val rosStatusView:TextView by lazy{
        findViewById(R.id.rosConnectionStatus)
    }

    private val waitDiscoveryView:TextView by lazy{
        findViewById(R.id.waitDiscoveryView)
    }

    private val angleSwitch:Switch by lazy{
        findViewById(R.id.angleSwitch)
    }

    private lateinit var wifiIP:String

    var lastTimeStamp=0L


    private var isRadarDrawing=false


    var rosDeviceIp=""
    var sterzoDeviceIp=""

    val riskSystem=RiskAssessment(40)
    var currentLidarProbability=-1f

    var lastRosGearSet=0
    var lastRosTimestamp=0L
    var lastReceivedSterzo=0f
    var isControlledByAngle=false


    var isWebSocketConnected=false
    //lateinit var hotspotReservation:WifiManager.LocalOnlyHotspotReservation

    //RosBridge manager helper class
    private val rosManager: RosConnectionManager = object: RosConnectionManager(){
        override fun processControlMessage(linearVelocity: Float, angularVelocity: Float,period:Float) {

            lastRosTimestamp=System.currentTimeMillis()

            val vel=if(linearVelocity>=0f){linearVelocity}else{0f}
            val targetVel=vel*3.6f
            val targetAng=Math.toDegrees(angularVelocity.toDouble()).toFloat()
           // var targetAng=min(Math.toDegrees(angularVelocity.toDouble()).toFloat(),60f)
           // targetAng=max(targetAng,-60f)
         //   val targetGear=gearFromSpeed(targetVel)

            webSocketConnection.sendMessage("CONTROL;$targetVel;$targetAng;")
            publishOnCommandFeedbackPhoneTopic("CONTROL;$targetVel;$targetAng;",System.currentTimeMillis()*1000000L)
            Log.d("ASD","ASD sent command: CONTROL;$targetVel;$targetAng;")


            /*if(lastRosGearSet!=targetGear){
                webSocketConnection.sendMessage("SET_GEAR;$targetGear;")
                publishOnCommandFeedbackPhoneTopic("SET_GEAR;$targetGear;",System.currentTimeMillis()*1000000L)
                Log.d("ASD","ASD sent command: SET_GEAR;$targetGear;")
                lastRosGearSet=targetGear
            }

            /*if(targetAng>0.5f && !isControlledByAngle){
                webSocketConnection.sendMessage("STERZO;${(targetAng*0.1f) + lastReceivedSterzo};")
                publishOnCommandFeedbackPhoneTopic("STERZO;${(targetAng*0.1f) + lastReceivedSterzo};",System.currentTimeMillis()*1000000L)
                Log.d("ASD","ASD sent command: STERZO;${(targetAng*0.1f) + lastReceivedSterzo};")
            }*/

            if(isControlledByAngle){
                webSocketConnection.sendMessage("STERZO;${Math.toDegrees(angularVelocity.toDouble()).toFloat()};")
                publishOnCommandFeedbackPhoneTopic("STERZO;${Math.toDegrees(angularVelocity.toDouble()).toFloat()};",System.currentTimeMillis()*1000000L)
                Log.d("ASD","ASD sent command: STERZO;${Math.toDegrees(angularVelocity.toDouble()).toFloat()};")
            }*/

            //webSocketConnection.sendMessage("TWIST;${vel.format(4,true)};${angularVelocity.format(4,true)};")
            //publishOnCommandFeedbackPhoneTopic("TWIST;${linearVelocity.format(4,true)};${angularVelocity.format(4,true)};",System.currentTimeMillis()*1000000L)
            Log.d("RosCommandReceived","Ros Twist Command Received: linearVel=$linearVelocity - angularVel=$angularVelocity")
        }
    }

    //check if we have a certain permission
    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }


    private fun gearFromSpeed(speed:Float):Int {
        // find closest gear
        val gearSpeeds =  intArrayOf( 0, 2, 3, 4, 6 )
        var actualSpeed=-1

        if (speed > gearSpeeds[4]){
            return 4
        }

        if (speed < gearSpeeds[0]){
            return 0
        }

        var minDiff = -1f
        var closestGear = 0

        for (i in gearSpeeds.indices) {
            val tempDif = abs(speed - gearSpeeds[i]);

            if (tempDif < minDiff || minDiff == -1f) {
                minDiff = tempDif
                closestGear = i
            }
        }
        return closestGear
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

    fun startRiskAssessmentLoop(){
        Handler(Looper.getMainLooper()).postDelayed(//TODO rallenta esecuzione? Investigare problemi con memoria o troppe esecuzioni
            {
                riskSystem.step(-1f,currentLidarProbability)

                currentLidarProbability=-1f

                val brake=if(riskSystem.shouldBrake()){
                    //vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    "BRAKE"
                }else{
                    "SAFE"
                }

                /*runOnUiThread {
                    headerTextView.text=brake+"\n"+riskSystem.probabilitiesToString()
                }*/

                startRiskAssessmentLoop()
            },
            riskSystem.timePeriod.toLong()
        )
    }

    private fun startBackgroundComputeThread(){
        backgroundComputeThread = HandlerThread("sharedCameraBackground")
        backgroundComputeThread?.start()
        backgroundComputeHandler = Handler(backgroundComputeThread!!.looper)
    }

    private fun stopBackgroundComputeThread() {
        backgroundComputeThread?.quitSafely()
        try {
            backgroundComputeThread!!.join()
            backgroundComputeThread = null
            backgroundComputeHandler = null
        } catch (e: InterruptedException) {
            Log.e("Backgroundthreaderror", "Interrupted while trying to join background handler thread", e)
        }
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

    //inizio loop heartbeat, mando un messaggio e controllo il messaggio proveniente da controller ogni delay millisecondi
    fun startHeartbeatLoop(delay:Long){
        heartbeatLoop(delay)
        Log.d("HBStartNotification","Heartbeat control loop started")
    }

    //inizio loop heartbeat, mando un numero di messaggi e controllo il messaggio proveniente da controller un numero di volte al secondo pari a rate
    fun startHeartbeatLoop(rate:Int){
        val delay=((1f/rate.toFloat())*1000f).toLong()//delay espresso in ms
        Log.d("HBStartNotification","Heartbeat control loop started")
        heartbeatLoop(delay)
    }

    fun heartbeatLoop(delay:Long){
        backgroundComputeHandler?.postDelayed({
            if(isWebSocketConnected){
                val currentTime=System.currentTimeMillis()
                val tolerance=10L//tolleranza ritardo pacchetto heartbeat in ms

                //se l'ultimo heartbeat che ho ricevuto è troppo vecchio allora c'è un problema lato esp
                if((currentTime-webSocketConnection.lastHeartbeatReceived)>5*(delay+tolerance)/* && webSocketConnection.isHeartbeatActive*/){
                    //Log.d("ASD","ASD ${currentTime-webSocketConnection.lastHeartbeatReceived}")

                    webSocketConnection.isHeartbeatActive=false
                    //TODO Log.e("HeartBeatFromWebsocketError","Did not receive heartbeat from websocket in the last $delay milliseconds")
                    //TODO gestire disconnessione in modo corretto
                    runOnUiThread{
                        twistStatusView.text="Twist Disconnected"
                        twistStatusView.setBackgroundColor(Color.RED)
                    }
                }else{
                    webSocketConnection.isHeartbeatActive=true//TODO probabilmente se l'heartbeat si è interrotto bisogna bloccare esecuzione
                    runOnUiThread{
                        twistStatusView.text="Twist Connected"
                        twistStatusView.setBackgroundColor(Color.GREEN)
                    }

                }
            }else{
                Log.d("HeartBeatFromWSError","Cannot receive heartbeat message from websocket, websocket is disconnected")
                runOnUiThread{
                    twistStatusView.text="Twist Disconnected"
                    twistStatusView.setBackgroundColor(Color.RED)
                }
            }
            runBlocking {
                launch{
                    withContext(Dispatchers.IO) {
                        if(isWebSocketConnected){
                            webSocketConnection.sendMessage("HEARTBEAT_PHONE")
                        }else{
                            Log.d("HeartbeatToWSError","Cannot send heartbeat message to websocket, websocket is disconnected")
                        }
                        val timestamp= ((System.currentTimeMillis()))*1000000L
                        rosManager.publishOnHeartbeatTopic(timestamp)
                    }
                }
            }

            if(rosManager.isConnected()){
                runOnUiThread{
                    rosStatusView.text="Ros Connected"
                    rosStatusView.setBackgroundColor(Color.GREEN)
                }
            }else{
                runOnUiThread{
                    rosStatusView.text="Ros Disconnected"
                    rosStatusView.setBackgroundColor(Color.RED)
                }
            }


            if(lastRosTimestamp!=0L && System.currentTimeMillis()-lastRosTimestamp>1000){
                webSocketConnection.sendMessage("SET_GEAR;0;")
                rosManager.publishOnCommandFeedbackPhoneTopic("SET_GEAR;0;",System.currentTimeMillis()*1000000L)
                lastRosGearSet=0
            }

            /*runBlocking { //TODO implementare thread correttamente
                (1..4).forEach {
                    launch {
                        println("$it, delaying in ${Thread.currentThread().name}")
                        delay(1000)
                        println("$it, done in ${Thread.currentThread().name}")
                    }
                }
            }*/

            heartbeatLoop(delay)
        }, delay)
    }

    fun buildWebsocketToSterzo(){
        webSocketConnection = object : WebSocketConnection(sterzoDeviceIp, ESP_PORT){
            //i pacchetti in formato stringa non dovrebbero essere1 processati
            override fun processMessage(message:String){

                Log.d("WSMessageReceived","WS Message received: $message")
                isWebSocketConnected=true
                if(message=="connection accepted" || message=="CONNECTION ACCEPTED" || message=="CONNECTION_ACCEPTED"){
                    Log.d("WSEstablished","Connection to master controller established")
                    startHeartbeatLoop(500L)
                }else if("TIME_SYNCHRO1" in message){
                    timeSyncronization.continueSynchronization(parseTimeSynchronizationMessage(message))
                }else if("HEARTBEAT_CONTROLLER" in message){
                    webSocketConnection.lastHeartbeatReceived=System.currentTimeMillis()
                }else if("BRAKE_ON received" in message){
                    //conferma BRAKE_ON
                }else if("COMMAND_RECEIVED" in message){
                    val parsedMessage=message.split(";")
                    val command=parsedMessage[1]
                    val millis=parsedMessage[2].toInt()

                    rosManager.publishOnCommandFeedbackControllerTopic(command,((millis).toLong()+timeSyncronization.ts)*1000000L)
                }else if("DATA" in message){//TODO direction è inserito direttamente all'interno della velocità al momento, eliminare direzione separata
                    val parsedMessage=message.split(";")
                    var msgSpeed=parsedMessage[1].toInt()
                    val msgSpeedTimestamp=parsedMessage[2].toInt()
                    val msgAngle=parsedMessage[3].toFloat()
                    val msgAngleTimestamp=parsedMessage[4].toInt()

                    if(parsedMessage[5].toInt()==0){
                        msgSpeed*=-1
                    }

                    lastReceivedSterzo=msgAngle

                    runOnUiThread {
                        speedView.text="Speed: ${msgSpeed/10f} km/h"
                        timeView.text= (msgAngle).toString()

                    }

                    if(timeSyncronization.ts!=-1L && msgSpeedTimestamp!=0 && msgAngleTimestamp!=0) {
                        rosManager.publishOnWheelchairTopic(
                            msgSpeed / 10f,
                            msgSpeedTimestamp,
                            msgAngle,
                            msgAngleTimestamp,
                            timeSyncronization.ts,
                            1
                        )
                    }
                    Log.d("ASD","ASD ricevuto DATA: $msgSpeed, $msgSpeedTimestamp, $msgAngle, $msgAngleTimestamp")
                }else{
                    //gestire messaggi diversi in base a necessità
                    Log.d("UnsupportedMsgReceived","Unsupported message received:$message")
                }
            }


            //i pacchetti dati del lidar saranno sempre in formato bytestring
            override fun processMessage(message: ByteString){
                val hex=message.hex()
                val packetParsed=parseFixedLidarData(hex)
                /*if(packetParsed!=null){
                    val alarm=computeAlarmLevel(packetParsed)
                }*/

                if(packetParsed==null){
                    //se viene ritornato un pacchetto vuoto c'è stato un errore di parsing, loggato nella funzione di parsing
                    return
                }


                if(!IS_AIT){
                    currentLidarProbability=computeLidarRiskProbability(packetParsed)
                }

                lastTimeStamp++

                if(timeSyncronization.ts!=-1L){
                    //rosManager.publishOnWheelchairTopic(packetParsed, timeSyncronization.ts, 1)

                    if(!packetParsed.isLidarDataDuplicated){
                        rosManager.publishOnLidarTopic(packetParsed, timeSyncronization.ts)
                    }
                }

                runOnUiThread {
                    //headerTextView.text="H;$currentTime,\$angle,$currentDirection,$currentSpeed,$currentBrakeStatus \n Alarm level: ${computeAlarmLevel(packetParsed!!)} \n"
                    speedView.text="Speed: $currentSpeed km/h"
                    timeView.text=packetParsed.time.toString()
                    //directionView.text="Direction $currentDirection"
                    drawLidarData(lidarPointArray.toList(), currentSpeed, currentDirection)
                }


            }

            override fun onSocketError(ex: Throwable) {
                Log.d("ASD","ASD Error occurred : ${ex.message} - ${ex.stackTrace} - ${ex.cause}")
            }
        }
    }

    /*fun buildWebsocketToRos(){

    }*/

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val context: Context = this
        val wm = context.getSystemService(WIFI_SERVICE) as WifiManager
        wifiIP = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)


        val wInfo = wm.connectionInfo


        val ips=arrayOf("","")
        val pings=arrayOf(0f,0f)
        var index=0

        SubnetDevices.fromLocalAddress().findDevices(object : OnSubnetDeviceFound {
            override fun onDeviceFound(device: Device?) {
                // Stub: Found subnet device
                Log.d("ASD","Device found: ${device.toString()}")

                /*var avgPing=0f

                for(i in 0..100){
                    val time=Ping.onAddress(device!!.ip).setTimeOutMillis(1000).doPing().timeTaken
                    avgPing+=time
                }
                Log.d("ASD","Ping: ${avgPing/100f}")*/
            }



            override fun onFinished(devicesFound: ArrayList<Device?>?) {
                //mi aspetto di trovare 3 dispositivi: s20,sterzo,portatile
                //s20-> ping<1ms
                //sterzo,portatile -> ping variabile, ping sterzo>ping portatile
                devicesFound!!.forEach { device->
                    var avgPing=0f

                    for(i in 0..100){
                        val time=Ping.onAddress(device!!.ip).setTimeOutMillis(1000).doPing().timeTaken
                        avgPing+=time
                    }
                    avgPing/=100f

                    Log.d("ASD","Ping: ${avgPing} i:$index")
                    if(avgPing>1f){
                        ips[index]=device!!.ip
                        pings[index]=avgPing
                        index++
                    }
                }

                if(index!=2){
                    Log.d("WrongDeviceNumbersError","There need to be only 2 devices connected on hotspot network")
                    runOnUiThread {
                        waitDiscoveryView.text="Error, there need to be 2 devices on the hotspot network. Please restart"
                    }
                }else{
                    if(pings[0]>pings[1]){
                        sterzoDeviceIp=ips[0]
                        rosDeviceIp=ips[1]
                    }else{
                        sterzoDeviceIp=ips[1]
                        rosDeviceIp=ips[0]
                    }

                    Log.d("DeviceIPsFoundCorrectly","IPs of connected devices found: sterzo: $sterzoDeviceIp, ros:$rosDeviceIp")
                    buildWebsocketToSterzo()
                    //startWebsocketToRos()

                    runOnUiThread{
                        waitDiscoveryView.visibility=View.GONE
                        scanButton.visibility=View.VISIBLE
                    }
                }
            }
        })

        /*val wifiCallback= object:WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation:WifiManager.LocalOnlyHotspotReservation) {
                super.onStarted(reservation);
                Log.d("WifiHotspot", "Wifi Hotspot is on now");
                hotspotReservation = reservation
                val currentConfig = hotspotReservation.wifiConfiguration;

                Log.v("DANG", "THE PASSWORD IS: ${currentConfig!!.preSharedKey} \n SSID is : ${currentConfig.SSID}")

            }

            override fun onStopped() {
                super.onStopped();
                Log.d("WifiHotspot", "onStopped: ");
                //mReservation = null;
            }

            override fun onFailed(reason:Int) {
                super.onFailed(reason);
                Log.d("WifiHotspot", "onFailed: ");
                //mReservation = null;
            }        }

        wm.startLocalOnlyHotspot(wifiCallback,Handler())*/

       /*

        wm.startLocalOnlyHotspot(object:WifiManager.LocalOnlyHotspotCallback() {

            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                super.onStarted(reservation);
                Log.d(TAG, "Wifi Hotspot is on now");
                mReservation = reservation;
            }

            @Override
            public void onStopped() {
                super.onStopped();
                Log.d(TAG, "onStopped: ");
                mReservation = null;
            }

            @Override
            public void onFailed(int reason) {
                super.onFailed(reason);
                Log.d(TAG, "onFailed: ");
                mReservation = null;
            }
        }, new Handler());
    }*/

       /*if(espConnectionType=="BLE"){
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
                                //directionView.text=bleLidarConnection.currentDirection.toString()
                                //headerTextView.text=bleLidarConnection.lastHeader
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

        }*/

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


        angleSwitch.setOnCheckedChangeListener { _, isChecked ->
            isControlledByAngle = isChecked
        }
        joystickSwitch.visibility=View.GONE
        //joystickContainer.visibility=View.GONE
        //radarView.visibility=View.VISIBLE

        joystickContainer.visibility=View.VISIBLE
        radarView.visibility=View.GONE

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
                //joystickSwitch.visibility=View.VISIBLE
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

        steeringTestButton.setOnClickListener {
            if(espConnectionType=="BLE" && connectionComplete){
                //TODO sendBLECommand("BRAKE_OFF")
            }else if(espConnectionType=="WEBSOCKET"){
                //sendWSCommand("BRAKE_OFF")

                startSteeringTestLoop()

            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile iniziare steering test")
            }
        }

        circleTestButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                //sendWSCommand("BRAKE_OFF")

                startCircleTestLoop()

            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile iniziare circle test")
            }
        }

        squareTestButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                //sendWSCommand("BRAKE_OFF")

                startSquareTest()

            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile iniziare square test")
            }
        }

        //joystick listeners

        buttonGear0.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("SET_GEAR;0;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio SET_GEAR")
            }
        }

        buttonGear1.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("SET_GEAR;1;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio SET_GEAR")
            }
        }

        buttonGear2.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("SET_GEAR;2;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio SET_GEAR")
            }
        }

        buttonGear3.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("SET_GEAR;3;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio SET_GEAR")
            }
        }

        buttonGear4.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("SET_GEAR;4;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio SET_GEAR")
            }
        }

        buttonSteeringN60.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("STERZO;-76;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio STERZO")
            }
        }

        buttonSteeringN20.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("STERZO;-35;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio STERZO")
            }
        }

        buttonSteering0.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("STERZO;0;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio STERZO")
            }
        }

        buttonSteeringP20.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("STERZO;35;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio STERZO")
            }
        }

        buttonSteeringP60.setOnClickListener{
            if(espConnectionType=="WEBSOCKET"){
                sendWSCommand("STERZO;76;")
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio STERZO")
            }
        }

        /*leftButton.setOnClickListener {
            if(espConnectionType=="WEBSOCKET"){
                webSocketConnection.changeSteeringAngle(-3)
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
                webSocketConnection.changeSteeringAngle(3)
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
                webSocketConnection.resetSteeringAngle()
            }else{
                Log.d("Errore","Dispositivo non connesso, impossibile mandare il messaggio WS_JOY_CENTER")
            }
        }*/

        if(!IS_AIT){
            startRiskAssessmentLoop()
        }
        if(IS_AIT){
            circleTestButton.visibility=View.GONE
            squareTestButton.visibility=View.GONE
            steeringTestButton.visibility=View.GONE
            connectionTypeSwitch.visibility=View.GONE
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

        startBackgroundComputeThread()

        //after create completes or when the app is reopened we check if bluetooth is enabled and prompt to enable it
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }


    override fun onDestroy() {

        //dato che sono in chiusura app non importa se cerco di chiudere una connessione inesistente
        //TODO bloccare connessione ble?
        webSocketConnection.stopSocket()

        rosManager.disconnect()

        stopBackgroundComputeThread()

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

    fun startSteeringTestLoop(delay:Int=50,step:Int=10){
        webSocketConnection.sendMessage("STERZO;90;")
        Log.d("ASD","ASD RESET angolo: 90, delay: $delay, ESP: ${webSocketConnection.currentTime}, ANDROID ${System.currentTimeMillis()-timeSyncronization.ts}")
        Handler(Looper.getMainLooper()).postDelayed({
            steeringLoopStep(90,delay,step)
        }, 1500)

    }

    fun steeringLoopStep(current:Int,delay:Int,step:Int){
        if(current<=270){
            Handler(Looper.getMainLooper()).postDelayed({
                webSocketConnection.sendMessage("STERZO;$current;")
                Log.d("ASD","ASD angolo: $current, delay: $delay, millis: ${webSocketConnection.currentTime}, ANDROID ${System.currentTimeMillis()-timeSyncronization.ts}")
                steeringLoopStep(current+step,delay,step)
            }, delay.toLong())
        }else{
            if(delay>0){
                Handler(Looper.getMainLooper()).postDelayed({
                    startSteeringTestLoop(delay)
                }, 1500)

            }
        }
    }

    fun startCircleTestLoop(){
        val linearSpeed = 1f/3.6f //velocità lineare 1km/h
        val angularSpeed = 17f*0.0174533f //velocità angolare 17gradi/s
        circleLoopStep(linearSpeed,angularSpeed)
    }

    /*fun circleLoopStep(linearSpeed:Float,angularSpeed:Float){
        Handler(Looper.getMainLooper()).postDelayed({
            webSocketConnection.sendMessage("TWIST;${linearSpeed.format(4)};${angularSpeed.format(4)};")
            Log.d("ASD","ASD TWIST;${linearSpeed.format(4)};${angularSpeed.format(4)};")
            circleLoopStep(linearSpeed,angularSpeed)
        },200)
    }*/

    fun circleLoopStep(linearVelocity: Float,angularVelocity: Float){
        Handler(Looper.getMainLooper()).postDelayed({
            val angle=Math.toDegrees(twistToControl(linearVelocity,angularVelocity).toDouble()).toInt()
            val vel=linearVelocity*3.6f
            val period=0.2f//periodo ogni quanto arrivano nuovi pacchetti di controllo in teoria TODO mettere valore corretto
            val desiredSpeed=((angle-webSocketConnection.lastAngle)/period)

            val maximumSpeed=10f//in gradi secondo
            val actualSpeed= min(maximumSpeed,desiredSpeed)
            if(maximumSpeed>desiredSpeed){
                Log.d("ControlCommandError","Angular velocity is more than the safe value, cutting to a safe value")
            }

            val newAngle=angle+actualSpeed*period

            webSocketConnection.lastAngle=newAngle

            webSocketConnection.sendMessage("CONTROL;${vel.format(4,true)};${angle};")
            //Log.d("ASD","ASD CONTROL;${vel.format(4,true)};${angle};")
            circleLoopStep(linearVelocity,angularVelocity)
        },200)
    }


    //TODO gestire controllo angular velocity troppo elevato da un'altra parte
    fun twistToControl(linearVelocity:Float,angularVelocity:Float):Float{
        if(linearVelocity==0f){
            return 0f
        }else{
            if(angularVelocity/linearVelocity>1f){
                return (Math.PI/2f).toFloat()
            }

            if(angularVelocity/linearVelocity<-1f){
                return -(Math.PI/2f).toFloat()
            }

            return asin(angularVelocity/linearVelocity)
        }
    }

    fun startSquareTest(){
        var waitTime=1L
        Handler(Looper.getMainLooper()).postDelayed({//avanti lato 1
            webSocketConnection.sendMessage("TWIST;1.0000;0.0000;")
        },waitTime)
        waitTime+=3000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 1
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//ruoto angolo 1
            webSocketConnection.sendMessage("TWIST;0.5000;0.7850;")
        },waitTime)
        waitTime+=2000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 1
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//avanti lato 2
            webSocketConnection.sendMessage("TWIST;1.0000;0.0000;")
        },waitTime)
        waitTime+=3000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 2
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//ruoto angolo 2
            webSocketConnection.sendMessage("TWIST;0.5000;0.7850;")
        },waitTime)
        waitTime+=2000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 1
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//avanti lato 3
            webSocketConnection.sendMessage("TWIST;1.0000;0.0000;")
        },waitTime)
        waitTime+=3000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 3
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//ruoto angolo 3
            webSocketConnection.sendMessage("TWIST;0.5000;0.7850;")
        },waitTime)
        waitTime+=2000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 1
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//avanti lato 4
            webSocketConnection.sendMessage("TWIST;1.0000;0.0000;")
        },waitTime)
        waitTime+=3000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 4
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
        Handler(Looper.getMainLooper()).postDelayed({//ruoto angolo 4
            webSocketConnection.sendMessage("TWIST;0.5000;0.7850;")
        },waitTime)
        waitTime+=2000L
        Handler(Looper.getMainLooper()).postDelayed({//frenata 1
            webSocketConnection.sendMessage("TWIST;0.0000;0.0000;")
        },waitTime)
        waitTime+=1000L
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

            if(sterzoDeviceIp=="" || rosDeviceIp==""){
                Log.d("DeviceIPsNotInitialized","Device IPs have not been initialized yet, cannot start connection")
                return
            }

            runBlocking {
                //websocket
                launch{
                    withContext(Dispatchers.IO) {
                        webSocketConnection.subscribeToSocketEvents()
                        connectionComplete=true

                        Handler(Looper.getMainLooper()).postDelayed({
                            timeSyncronization.startSynchronization()
                        }, 100)
                    }
                }

                //jrosbridge websocket
               launch{
                    withContext(Dispatchers.IO) {
                        if(rosManager.connect(rosDeviceIp)){
                            runOnUiThread{
                                rosStatusView.text="Ros Connected"
                                rosStatusView.setBackgroundColor(Color.GREEN)
                            }
                        }else{
                            runOnUiThread{
                                rosStatusView.text="Ros Disconnected"
                                rosStatusView.setBackgroundColor(Color.RED)
                            }
                        }
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