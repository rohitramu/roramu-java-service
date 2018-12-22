package roramu.service.status;

/**
 * Represents the status of a service.
 */
public class ServiceStatus {
    private final JvmStatus jvmStatus;
    private final Object status;

    public ServiceStatus(JvmStatus jvmStatus, Object extraInfo) {
        this.jvmStatus = jvmStatus;
        this.status = extraInfo;
    }

    public ServiceStatus(Object extraInfo) {
        this(JvmStatus.createStatus(), extraInfo);
    }

    public ServiceStatus() {
        this(null);
    }

    public JvmStatus getJvmStatus() {
        return this.jvmStatus;
    }

    public Object getStatus() {
        return this.status;
    }
}
