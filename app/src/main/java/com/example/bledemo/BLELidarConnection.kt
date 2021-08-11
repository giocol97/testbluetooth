package com.example.bledemo

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlin.collections.*
import java.util.*
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

//the Client Characteristic Configuration Descriptor UUID is assigned by the Bluetooth foundation to Google and is the basis for all UUIDs used in Android (https://devzone.nordicsemi.com/f/nordic-q-a/24974/client-characteristic-configuration-descriptor-uuid)
private  const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

//lidar constants
private const val LIDAR_UUID="7da5e347-cbd3-42bb-b813-5a4585d5e44a"//TODO put real one
private const val LIDAR_SERVICE_UUID="7da5e347-cbd3-42bb-b813-5a4585d5e44a"//TODO put real one
private const val LIDAR_CHARACTERISTIC_UUID="eec3f9dd-cf13-4ed7-89d7-49592be711b2"//TODO put real one
private const val GATT_MAX_MTU_SIZE = 517//TODO put real one

//lidar data packets constants
private const val END_LINE=0x0A // '\n'
private const val DATA_SEPARATOR=0x3B //';'

//status change constants
private const val DEVICE_FOUND=0
private const val CONNECTION_START=1
private const val CONNECTION_ERROR=2
private const val CONNECTION_COMPLETE=3
private const val LIDAR_CHARACTERISTIC_CHANGED=4
private const val LIDAR_CHARACTERISTIC_READ=5
private const val LIDAR_CHARACTERISTIC_PARSED=6


open class BLELidarConnection (bluetoothManager: BluetoothManager,context:Context){


    //get bluetooth adapter and scanner from passed manager
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    //build filter to only scan for the lidar device with known UUID
    private val filters=listOf(
        ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(LIDAR_UUID)).build()
    )


    //scan settings, SCAN_MODE_LOW_LATENCY is used since we need to only search once
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    //variable to check if device is scanning, if set also change ui text
    private var isScanning = false

    val lidarPointList=mutableListOf<LidarPoint>()
    var currentDirection=0
    var currentSpeed=0f


    //ScanCallback variable to obtain scan results and start the connection
    private val scanCallback = object : ScanCallback() {

        //using uuid filter only lidar scan results should appear
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (isScanning) {
                stopBleScan()
            }

            onConnectionStatusChange(DEVICE_FOUND)

            Log.w("ScanCallback", "Connecting to ${result.device.address}")
            result.device.connectGatt(context, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }

    //gatt service variable, is initialized on connection complete
    private lateinit var bluetoothGatt: BluetoothGatt


    //BluetoothGattCallback object, it is our main way to interact with the gatt server
    private val gattCallback = object : BluetoothGattCallback() {


        //when connection is established we get the BluetoothGatt object here
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            //connected/disconnected from a device
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    onConnectionStatusChange(CONNECTION_START)

                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")

                    //save the BluetoothGatt object, change MTU size and discover the services offered by the device
                    bluetoothGatt = gatt
                    bluetoothGatt.requestMtu(GATT_MAX_MTU_SIZE)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {//error encountered in connecting

                onConnectionStatusChange(CONNECTION_ERROR)

                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        //after connection is established we discover services to complete the setup
        //we can also set up a notification for the lidar data characteristic here
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here

                //set up notifications for the lidar characteristic so we get a callback every time the data updates
                val lidarServiceUuid = UUID.fromString(LIDAR_SERVICE_UUID)
                val lidarDataCharUuid = UUID.fromString(LIDAR_CHARACTERISTIC_UUID)
                val lidarDataChar = bluetoothGatt.getService(lidarServiceUuid)?.getCharacteristic(lidarDataCharUuid)
                if (lidarDataChar != null) {
                    onConnectionStatusChange(CONNECTION_COMPLETE)
                    enableNotifications(lidarDataChar)
                }else{
                    Log.e("BluetoothGattCallback","Error: Lidar characteristic not found")
                }


            }
        }

        //callback happens after we change the MTU size after we established the connection in onConnectionStateChange
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.w("BluetoothGattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            Handler(Looper.getMainLooper()).post {
                bluetoothGatt.discoverServices()
            }

        }

        //after we make a read operation this callback will be called with the data requested in characteristic.value or an error
        override fun onCharacteristicRead( gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        //Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid")

                        onConnectionStatusChange(LIDAR_CHARACTERISTIC_READ)

                        //we parse the raw data received from the server into a list of LidarPoints
                        parseLidarData(value)

                        if (!lidarPointList.isNullOrEmpty()) {
                            //drawLidarData()//TODO move to main activity
                        }else{
                            Log.e("LidarDataError","Lidar data is null")
                        }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        //this call back tells us the result of a write operation on the server
        override fun onCharacteristicWrite( gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }

        //this callback happens after we set up a notification for a characteristic and the characteristic is updated by the server
        //if the characteristic is the lidar data we can then attempt to read it and we will receive the data on the onCharacteristicRead callback
        override fun onCharacteristicChanged( gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic ) {
            with(characteristic) {
//                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: ${value.toHexString()}")
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed")
                onConnectionStatusChange(LIDAR_CHARACTERISTIC_CHANGED)
                if(uuid.toString()==LIDAR_CHARACTERISTIC_UUID){
                    readRawLidarData()
                }
            }
        }
    }

    //write on a characteristic descriptor on the server, used in setting up notifications
    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt.let { gatt ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    //this function enables notifications for the BluetoothGattCharacteristic passed as input
    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e("ConnectionManager", "${characteristic.uuid} doesn't support notifications/indications")
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (!bluetoothGatt.setCharacteristicNotification(characteristic, true)) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    //this function disables notifications for the BluetoothGattCharacteristic passed as input
    fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e("ConnectionManager", "${characteristic.uuid} doesn't support indications/notifications")
            return
        }

        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (!bluetoothGatt.setCharacteristicNotification(characteristic, false)) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    //transforms a ByteArray object in a string, mostly for logging purposes
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }


    //log discovered services in a device for debug
    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


    //these small functions are used to simplify checking characteristic properties using containsProperty
    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    private fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    private fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    //using the UUIDs provided in the constants read the lidar characteristic
    private fun readRawLidarData(){
        val lidarServiceUuid = UUID.fromString(LIDAR_SERVICE_UUID)
        val lidarDataCharUuid = UUID.fromString(LIDAR_CHARACTERISTIC_UUID)
        val lidarDataChar = bluetoothGatt.getService(lidarServiceUuid)?.getCharacteristic(lidarDataCharUuid)
        if (lidarDataChar?.isReadable() == true) {
            bluetoothGatt.readCharacteristic(lidarDataChar)
        }
    }

    //parse the lidar data in the desired format, returns null if the header is wrong
    private fun parseLidarData(data:ByteArray){

        //check if header char is correct
        if(data[0]!='H'.toByte()){
            Log.e("parseLidarDataError","Header line does not contain control character")
            return
        }

        val temp=mutableListOf<Char>()
        var i=2

        //get starting angle for packet
        do{
            temp.add(data[i].toChar())
            i++
        }while(data[i]!=DATA_SEPARATOR.toByte())

        var angle=temp.joinToString("").toInt()
        temp.clear()
        i++

        //get current direction
        do{
            temp.add(data[i].toChar())
            i++
        }while(data[i]!=DATA_SEPARATOR.toByte())

        currentDirection=temp.joinToString("").toInt()
        temp.clear()
        i++

        //get current speed
        do{
            temp.add(data[i].toChar())
            i++
        }while(data[i]!=END_LINE.toByte())

        currentSpeed=temp.joinToString("").toFloat()
        currentSpeed/=10f
        temp.clear()
        i++

//TODO do in main activity
        /*  speedView.text= "Speed: $currentSpeed"
          directionView.text= "Direction: $currentDirection"
  */
        var tempIntensity=0
        var tempDistance=0

        while(i<data.size){//TODO check arrayoutofbounds
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=DATA_SEPARATOR.toByte())

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

            addToListNoDuplicates(LidarPoint(angle++,tempDistance,tempIntensity))
        }
        onConnectionStatusChange(LIDAR_CHARACTERISTIC_PARSED)
    }

    //add a point to the list checking for duplicate angle TODO make more efficient
    private fun addToListNoDuplicates(point:LidarPoint){
        var found=false
        for(i in lidarPointList.indices){
            if(lidarPointList[i].angle==point.angle){
                found=true
                lidarPointList[i]=point
                break
            }
        }
        if(!found){
            lidarPointList.add(point)
        }
    }

    //function to write data in ByteArray format in a characteristic on the server
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

