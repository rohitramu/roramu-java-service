package roramu.service.status;

/**
 * Represents the extraInfo of a service.
 */
public class ServiceStatus {
    private final JvmStatus jvmStatus;
    private final Object extraInfo;

    public ServiceStatus(JvmStatus jvmStatus, Object extraInfo) {
        this.jvmStatus = jvmStatus;
        this.extraInfo = extraInfo;
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

    public Object getExtraInfo() {
        return this.extraInfo;
    }
}
