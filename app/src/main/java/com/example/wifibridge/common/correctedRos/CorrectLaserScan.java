package com.example.wifibridge.common.correctedRos;

import java.io.StringReader;
import java.util.Arrays;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import edu.wpi.rail.jrosbridge.messages.Message;

public class CorrectLaserScan  extends Message{

    /**
     * The name of the header field for the message.
     */
    public static final String FIELD_HEADER = "header";

    public static final String FIELD_ANGLE_MIN = "angle_min";
    public static final String FIELD_ANGLE_MAX = "angle_max";
    public static final String FIELD_ANGLE_INCREMENT = "angle_increment";
    public static final String FIELD_TIME_INCREMENT = "time_increment";
    public static final String FIELD_SCAN_TIME = "scan_time";
    public static final String FIELD_RANGE_MIN = "range_min";
    public static final String FIELD_RANGE_MAX = "range_max";
    public static final String FIELD_RANGES = "ranges";
    public static final String FIELD_INTENSITIES = "intensities";


    /**
     * The message type.
     */
    public static final String TYPE = "sensor_msgs/LaserScan";

    private final CorrectHeader header;
    private final float angle_min;
    private final float angle_max;
    private final float angle_increment;
    private final float time_increment;
    private final float scan_time;
    private final float range_min;
    private final float range_max;
    private final float[] ranges;
    private final float[] intensities;

    /**
     * Create a new CorrectLaserScan with all 0s. //TODO togliere e basta
     */
    public CorrectLaserScan() {
        this(new CorrectHeader(), 0,0,0,0,0,0,0, new float[0],new float[0]);
    }

    /**
     * Create a new CorrectLaserScan with the given values.
     */
    public CorrectLaserScan(CorrectHeader header, float angle_min,float angle_max,float angle_increment,float time_increment,float scan_time,float range_min,float range_max,float[] ranges,float[] intensities) {
        // build the JSON object
        super(Json.createObjectBuilder()
                        .add(CorrectLaserScan.FIELD_HEADER, header.toJsonObject())
                        .add(CorrectLaserScan.FIELD_ANGLE_MIN, angle_min)
                        .add(CorrectLaserScan.FIELD_ANGLE_MAX, angle_max)
                        .add(CorrectLaserScan.FIELD_ANGLE_INCREMENT, angle_increment)
                        .add(CorrectLaserScan.FIELD_TIME_INCREMENT, time_increment)
                        .add(CorrectLaserScan.FIELD_SCAN_TIME, scan_time)
                        .add(CorrectLaserScan.FIELD_RANGE_MIN, range_min)
                        .add(CorrectLaserScan.FIELD_RANGE_MAX, range_max)
                        .add(CorrectLaserScan.FIELD_RANGES, Json.createReader(
                                new StringReader(Arrays.toString(ranges)))
                                .readArray())
                        .add(CorrectLaserScan.FIELD_INTENSITIES, Json.createReader(
                                new StringReader(Arrays.toString(intensities)))
                                .readArray()).build(),
                CorrectLaserScan.TYPE);
        this.header = header;
        this.angle_min=angle_min ;
        this.angle_max =angle_max ;
        this.angle_increment =angle_increment ;
        this.time_increment =time_increment ;
        this.scan_time =scan_time ;
        this.range_min =range_min ;
        this.range_max =range_max;

        this.ranges =new float[ranges.length];
        System.arraycopy(ranges, 0, this.ranges, 0, ranges.length);
        this.intensities =new float[intensities.length];
        System.arraycopy(intensities, 0, this.intensities, 0, intensities.length);
    }

    /**
     * Get the header value of this pose.
     *
     * @return The header value of this pose.
     */
    public CorrectHeader getHeader() {
        return this.header;
    }

    /**
     * Create a clone of this PoseStamped.
     */
    @Override
    public CorrectLaserScan clone() {
        return new CorrectLaserScan(this.header,this.angle_min,this.angle_max ,this.angle_increment, this.time_increment , this.scan_time, this.range_min , this.range_max,this.ranges,this.intensities );
    }

    /**
     * Create a new PoseStamped based on the given JSON string. Any missing
     * values will be set to their defaults.
     *
     * @param jsonString
     *            The JSON string to parse.
     * @return A PoseStamped message based on the given JSON string.
     */
    public static CorrectLaserScan fromJsonString(String jsonString) {
        // convert to a message
        return CorrectLaserScan.fromMessage(new Message(jsonString));
    }

    /**
     * Create a new PoseStamped based on the given Message. Any missing values
     * will be set to their defaults.
     *
     * @param m
     *            The Message to parse.
     * @return A PoseStamped message based on the given Message.
     */
    public static CorrectLaserScan fromMessage(Message m) {
        // get it from the JSON object
        return CorrectLaserScan.fromJsonObject(m.toJsonObject());
    }

    /**
     * Create a new PoseStamped based on the given JSON object. Any missing
     * values will be set to their defaults.
     *
     * @param jsonObject
     *            The JSON object to parse.
     * @return A PoseStamped message based on the given JSON object.
     */
    public static CorrectLaserScan fromJsonObject(JsonObject jsonObject) {
        // check the fields
        CorrectHeader header = jsonObject.containsKey(CorrectLaserScan.FIELD_HEADER) ? CorrectHeader
                .fromJsonObject(jsonObject
                        .getJsonObject(CorrectLaserScan.FIELD_HEADER))
                : new CorrectHeader();

        float angle_min = jsonObject.containsKey(CorrectLaserScan.FIELD_ANGLE_MIN) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_ANGLE_MIN).doubleValue() : 0;
        float angle_max = jsonObject.containsKey(CorrectLaserScan.FIELD_ANGLE_MAX) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_ANGLE_MAX).doubleValue() : 0;
        float angle_increment = jsonObject.containsKey(CorrectLaserScan.FIELD_ANGLE_INCREMENT) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_ANGLE_INCREMENT).doubleValue() : 0;
        float time_increment = jsonObject.containsKey(CorrectLaserScan.FIELD_TIME_INCREMENT) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_TIME_INCREMENT).doubleValue() : 0;
        float scan_time = jsonObject.containsKey(CorrectLaserScan.FIELD_SCAN_TIME) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_SCAN_TIME).doubleValue() : 0;
        float range_min = jsonObject.containsKey(CorrectLaserScan.FIELD_RANGE_MIN) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_RANGE_MIN).doubleValue() : 0;
        float range_max = jsonObject.containsKey(CorrectLaserScan.FIELD_RANGE_MAX) ? (float) jsonObject.getJsonNumber(CorrectLaserScan.FIELD_RANGE_MAX).doubleValue() : 0;

        JsonArray jsonRanges = jsonObject.getJsonArray(CorrectLaserScan.FIELD_RANGES);
        float[] ranges = new float[jsonRanges.size()];
        if (jsonRanges.size()>0) {
            for (int i = 0; i < ranges.length; i++) {
                ranges[i] = (float) jsonRanges.getJsonNumber(i).doubleValue();
            }
        } else {
            //TODO
        }

        JsonArray jsonIntensities = jsonObject.getJsonArray(CorrectLaserScan.FIELD_INTENSITIES);
        float[] intensities = new float[jsonIntensities.size()];
        if (jsonIntensities.size()>0) {
            for (int i = 0; i < intensities.length; i++) {
                intensities[i] = (float) jsonIntensities.getJsonNumber(i).doubleValue();
            }
        } else {
            //TODO
        }


        return new CorrectLaserScan(header, angle_min,angle_max,angle_increment,time_increment,scan_time,range_min,range_max,ranges,intensities);
    }
}
