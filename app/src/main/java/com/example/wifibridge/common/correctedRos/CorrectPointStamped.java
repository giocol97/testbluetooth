package com.example.wifibridge.common.correctedRos;

import javax.json.Json;
import javax.json.JsonObject;

import edu.wpi.rail.jrosbridge.messages.Message;
import edu.wpi.rail.jrosbridge.messages.geometry.Point;

public class CorrectPointStamped  extends Message {

    /**
     * The name of the header field for the message.
     */
    public static final String FIELD_HEADER = "header";

    /**
     * The name of the point field for the message.
     */
    public static final String FIELD_POINT = "point";

    /**
     * The message type.
     */
    public static final String TYPE = "geometry_msgs/PointStamped";

    private final CorrectHeader header;
    private final Point point;

    /**
     * Create a new PointStamped with all 0s.
     */
    public CorrectPointStamped() {
        this(new CorrectHeader(), new Point());
    }

    /**
     * Create a new PointStamped with the given values.
     *
     * @param header
     *            The header value of the point.
     * @param point
     *            The point value of the point.
     */
    public CorrectPointStamped(CorrectHeader header, Point point) {
        // build the JSON object
        super(Json.createObjectBuilder()
                        .add(CorrectPointStamped.FIELD_HEADER, header.toJsonObject())
                        .add(CorrectPointStamped.FIELD_POINT, point.toJsonObject()).build(),
                CorrectPointStamped.TYPE);
        this.header = header;
        this.point = point;
    }

    /**
     * Get the header value of this point.
     *
     * @return The header value of this point.
     */
    public CorrectHeader getHeader() {
        return this.header;
    }

    /**
     * Get the point value of this point.
     *
     * @return The point value of this point.
     */
    public Point getPoint() {
        return this.point;
    }

    /**
     * Create a clone of this PointStamped.
     */
    @Override
    public CorrectPointStamped clone() {
        return new CorrectPointStamped(this.header, this.point);
    }

    /**
     * Create a new PointStamped based on the given JSON string. Any missing
     * values will be set to their defaults.
     *
     * @param jsonString
     *            The JSON string to parse.
     * @return A PointStamped message based on the given JSON string.
     */
    public static CorrectPointStamped fromJsonString(String jsonString) {
        // convert to a message
        return CorrectPointStamped.fromMessage(new Message(jsonString));
    }

    /**
     * Create a new PointStamped based on the given Message. Any missing values
     * will be set to their defaults.
     *
     * @param m
     *            The Message to parse.
     * @return A PointStamped message based on the given Message.
     */
    public static CorrectPointStamped fromMessage(Message m) {
        // get it from the JSON object
        return CorrectPointStamped.fromJsonObject(m.toJsonObject());
    }

    /**
     * Create a new PointStamped based on the given JSON object. Any missing
     * values will be set to their defaults.
     *
     * @param jsonObject
     *            The JSON object to parse.
     * @return A PointStamped message based on the given JSON object.
     */
    public static CorrectPointStamped fromJsonObject(JsonObject jsonObject) {
        // check the fields
        CorrectHeader header = jsonObject.containsKey(CorrectPointStamped.FIELD_HEADER) ? CorrectHeader
                .fromJsonObject(jsonObject
                        .getJsonObject(CorrectPointStamped.FIELD_HEADER))
                : new CorrectHeader();
        Point point = jsonObject.containsKey(CorrectPointStamped.FIELD_POINT) ? Point
                .fromJsonObject(jsonObject
                        .getJsonObject(CorrectPointStamped.FIELD_POINT)) : new Point();
        return new CorrectPointStamped(header, point);
    }
}
