package com.example.wifibridge.common.correctedRos;

import javax.json.Json;
import javax.json.JsonObject;

import edu.wpi.rail.jrosbridge.messages.Message;

public class CorrectHeader extends Message {

    /**
     * The name of the timestamp field for the message.
     */
    public static final java.lang.String FIELD_STAMP = "stamp";

    /**
     * The name of the frame ID field for the message.
     */
    public static final java.lang.String FIELD_FRAME_ID = "frame_id";

    /**
     * The message type.
     */
    public static final java.lang.String TYPE = "std_msgs/Header";

    private final CorrectTime stamp;
    private final java.lang.String frameID;

    /**
     * Create a new Header with all empty values.
     */
    public CorrectHeader() {
        this(new CorrectTime(), "");
    }

    /**
     * Create a new Header with the given values.
     *
     * @param stamp
     *            The timestamp.
     * @param frameID
     *            The frame ID.
     */
    public CorrectHeader( CorrectTime stamp,
                  java.lang.String frameID) {
        // build the JSON object
        super(Json.createObjectBuilder()
                .add(CorrectHeader.FIELD_STAMP, stamp.toJsonObject())
                .add(CorrectHeader.FIELD_FRAME_ID, frameID).build(), CorrectHeader.TYPE);
        this.stamp = stamp;
        this.frameID = frameID;
    }


    /**
     * Get the timestamp value of this header.
     *
     * @return The timestamp value of this header.
     */
    public CorrectTime getStamp() {
        return this.stamp;
    }

    /**
     * Get the frame ID value of this header.
     *
     * @return The frame ID value of this header.
     */
    public java.lang.String getFrameID() {
        return this.frameID;
    }

    /**
     * Create a clone of this Header.
     */
    @Override
    public CorrectHeader clone() {
        // time primitives are mutable, create a clone
        return new CorrectHeader( this.stamp.clone(), this.frameID);
    }

    /**
     * Create a new Header based on the given JSON string. Any missing values
     * will be set to their defaults.
     *
     * @param jsonString
     *            The JSON string to parse.
     * @return A Header message based on the given JSON string.
     */
    public static CorrectHeader fromJsonString(java.lang.String jsonString) {
        // convert to a message
        return CorrectHeader.fromMessage(new Message(jsonString));
    }

    /**
     * Create a new Header based on the given Message. Any missing values will
     * be set to their defaults.
     *
     * @param m
     *            The Message to parse.
     * @return A Header message based on the given Message.
     */
    public static CorrectHeader fromMessage(Message m) {
        // get it from the JSON object
        return CorrectHeader.fromJsonObject(m.toJsonObject());
    }

    /**
     * Create a new Header based on the given JSON object. Any missing values
     * will be set to their defaults.
     *
     * @param jsonObject
     *            The JSON object to parse.
     * @return A Header message based on the given JSON object.
     */
    public static CorrectHeader fromJsonObject(JsonObject jsonObject) {
        // check the fields
        CorrectTime stamp = jsonObject
                .containsKey(CorrectHeader.FIELD_STAMP) ? CorrectTime
                .fromJsonObject(jsonObject.getJsonObject(CorrectHeader.FIELD_STAMP))
                : new CorrectTime();
        java.lang.String frameID = jsonObject
                .containsKey(CorrectHeader.FIELD_FRAME_ID) ? jsonObject
                .getString(CorrectHeader.FIELD_FRAME_ID) : "";

        // convert to a 32-bit number

        return new CorrectHeader( stamp, frameID);
    }
}
