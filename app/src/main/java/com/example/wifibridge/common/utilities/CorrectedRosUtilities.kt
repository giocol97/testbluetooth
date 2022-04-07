package com.example.wifibridge.common.utilities

import com.example.wifibridge.WebSocketConnection
import com.example.wifibridge.common.correctedRos.CorrectHeader
import com.example.wifibridge.common.correctedRos.CorrectLaserScan
import com.example.wifibridge.common.correctedRos.CorrectTime
import com.example.wifibridge.common.correctedRos.CorrectTwistStamped
import edu.wpi.rail.jrosbridge.messages.geometry.Twist
import edu.wpi.rail.jrosbridge.messages.geometry.Vector3

class CorrectedRosUtilities {

    companion object{
        //trasforma vettore 3d/quaternione da convenzione arcore (x verso destra, y verso alto, z verso schermo) a convezione ros (x verso retro telefono, y verso sinistra, z verso alto)
        fun arcoreToRosConvention(vec:FloatArray):FloatArray{
            if(vec.size==3){
                return floatArrayOf(-vec[2],-vec[0],vec[1])
            }

            if(vec.size==4){
                return floatArrayOf(-vec[2],-vec[0],vec[1],vec[3])
            }

            return floatArrayOf(0f)
        }

        //
        fun toCorrectLaserScan(packet: WebSocketConnection.PacketParsed, ts:Long=0L): CorrectLaserScan {
            val timestamp= CorrectTime(((packet.time).toLong()+ts)*1000000L)

            val header= CorrectHeader(timestamp,"laser_frame")
            val angle_min=Math.toRadians(packet.startAngle.toDouble()).toFloat()
            val angle_max=Math.toRadians(packet.endAngle.toDouble()).toFloat()//TODO attenzione a endAngle>360
            val angle_increment=Math.toRadians(1.toDouble()).toFloat()
            val time_increment=0.0002f//TODO verificare sia giusto, ylidar g2 ranging frequency 5000hz
            val scan_time=0.2f//TODO verificare sia giusto, potrebbe variare da 5 a 10hz
            val range_min=0.1f
            val range_max=12f

            val ranges=Array<Float>(packet.lidarPoints.size){i-> packet.lidarPoints[i].distance/1000f}
            val intensities=Array<Float>(packet.lidarPoints.size){i-> packet.lidarPoints[i].intensity.toFloat()}

            return CorrectLaserScan(header,angle_min,angle_max,angle_increment,time_increment,scan_time,range_min,range_max,ranges.toFloatArray(),intensities.toFloatArray())

        }

        //
        fun toCorrectTwistStamped(packet: WebSocketConnection.PacketParsed, ts:Long=0L, currentDirection:Int): CorrectTwistStamped {
            val timestamp= CorrectTime(((packet.time).toLong()+ts)*1000000L)

            val header= CorrectHeader(timestamp,"wheel_frame")
            val speed=packet.speed/3.6f //convert to metres per second
            //TODO direzione sterzo non disponibile in prototipo twist, direzione fissata a 0 gradi per il momento
            /*var directionAngle=-0.000924f*packet.direction+0.7854f //direction +45/-45 degrees only with steering values 0/1700

            if(currentDirection<0){//if direction is backward the angle of movement is inverted //TODO passare la marcia tramite pacchetto per rendere indipendente da arcore
                directionAngle=-directionAngle + PI.toFloat()
            }*/

            val linearVelocity= Vector3(speed.toDouble(), 0.0,0.0) //speed is all directed towards x axis
            //val angolarVelocity= Vector3(0.0,0.0,directionAngle.toDouble()) //steering acts in the z axis
            val angolarVelocity= Vector3(0.0,0.0,0.0) //steering acts in the z axis
            val twist= Twist(linearVelocity,angolarVelocity)

            return CorrectTwistStamped(header, twist)
        }
    }
}