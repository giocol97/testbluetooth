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


//useful android constants
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

//radar view constants
private const val MAX_DISTANCE=5000
private const val ANGLE_OFFSET=180

//BLE status change constants
private const val DEVICE_FOUND=0
private const val CONNECTION_START=1
private const val CONNECTION_ERROR=2
private const val CONNECTION_COMPLETE=3
private const val LIDAR_CHARACTERISTIC_CHANGED=4
private const val LIDAR_CHARACTERISTIC_READ=5
private const val LIDAR_CHARACTERISTIC_PARSED=6

class MainActivity : AppCompatActivity() {

    private val bluetoothManager by lazy{
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private lateinit var bleLidarConnection: BLELidarConnection


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



    //check if we have a certain permission
    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }




    private fun polarToCanvas(distance:Int,angle:Int,width:Int,height:Int):Pair<Float,Float>{
        //transform polar->cartesian coordinates
        //MAX_DISTANCE defines a saturation point for distance representation
        //ANGLE_OFFSET defines where the angles start from, 0 is x axis
        val angleRads = (angle + ANGLE_OFFSET) * Math.PI / 180;

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

    private fun drawLidarData(lidarPointList: List<LidarPoint>?){
        //code is inside synchronized block to avoid trying to draw on it twice
        synchronized(radarView.holder){
            //get the canvas from the radarView surface
            val canvas: Canvas =radarView.holder.lockCanvas()
            val green=Paint()
            green.color=Color.GREEN
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
                val red=Paint()
                red.color=Color.RED
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

    private fun min(a:Int,b:Int): Int {
        return if(a>b) b else a
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        bleLidarConnection=object : BLELidarConnection(bluetoothManager,this){
            override fun onConnectionStatusChange(status:Int){
                //TODO gestire risposte dalla classe
                if(status==LIDAR_CHARACTERISTIC_PARSED){
                    drawLidarData(bleLidarConnection.lidarPointList)
                    speedView.text=bleLidarConnection.currentSpeed.toString()
                    directionView.text=bleLidarConnection.currentDirection.toString()
                }else{
                    Log.d("BLELidarConnection","Status: $status")
                }

            }
        }

        //setup listener on the scan button
        scanButton.setOnClickListener {
            startConnection()
        }

        //when the view is created draw the default example data on it
        radarView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int) = Unit

            @RequiresApi(Build.VERSION_CODES.O)
            override fun surfaceCreated(holder: SurfaceHolder) {
                drawLidarData(null)
                //TODO startRadarLoop()
            }
        })

        //after create completes or when the app is reopened we check if bluetooth is enabled and prompt to enable it
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }

        if (!isLocationPermissionGranted) {
            requestLocationPermission()
        }
    }

    override fun onResume(){
        super.onResume()

        //after create completes or when the app is reopened we check if bluetooth is enabled and prompt to enable it
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLocationPermissionGranted && bluetoothAdapter.isEnabled) {
            bleLidarConnection.startBleScan()
        }else {
            Log.e("StartConnectionError","Location permission not granted or bluetooth off")
        }
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
data class LidarPoint(val angle: Int,val distance: Int, val intensity: Int)