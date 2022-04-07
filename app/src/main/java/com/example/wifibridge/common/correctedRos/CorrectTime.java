package com.example.wifibridge.common.correctedRos;

import java.io.StringReader;
import java.util.Calendar;
import java.util.Date;

import javax.json.Json;
import javax.json.JsonObject;

import edu.wpi.rail.jrosbridge.primitives.Duration;


public class CorrectTime extends CorrectTimeBase<CorrectTime>{
    public static final String TYPE = "time";

    /**
     * Create a new Time with a default of 0.
     */
    public CorrectTime() {
        super(CorrectTime.TYPE);
    }

    /**
     * Create a new Time with the given seconds and nanoseconds values.
     *
     * @param secs
     *            The seconds value of this time.
     * @param nsecs
     *            The nanoseconds value of this time.
     */
    public CorrectTime(int secs, int nsecs) {
        super(secs, nsecs, CorrectTime.TYPE);
    }

    /**
     * Create a new Time with the given time in seconds (and partial seconds).
     *
     * @param sec
     *            The time in seconds.
     */
    public CorrectTime(double sec) {
        super(sec, CorrectTime.TYPE);
    }

    /**
     * Create a new Time with the given time in nanoseconds.
     *
     * @param nano
     *            The time in nanoseconds.
     */
    public CorrectTime(long nano) {
        super(nano, CorrectTime.TYPE);
    }

    /**
     * Add the given Time to this Time and return a new Time with that value.
     *
     * @param t
     *            The Time to add.
     * @return A new Time with the new value.
     */
    @Override
    public CorrectTime add(CorrectTime t) {
        return new CorrectTime(this.toSec() + t.toSec());
    }

    /**
     * Subtract the given Time from this Time and return a new Time with that
     * value.
     *
     * @param t
     *            The Time to subtract.
     * @return A new Time with the new value.
     */
    @Override
    public CorrectTime subtract(CorrectTime t) {
        return new CorrectTime(this.toSec() - t.toSec());
    }

    /**
     * Check if this Time is valid. A time is valid if it is non-zero.
     *
     * @return If this Time is valid.
     */
    public boolean isValid() {
        return !this.isZero();
    }

    /**
     * Crate a new Java Date object based on this message.
     *
     * @return A new Java Date object based on this message.
     */
    public Date toDate() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis((long) (this.toSec() * (double) CorrectTimeBase.SECS_TO_MILLI));
        return c.getTime();
    }

    /**
     * Sleep until the given time.
     *
     * @param t
     *            The time to sleep until.
     * @return If the sleep was successful.
     */
    public static boolean sleepUntil(CorrectTime t) {
        // use a duration to sleep with
        return Duration.fromSec(t.subtract(CorrectTime.now()).toSec()).sleep();
    }

    /**
     * Create a clone of this Time.
     */
    @Override
    public CorrectTime clone() {
        return new CorrectTime(this.secs, this.nsecs);
    }

    /**
     * Create a new Time message based on the current system time. Note that
     * this might not match the current ROS time.
     *
     * @return The new Time message.
     */
    public static CorrectTime now() {
        return CorrectTime.fromSec(((double) System.currentTimeMillis())
                * CorrectTimeBase.MILLI_TO_SECS);
    }

    /**
     * Create a new Time message based on the given seconds.
     *
     * @param sec
     *            The time in seconds.
     *
     * @return The new Time primitive.
     */
    public static CorrectTime fromSec(double sec) {
        return new CorrectTime(sec);
    }

    /**
     * Create a new Time message based on the given nanoseconds.
     *
     * @param nano
     *            The time in nanoseconds.
     *
     * @return The new Time primitive.
     */
    public static CorrectTime fromNano(long nano) {
        return new CorrectTime(nano);
    }

    /**
     * Create a new Time from the given Java Data object.
     *
     * @param date
     *            The Date to create a Time from.
     * @return The resulting Time primitive.
     */
    public static CorrectTime fromDate(Date date) {
        return CorrectTime.fromSec(((double) date.getTime()) * CorrectTimeBase.MILLI_TO_SECS);
    }

    /**
     * Create a new Time based on the given JSON string. Any missing values will
     * be set to their defaults.
     *
     * @param jsonString
     *            The JSON string to parse.
     * @return A Time message based on the given JSON string.
     */
    public static CorrectTime fromJsonString(String jsonString) {
        // convert to a JSON object
        return CorrectTime.fromJsonObject(Json.createReader(
                new StringReader(jsonString)).readObject());
    }

    /**
     * Create a new Time based on the given JSON object. Any missing values will
     * be set to their defaults.
     *
     * @param jsonObject
     *            The JSON object to parse.
     * @return A Time message based on the given JSON object.
     */
    public static CorrectTime fromJsonObject(JsonObject jsonObject) {
        // check the fields
        int secs = jsonObject.containsKey(CorrectTime.FIELD_SECS) ? jsonObject
                .getInt(CorrectTime.FIELD_SECS) : 0;
        int nsecs = jsonObject.containsKey(CorrectTime.FIELD_NSECS) ? jsonObject
                .getInt(CorrectTime.FIELD_NSECS) : 0;
        return new CorrectTime(secs, nsecs);
    }
}
