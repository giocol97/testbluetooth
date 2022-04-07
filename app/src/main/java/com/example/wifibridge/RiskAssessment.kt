package com.example.wifibridge

import android.annotation.SuppressLint
import java.lang.Float.max
import kotlin.math.pow

class RiskAssessment(val timePeriod:Int) {

    val probabilitiesArcore=FloatArray(13){-1f}//(25){-1f} TODO inserire predizioni future
    val probabilitiesLidar=FloatArray(13){-1f}

    val uncertaintyTolerance=0.6f

    var stringToOutput=""

    fun step(arcoreLogOddsSum:Float,lidarDetections:Float){//arcorePredictions:FloatArray TODO aggiungere le predizioni future
        probabilitiesArcore.rotateLeft(1)
        probabilitiesArcore[12]=if(arcoreLogOddsSum!=-1f){//se non ho dati aggiornati inserisco il valore più incerto possibile
            arcoreSingleProbability(arcoreLogOddsSum)
        }else{
            0.5f
        }

        //TODO aggiungere le predizioni future e possibilmente valutare precisione di predizioni precedenti
        /*for(i in arcorePredictions.indices){
            probabilitiesArcore[13+i]=arcorePredictions[i]
        }*/
        probabilitiesLidar.rotateLeft(1)
        probabilitiesLidar[12]=if(lidarDetections!=-1f){//se non ho dati aggiornati inserisco il valore più incerto possibile
            lidarSingleProbability(lidarDetections)
        }else{
            0.5f
        }

    }

    //trasformo il valore da somme di logodds a una probabilità d'impatto
    fun arcoreSingleProbability(value:Float):Float{
        if(value<50f){
            return 0f
        }
        if(value>4000f){
            return 1f
        }

        return (0.00025316455696202533f*value-0.012658227848101333f)
    }

    fun arcoreAggregateProbability(values:FloatArray):Float{
        var sum=0f
        values.forEach { sum+=it }
        return sum/values.size
    }

    //trasformo il valore di percentuale di angoli lidar a distanza pericolosa in una probabilità d'impatto
    fun lidarSingleProbability(value:Float):Float{
        if(value<0.2f){
            return 0f
        }
        if(value>0.4f){
            return 1f
        }

        return (5f*value-1f)
    }

    fun lidarAggregateProbability(values:FloatArray):Float{
        var max=-1f
        values.forEach { if(it>max){max=it} }
        return max
    }

    @SuppressLint("NewApi")
    fun aggregateProbability(arcoreProbability:Float, lidarProbability:Float):Float{
        return max(arcoreProbability,lidarProbability)
    }

    fun shouldBrake():Boolean{
        var countArcore=0
        var countLidar=0
        var count=0

        val arcoreOldestProbability = arcoreAggregateProbability(probabilitiesArcore.copyOfRange(0,5))
        val arcoreOldProbability = arcoreAggregateProbability(probabilitiesArcore.copyOfRange(5,10))
        val arcoreCurrentProbability = arcoreAggregateProbability(probabilitiesArcore.copyOfRange(10,13))//,15))//TODO inserire predizioni
        //val arcoreNextProbability = arcoreAggregateProbability(probabilitiesArcore.copyOfRange(15,20))//TODO
        //val arcoreFutureProbability = arcoreAggregateProbability(probabilitiesArcore.copyOfRange(20,25))//TODO

        if(arcoreOldestProbability>uncertaintyTolerance) countArcore++
        if(arcoreOldProbability>uncertaintyTolerance) countArcore++
        if(arcoreCurrentProbability>uncertaintyTolerance) countArcore++


        val lidarOldestProbability = lidarAggregateProbability(probabilitiesLidar.copyOfRange(0,5))
        val lidarOldProbability = lidarAggregateProbability(probabilitiesLidar.copyOfRange(5,10))
        val lidarCurrentProbability = lidarAggregateProbability(probabilitiesLidar.copyOfRange(10,13))

        if(lidarOldestProbability>uncertaintyTolerance) countLidar++
        if(lidarOldProbability>uncertaintyTolerance) countLidar++
        if(lidarCurrentProbability>uncertaintyTolerance) countLidar++

        val oldestProbability=aggregateProbability(arcoreOldestProbability,lidarOldestProbability)
        val oldProbability=aggregateProbability(arcoreOldProbability,lidarOldProbability)
        val currentProbability=aggregateProbability(arcoreCurrentProbability,lidarCurrentProbability)

        if(oldestProbability>uncertaintyTolerance) count++
        if(oldProbability>uncertaintyTolerance) count++
        if(currentProbability>uncertaintyTolerance) count++

        stringToOutput="AR: ${arcoreOldestProbability.format(2)} | ${arcoreOldProbability.format(2)} | ${arcoreCurrentProbability.format(2)}  | count: $countArcore\n"
        stringToOutput+="LI: ${lidarOldestProbability.format(2)} | ${lidarOldProbability.format(2)} | ${lidarCurrentProbability.format(2)} | count: $countLidar\n"
        stringToOutput+="SB: ${oldestProbability.format(2)} | ${oldProbability.format(2)} | ${currentProbability.format(2)} | count: $count"

        return count>=2
    }



    fun probabilitiesToString():String{
        /*val output=StringBuilder()

        output.append(stringToOutput)

        return output.toString()*/

        return stringToOutput
    }

companion object{
    fun Float.format(digits: Int, dot: Boolean=false):String{
        if(dot){
            return "%.${digits}f".format(this).replace(",",".")
        }else{

            return "%.${digits}f".format(this)
        }
    }


    fun FloatArray.rotateLeft(n:Int){
        for(j in 0 until n){
            for (i in 0 until this.size-1){
                this[i]=this[i+1]
            }
            this[size-1]=-1f
        }
    }

    fun computeLidarRiskProbability(lidarPacket:WebSocketConnection.PacketParsed):Float {
        val shift = 180
        val targetDistance = lidarPacket.speed.pow(2) / 200f + 0.16f + 0.3f //formula per distanza di frenata basata su velocità in km/h, 0.16m è distanza lidar-fronte ruota, 0.3m è un valore di tolleranza
        var pointsInArea = 0
        var dangerousPointsInArea = 0
        //for (i in -18..18) {
        for (i in -9..9) {//area ristretta per prova contro falsi positivi
            if (lidarPacket.lidarPoints[i + shift].distance!=-1f && lidarPacket.lidarPoints[i + shift].distance / 1000f < targetDistance) {
                pointsInArea++
                dangerousPointsInArea++
            } else {
                pointsInArea++
            }
        }

        return (dangerousPointsInArea.toFloat() / pointsInArea.toFloat())
    }
}

}