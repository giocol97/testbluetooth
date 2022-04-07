package com.example.wifibridge.common.correctedRos;

import javax.json.Json;

import edu.wpi.rail.jrosbridge.primitives.Primitive;

public abstract class CorrectTimeBase<T extends Primitive> extends Primitive implements
        Comparable<CorrectTimeBase<T>>{
    public static final String FIELD_SECS = "sec";

    /**
     * The name of the nanoseconds field for the Primitive.
     */
    public static final String FIELD_NSECS = "nanosec";

    /**
     * The number of milliseconds in a second.
     */
    protected static final long SECS_TO_MILLI = 1000;

    /**
     * The fraction of a second in a millisecond.
     */
    protected static final double MILLI_TO_SECS = 0.001;

    /**
     * The number of nanoseconds in a second.
     */
    protected static final long SECS_TO_NSECS = 1000000000l;

    /**
     * The fraction of a second in a nanosecond.
     */
    protected static final double NSECS_TO_SECS = 1e-9;

    /**
     * The number of milliseconds in a second.
     */
    protected static final long MILLI_TO_NSECS = 1000000;

    /**
     * The number of milliseconds in a second.
     */
    protected static final double NSECS_TO_MILLI = 1e-6;

    public final int secs, nsecs;

    /**
     * Create an empty CorrectTimeBase with the given type field.
     *
     * @param type
     *            The type of primitive.
     */
    public CorrectTimeBase(String type) {
        this(0, 0, type);
    }

    /**
     * Create a new CorrectTimeBase with the given time in seconds (and partial
     * seconds).
     *
     * @param sec
     *            The time in seconds.
     * @param type
     *            The type of CorrectTimeBase primitive.
     */
    public CorrectTimeBase(double sec, String type) {
        this((long) (sec * CorrectTimeBase.SECS_TO_NSECS), type);
    }

    /**
     * Create a new CorrectTimeBase with the given time in nanoseconds.
     *
     * @param nano
     *            The time in nanoseconds.
     * @param type
     *            The type of CorrectTimeBase primitive.
     */
    public CorrectTimeBase(long nano, String type) {
        // extract seconds and nanoseconds
        this((int) (nano / CorrectTimeBase.SECS_TO_NSECS),
                (int) (nano % CorrectTimeBase.SECS_TO_NSECS), type);
    }

    /**
     * Create a new CorrectTimeBase with the given time in seconds and nanoseconds.
     *
     * @param secs
     *            The amount of seconds.
     * @param nsecs
     *            The amount of additional nanoseconds.
     * @param type
     *            The type of CorrectTimeBase primitive.
     */
    public CorrectTimeBase(int secs, int nsecs, String type) {
        // build the JSON object
        super(Json.createObjectBuilder().add(CorrectTimeBase.FIELD_SECS, secs)
                .add(CorrectTimeBase.FIELD_NSECS, nsecs).build(), type);
        this.secs = secs;
        this.nsecs = nsecs;
    }

    /**
     * Get the seconds value of this CorrectTimeBase.
     *
     * @return The seconds value of this CorrectTimeBase.
     */
    public int getSecs() {
        return this.secs;
    }

    /**
     * Get the nanoseconds value of this CorrectTimeBase.
     *
     * @return The nanoseconds value of this CorrectTimeBase.
     */
    public int getNsecs() {
        return this.nsecs;
    }

    /**
     * Check if the value of this CorrectTimeBase is zero.
     *
     * @return If the value of this CorrectTimeBase is zero.
     */
    public boolean isZero() {
        return (this.secs + this.nsecs) == 0;
    }

    /**
     * Convert this CorrectTimeBase to seconds (and partial seconds).
     *
     * @return This CorrectTimeBase to seconds (and partial seconds).
     */
    public double toSec() {
        return this.secs + (CorrectTimeBase.NSECS_TO_SECS * (double) this.nsecs);
    }

    /**
     * Convert this CorrectTimeBase to nanoseconds.
     *
     * @return This CorrectTimeBase to nanoseconds.
     */
    public long toNSec() {
        return ((long) (this.secs * CorrectTimeBase.SECS_TO_NSECS))
                + ((long) this.nsecs);
    }

    /**
     * Compare the given CorrectTimeBase object to this one.
     *
     * @param t
     *            The CorrectTimeBase to compare to.
     * @return 0 if the values are equal, less than 0 if t is less that this
     *         CorrectTimeBase, and greater than 0 otherwise.
     */
    @Override
    public int compareTo(CorrectTimeBase<T> t) {
        return Double.compare(this.toSec(), t.toSec());
    }

    /**
     * Add the given type to this CorrectTimeBase and return a new object with that
     * value.
     *
     * @param t
     *            Add the given type to this CorrectTimeBase.
     * @return A new object with the new value.
     */
    public abstract T add(T t);

    /**
     * Subtract the given type to this CorrectTimeBase and return a new object with
     * that value.
     *
     * @param t
     *            Subtract the given type to this CorrectTimeBase.
     * @return A new object with the new value.
     */
    public abstract T subtract(T t);

    /**
     * Create a clone of this CorrectTimeBase.
     *
     * @return A clone of this CorrectTimeBase.
     */
    @Override
    public abstract T clone();
}
