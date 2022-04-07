package com.example.wifibridge.common

import android.util.Log
import com.example.wifibridge.WebSocketConnection
import com.example.wifibridge.common.correctedRos.CorrectHeader
import com.example.wifibridge.common.correctedRos.CorrectPoseStamped
import com.example.wifibridge.common.correctedRos.CorrectTime
import com.example.wifibridge.common.correctedRos.CorrectTwistStamped
import com.example.wifibridge.common.utilities.CorrectedRosUtilities
import edu.wpi.rail.jrosbridge.Ros
import edu.wpi.rail.jrosbridge.Topic

//rosbridge connection constants
private const val ROS_SERVER_ADDRESS="192.168.1.3"
private const val ROS_SERVER_PORT=9090
private const val ROS_TRANSFER_TYPE="wheelchair:lidar"//opzioni-> pose - wheelchair - lidar - all, possibile mettere una o più opzioni separate da : senza spazi (ES pose:lidar ), all attiva tutti i topic

open class RosConnectionManager {

    companion object{
        const val ROSBRIDGE_ACTIVE=true

        //su prototipo AIT diverse funzionalità sono disattivate
        const val IS_AIT=false
    }

    //ROS VARIABLES
    private lateinit var ros: Ros
    private lateinit var poseTopic: Topic
    private lateinit var wheelchairTopic: Topic
    private lateinit var lidarTopic: Topic
    private lateinit var controlTopic: Topic
    private lateinit var heartbeatTopic: Topic

    private lateinit var commandFeedbackPhoneTopic: Topic
    private lateinit var commandFeedbackControllerTopic: Topic

    //tengo traccia dell'ultimo angolo che è stato richiesto allo sterzo
    var lastAngle=0f

    var lastControlTimestamp=0L

    //TODO gestire riconnessione a rosbridge dopo disconnessione dovuta a possibili problemi wifi o onPause

    fun connect():Boolean {
        if(ROSBRIDGE_ACTIVE){
            ros = Ros(ROS_SERVER_ADDRESS, ROS_SERVER_PORT)
            if(!ros.connect()){
                return false
            }

            //creo i topic selezionati e li pubblico
            val topicsSelected=ROS_TRANSFER_TYPE.split(":")

            if("pose" in topicsSelected || "all" in topicsSelected){
                poseTopic=Topic(ros,"/phone_pose","geometry_msgs/PoseStamped")
                poseTopic.advertise()
            }

            if("wheelchair" in topicsSelected || "all" in topicsSelected){
                wheelchairTopic=Topic(ros,"/wheelchair_motion","geometry_msgs/TwistStamped")
                wheelchairTopic.advertise()
            }

            if("lidar" in topicsSelected || "all" in topicsSelected){
                lidarTopic=Topic(ros,"/lidar_data","sensor_msgs/LaserScan")
                lidarTopic.advertise()
            }

            heartbeatTopic=Topic(ros,"/heartbeat","std_msgs/Header")//TODO quando piu dati disponibili rendere utile
            heartbeatTopic.advertise()

            commandFeedbackPhoneTopic=Topic(ros,"/command_feedback_phone","std_msgs/Header")
            commandFeedbackPhoneTopic.advertise()

            commandFeedbackControllerTopic=Topic(ros,"/command_feedback_controller","std_msgs/Header")
            commandFeedbackControllerTopic.advertise()

            //mi iscrivo al topic dove Ros pubblicherà i comandi di controllo per la carrozzina, verranno gestiti indipendentemente da questa classe
            controlTopic=Topic(ros, "/wheelchair_control", "geometry_msgs/TwistStamped")
            controlTopic.subscribe { msg ->
                val twistCommand=CorrectTwistStamped.fromJsonObject(msg.toJsonObject())
                //val twistCommand=Twist.fromJsonObject(msg.toJsonObject())
                val currentControlTimestamp=twistCommand.header.stamp.toNSec()
                Log.d("ASD","Comando: ${twistCommand.twist.linear.x.toFloat()}, ${twistCommand.twist.angular.z.toFloat()}")

                //val currentControlTimestamp=100L
                if(lastControlTimestamp!=0L){
                    val period=(currentControlTimestamp - lastControlTimestamp) * 1e-9f
                    processControlMessage(twistCommand.twist.linear.x.toFloat(),twistCommand.twist.angular.z.toFloat(),period)
                }
                lastControlTimestamp=currentControlTimestamp
            }

            return true
        }else{
            Log.d("RosBridgeDisabledNotice","RosBridge is disabled for this session")
            return false
        }

    }

