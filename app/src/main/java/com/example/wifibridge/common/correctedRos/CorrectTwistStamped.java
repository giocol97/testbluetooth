package com.example.wifibridge.common.correctedRos;

import javax.json.Json;
import javax.json.JsonObject;

import edu.wpi.rail.jrosbridge.messages.Message;
import edu.wpi.rail.jrosbridge.messages.geometry.Twist;

/**
 * The geometry_msgs/TwistStamped message. A twist with reference coordinate
 * frame and timestamp.
 *
 * @author Russell Toris -- russell.toris@gmail.com
 * @version April 1, 2014
 */
public class CorrectTwistStamped extends Message {

    /**
     * The name of the header field for the message.
     */
    public static final String FIELD_HEADER = "header";

    /**
     * The name of the twist field for the message.
     */
    public static final String FIELD_TWIST = "twist";

    /**
     * The message type.
     */
    public static final String TYPE = "geometry_msgs/TwistStamped";

    private final CorrectHeader header;
    private final Twist twist;

    /**
     * Create a new TwistStamped with all 0s.
     */
    public CorrectTwistStamped() {
        this(new CorrectHeader(), new Twist());
    }

    /**
     * Create a new TwistStamped with the given values.
     *
     * @param header
     *            The header value of the twist.
     * @param twist
     *            The twist value of the twist.
     */
    public CorrectTwistStamped(CorrectHeader header, Twist twist) {
        // build the JSON object
        super(Json.createObjectBuilder()
                        .add(CorrectTwistStamped.FIELD_HEADER, header.toJsonObject())
                        .add(CorrectTwistStamped.FIELD_TWIST, twist.toJsonObject()).build(),
                CorrectTwistStamped.TYPE);
        this.header = header;
        this.twist = twist;
    }

    /**
     * Get the header value of this twist.
     *
     * @return The header value of this twist.
     */
    public CorrectHeader getHeader() {
        return this.header;
    }

    /**
     * Get the twist value of this twist.
     *
     * @return The twist value of this twist.
     */
    public Twist getTwist() {
        return this.twist;
    }

    /**
     * Create a clone of this TwistStamped.
     */
    @Override
    public CorrectTwistStamped clone() {
        return new CorrectTwistStamped(this.header, this.twist);
    }

    /**
     * Create a new TwistStamped based on the given JSON string. Any missing
     * values will be set to their defaults.
     *
     * @param jsonString
     *            The JSON string to parse.
     * @return A TwistStamped message based on the given JSON string.
     */
    public static CorrectTwistStamped fromJsonString(String jsonString) {
        // convert to a message
        return CorrectTwistStamped.fromMessage(new Message(jsonString));
    }

    /**
     * Create a new TwistStamped based on the given Message. Any missing values
     * will be set to their defaults.
     *
     * @param m
     *            The Message to parse.
     * @return A TwistStamped message based on the given Message.
     */
    public static CorrectTwistStamped fromMessage(Message m) {
        // get it from the JSON object
        return CorrectTwistStamped.fromJsonObject(m.toJsonObject());
    }

    /**
     * Create a new TwistStamped based on the given JSON object. Any missing
     * values will be set to their defaults.
     *
     * @param jsonObject
     *            The JSON object to parse.
     * @return A TwistStamped message based on the given JSON object.
     */
    public static CorrectTwistStamped fromJsonObject(JsonObject jsonObject) {
        // check the fields
        CorrectHeader header = jsonObject.containsKey(CorrectTwistStamped.FIELD_HEADER) ? CorrectHeader
                .fromJsonObject(jsonObject
                        .getJsonObject(CorrectTwistStamped.FIELD_HEADER))
                : new CorrectHeader();
        Twist twist = jsonObject.containsKey(CorrectTwistStamped.FIELD_TWIST) ? Twist
                .fromJsonObject(jsonObject
                        .getJsonObject(CorrectTwistStamped.FIELD_TWIST)) : new Twist();
        return new CorrectTwistStamped(header, twist);
    }
}
