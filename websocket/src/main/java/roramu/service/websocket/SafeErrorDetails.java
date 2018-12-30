package roramu.service.websocket;

import roramu.util.json.JsonUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A subset of information from a Java {@link Throwable} that is safe to
 * serialize.
 */
public final class SafeErrorDetails {
    private static final int DEFAULT_MAX_STACK_TRACE_DEPTH = 3;

    private String error = null;
    private String[] reasons = null;
    private StackTraceElement[] stackTrace = null;

    /**
     * Private default constructor for serialization purposes.
     */
    private SafeErrorDetails() {}

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     */
    public SafeErrorDetails(String error) {
        this(error, (String) null, null, 0);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param reason The reasons for the error.
     */
    public SafeErrorDetails(String error, String reason) {
        this(error, reason, null, 0);
    }

    /**
     * Creates a safely serializable error message. Does not limit the stack
     * trace depth.
     *
     * @param error The error.
     * @param reason The reasons for the error.
     * @param stackTrace The stack trace.
     */
    public SafeErrorDetails(String error, String reason, StackTraceElement[] stackTrace) {
        this(error, reason, stackTrace, stackTrace.length);
    }

    /**
     * Creates a safely serializable error message. Does not limit the stack
     * trace depth.
     *
     * @param error The error.
     * @param stackTrace The stack trace.
     */
    public SafeErrorDetails(String error, StackTraceElement[] stackTrace) {
        this(error, (String) null, stackTrace, stackTrace.length);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param stackTrace The stack trace.
     * @param limitStackTraceDepth Whether or not to limit the stack trace depth
     * to the default value of {@value DEFAULT_MAX_STACK_TRACE_DEPTH}.
     */
    public SafeErrorDetails(String error, StackTraceElement[] stackTrace, boolean limitStackTraceDepth) {
        this(error, (String[]) null, stackTrace, limitStackTraceDepth);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param stackTrace The stack trace.
     * @param maxStackTraceDepth The maximum number of elements to take from the
     * provided stack trace.
     */
    public SafeErrorDetails(String error, StackTraceElement[] stackTrace, int maxStackTraceDepth) {
        this(error, (String) null, stackTrace, maxStackTraceDepth);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param reason The reasons for the error.
     * @param stackTrace The stack trace.
     * @param limitStackTraceDepth Whether or not to limit the stack trace depth
     * to the default value of {@value DEFAULT_MAX_STACK_TRACE_DEPTH}.
     */
    public SafeErrorDetails(String error, Throwable reason, StackTraceElement[] stackTrace, boolean limitStackTraceDepth) {
        this(error, reason == null ? null : getReasonsFromThrowable(reason), stackTrace, limitStackTraceDepth);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param reasons The reasons for the error.
     * @param stackTrace The stack trace.
     * @param limitStackTraceDepth Whether or not to limit the stack trace depth
     * to the default value of {@value DEFAULT_MAX_STACK_TRACE_DEPTH}.
     */
    public SafeErrorDetails(String error, String[] reasons, StackTraceElement[] stackTrace, boolean limitStackTraceDepth) {
        this(error, reasons, stackTrace, limitStackTraceDepth ? DEFAULT_MAX_STACK_TRACE_DEPTH : stackTrace.length);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param reason The reasons for the error.
     * @param stackTrace The stack trace.
     * @param maxStackTraceDepth The maximum number of elements to take from the
     * provided stack trace.
     */
    public SafeErrorDetails(String error, Throwable reason, StackTraceElement[] stackTrace, int maxStackTraceDepth) {
        this(error, reason == null ? null : getReasonsFromThrowable(reason), stackTrace, maxStackTraceDepth);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param reason The reasons for the error.
     * @param stackTrace The stack trace.
     * @param maxStackTraceDepth The maximum number of elements to take from the
     * provided stack trace.
     */
    public SafeErrorDetails(String error, String reason, StackTraceElement[] stackTrace, int maxStackTraceDepth) {
        this(error, new String[]{reason}, stackTrace, maxStackTraceDepth);
    }

    /**
     * Creates a safely serializable error message.
     *
     * @param error The error.
     * @param reason The reasons for the error.
     * @param stackTrace The stack trace.
     * @param maxStackTraceDepth The maximum number of elements to take from the
     * provided stack trace.
     */
    public SafeErrorDetails(String error, String[] reason, StackTraceElement[] stackTrace, int maxStackTraceDepth) {
        this.error = error;
        this.reasons = reason;
        if (stackTrace != null) {
            if (maxStackTraceDepth < 0) {
                throw new IllegalArgumentException("'maxStackTraceDepth' cannot be negative");
            }
            if (maxStackTraceDepth > 0) {
                this.stackTrace = Arrays.copyOf(stackTrace, Math.min(stackTrace.length, maxStackTraceDepth));
            }
        }
    }

    private static String[] getReasonsFromThrowable(Throwable thr) {
        List<String> strings = new LinkedList<>();
        Throwable currentReason = thr;
        while (currentReason != null) {
            strings.add(currentReason.toString());
            currentReason = currentReason.getCause();
        }
        String[] reasonMessages = strings.toArray(new String[]{});
        return reasonMessages;
    }

    /**
     * Gets the error.
     *
     * @return The error.
     */
    public String getError() {
        return error;
    }

    /**
     * Gets the message from the {@link java.lang.Throwable} that caused this
     * error.
     *
     * @return The reasons for this error.
     */
    public String[] getReasons() {
        return reasons;
    }

    /**
     * Gets the stack trace.
     *
     * @return The stack trace.
     */
    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    @Override
    public String toString() {
        return JsonUtils.write(this, true);
    }
}