    open fun processControlMessage(linearVelocity:Float, angularVelocity:Float, period:Float){}

    fun isConnected():Boolean{
        return ros.isConnected
    }

    fun publishOnPoseTopic(translation:FloatArray, rotationQuaternion:FloatArray, timestamp:Long){
        if(ROSBRIDGE_ACTIVE && ros.isConnected && ::poseTopic.isInitialized) {
            val t = CorrectedRosUtilities.arcoreToRosConvention(translation)
            val point=edu.wpi.rail.jrosbridge.messages.geometry.Point(t[0].toDouble(),t[1].toDouble(),t[2].toDouble())

            val q = CorrectedRosUtilities.arcoreToRosConvention(rotationQuaternion)
            val quaternion=edu.wpi.rail.jrosbridge.messages.geometry.Quaternion(q[0].toDouble(),q[1].toDouble(),q[2].toDouble(),q[3].toDouble())
            val toSend = edu.wpi.rail.jrosbridge.messages.geometry.Pose(point,quaternion)

            val header = CorrectHeader(CorrectTime(timestamp),"phone_frame")

            val poseTimestamped=
                CorrectPoseStamped(
                    header,
                    toSend
                )
            poseTopic.publish(poseTimestamped)
        }
    }

    fun publishOnWheelchairTopic(packetParsed: WebSocketConnection.PacketParsed, timeSyncronization:Long, direction:Int){
        if(ROSBRIDGE_ACTIVE && ros.isConnected && ::wheelchairTopic.isInitialized) {
            wheelchairTopic.publish(CorrectedRosUtilities.toCorrectTwistStamped(packetParsed,timeSyncronization,direction))
        }
    }

    fun publishOnLidarTopic(packetParsed: WebSocketConnection.PacketParsed, timeSyncronization:Long){
        if(ROSBRIDGE_ACTIVE && ros.isConnected && ::lidarTopic.isInitialized) {
            lidarTopic.publish(CorrectedRosUtilities.toCorrectLaserScan(packetParsed,timeSyncronization))
        }
    }

    fun publishOnHeartbeatTopic(timestamp:Long){
        if(ROSBRIDGE_ACTIVE && ros.isConnected && ::heartbeatTopic.isInitialized) {
            heartbeatTopic.publish(CorrectHeader(CorrectTime(timestamp),"heartbeat"))
        }
    }

    fun publishOnCommandFeedbackPhoneTopic(command:String,timestamp:Long){
        if(ROSBRIDGE_ACTIVE && ros.isConnected && ::commandFeedbackPhoneTopic.isInitialized){
            commandFeedbackPhoneTopic.publish(CorrectHeader(CorrectTime(timestamp),command))
        }
    }

    fun publishOnCommandFeedbackControllerTopic(command:String,timestamp:Long){
        if(ROSBRIDGE_ACTIVE && ros.isConnected && ::commandFeedbackControllerTopic.isInitialized){
            commandFeedbackControllerTopic.publish(CorrectHeader(CorrectTime(timestamp),command))
        }
    }

    fun disconnect(){
        if (ROSBRIDGE_ACTIVE && ros.isConnected){
            ros.disconnect()
        }
    }

    //TODO gestire controllo angular velocity troppo elevato da un'altra parte
    /*fun twistToControlAngle(linearVelocity:Float,angularVelocity:Float):Float{
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
    }*/
}