//TODO do in main activity
    /*
     private fun polarToCanvas(distance:Int,angle:Int,width:Int,height:Int):Pair<Float,Float>{
         //transform polar->cartesian coordinates
         //MAX_DISTANCE defines a saturation point for distance representation
         //ANGLE_OFFSET defines where the angles start from, 0 is x axis
         val angleRads = (angle + ANGLE_OFFSET) * Math.PI / 180;

         var x=(min(distance, MAX_DISTANCE)* cos(angleRads)).toFloat()
         var y=(min(distance,MAX_DISTANCE)* sin(angleRads)).toFloat()

         //fit in the canvas
         x=x*(width/2)/MAX_DISTANCE
         y=y*(height/2)/MAX_DISTANCE

         //center in the middle of the canvas
         x+=(width/2)
         y+=(height/2)

         return Pair(x,y)
     }

  private fun drawLidarData(){
         //code is inside synchronized block to avoid trying to draw on it twice
         synchronized(radarView.holder){
             //get the canvas from the radarView surface
             val canvas: Canvas =radarView.holder.lockCanvas()
             val green= Paint()
             green.color= Color.GREEN
             var coords=Pair(0f,0f)

             canvas.drawColor(Color.BLACK)

             if (!lidarPointList.isNullOrEmpty()) {
                 //draw each point on the canvas
                 lidarPointList.forEach{ point->
                     //don't draw null points
                     if(point.distance!=0 && point.intensity!=0){//TODO controllare perchè alcuni punti rimangono anche se arriva un pacchetto nuovo

                         coords=polarToCanvas(point.distance,point.angle,canvas.width,canvas.height)

                         canvas.drawCircle(coords.first,coords.second,4f, green)
                     }
                 }

                 //point "forward" test
                 val coordsStart=polarToCanvas(0,0,canvas.width,canvas.height)
                 val coordsFinish=polarToCanvas(10000,0,canvas.width,canvas.height)
                 val red= Paint()
                 red.color= Color.RED
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
         }
     }

     //Android API N is needed for min function...
     private fun min(a:Int,b:Int): Int {
         return if(a>b) b else a
     }*/



    //clear the previous scan results and start a new scan
    fun startBleScan() {
        bleScanner.startScan(filters, scanSettings, scanCallback)
        isScanning = true
    }

    //stop an ongoing scan
    fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    public open fun onConnectionStatusChange(status:Int){}



}