package roramu.service.status;

import com.kstruct.gethostname4j.Hostname;
import com.sun.management.OperatingSystemMXBean;
import roramu.util.net.NetworkUtils;
import roramu.util.time.TimeUtils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Paths;

/**
 * Represents the status of the Java Virtual Machine.
 */
public class JvmStatus {
    private static final OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // General
    /**
     * The time that the status was last updated (i.e. the time that this object
     * was created). The time is retrieved using the
     * {@link TimeUtils#getCurrentMillis() } method.
     */
    private Long lastUpdated;

    // OS
    /**
     * The host Operating System's name.
     */
    private String osName;
    /**
     * The host Operating System's architecture.
     */
    private String osArchitecture;
    /**
     * The host Operating System's version.
     */
    private String osVersion;

    // CPU
    /**
     * The CPU load as a fraction of available CPU power. Multiply by 100 to get
     * this as a percentage.
     */
    private Double cpuLoad;
    /**
     * The total number of cores available to the service.
     */
    private Integer cpuCores;

    // Memory
    /**
     * The total amount of memory available to the service in bytes.
     */
    private Long runtimeMemoryTotalBytes;
    /**
     * The amount of memory unused by the service in bytes.
     */
    private Long runtimeMemoryFreeBytes;
    /**
     * The total amount of memory installed on the server.
     */
    private Long physicalMemoryTotalBytes;
    /**
     * The amount of memory free on the server.
     */
    private Long physicalMemoryFreeBytes;

    // Storage
    /**
     * The total amount of storage available to the service in kilobytes.
     */
    private Long localStorageTotalBytes;
    /**
     * The amount of storage used by the service in kilobytes.
     */
    private Long localStorageUsedBytes;

    // Network
    /**
     * The name of the network adapter being used by the service.
     */
    private String networkAdapterName;
    /**
     * The local (LAN) IP address of the host server.
     */
    private String ipAddressLocal;
    /**
     * The external IP address of the host server.
     */
    private String ipAddressExternal;
    /**
     * The hostname of the host server.
     */
    private String hostname;
    /**
     * The MAC address of the network adapter that the service is using.
     */
    private byte[] macAddress;

    public static JvmStatus createStatus() {
        JvmStatus status = new JvmStatus();

        // Safely get timestamp
        try {
            status.lastUpdated = TimeUtils.getCurrentMillis();
        } catch (Exception ex) {
            // TODO: log
            ex.printStackTrace();
        }

        // Safely get OS info
        status.osName = null;
        status.osArchitecture = null;
        status.osVersion = null;
        try {
            status.osName = osBean.getName();
            status.osArchitecture = osBean.getArch();
            status.osVersion = osBean.getVersion();
        } catch (Exception ex) {
            // TODO: log
            ex.printStackTrace();
        }

        // Safely get CPU info
        try {
            status.cpuCores = osBean.getAvailableProcessors();
            status.cpuLoad = osBean.getSystemCpuLoad();
        } catch (Exception ex) {
            // TODO: log
            ex.printStackTrace();
        }

        // Safely get memory info
        try {
            status.physicalMemoryTotalBytes = osBean.getTotalPhysicalMemorySize();
            status.physicalMemoryFreeBytes = osBean.getFreePhysicalMemorySize();
            status.runtimeMemoryTotalBytes = memoryBean.getNonHeapMemoryUsage().getCommitted() + memoryBean.getHeapMemoryUsage().getCommitted();
            status.runtimeMemoryFreeBytes = status.runtimeMemoryTotalBytes -
                (memoryBean.getNonHeapMemoryUsage().getUsed() + memoryBean.getHeapMemoryUsage().getUsed());
        } catch (Exception ex) {
            // TODO: log
            ex.printStackTrace();
        }

        // Safely get storage info
        try {
            File rootDriveDir = Paths.get("").toAbsolutePath().normalize().getRoot().toFile();
            status.localStorageTotalBytes = rootDriveDir.getTotalSpace();
            status.localStorageUsedBytes = status.localStorageTotalBytes - rootDriveDir.getUsableSpace();
        } catch (Exception ex) {
            // TODO: log
            ex.printStackTrace();
        }

        // Safely get network info
        try {
            // Localhost
            status.hostname = Hostname.getHostname(); //localhost.getCanonicalHostName();
            InetAddress localhost = InetAddress.getLocalHost();
            status.ipAddressLocal = InetAddress.getByAddress(status.hostname, localhost.getAddress()).getHostAddress();
            // External
            InetAddress external = NetworkUtils.tryGetExternalAddress();
            status.ipAddressExternal = external == null ? null : external.getHostAddress();
            // Network adapter
            NetworkInterface networkAdapter = NetworkInterface.getByInetAddress(localhost);
            status.networkAdapterName = networkAdapter.getDisplayName();
            status.macAddress = networkAdapter.getHardwareAddress();
        } catch (Exception ex) {
            // TODO: log
            ex.printStackTrace();
        }

        return status;
    }

    // =============
    // == GETTERS ==
    // =============

    /**
     * The time that the status was last updated (i.e. the time that this object
     * was created). The time is retrieved using the {@link
     * TimeUtils#getCurrentMillis() } method.
     *
     * @return The time that the status was last updated.
     */
    public long getLastUpdated() {
        return this.lastUpdated;
    }

    /**
     * The host Operating System's name.
     *
     * @return The host Operating System's name.
     */
    public String getOsName() {
        return this.osName;
    }

    /**
     * The host Operating System's architecture.
     *
     * @return The host Operating System's architecture.
     */
    public String getOsArchitecture() {
        return this.osArchitecture;
    }

    /**
     * The host Operating System's version.
     *
     * @return The host Operating System's version.
     */
    public String getOsVersion() {
        return this.osVersion;
    }

    /**
     * The CPU load as a fraction of available CPU power. Multiply by 100 to
     * get this as a percentage.
     *
     * @return The CPU load as a fraction of available CPU power. Multiply by
     * 100 to get this as a percentage.
     */
    public double getCpuLoad() {
        return this.cpuLoad;
    }

    /**
     * The total number of cores available to the service.
     *
     * @return The total number of cores available to the service.
     */
    public int getCpuCores() {
        return this.cpuCores;
    }

    /**
     * The total amount of memory available to the service in bytes.
     *
     * @return The total amount of memory available to the service in bytes.
     */
    public long getRuntimeMemoryTotalBytes() {
        return this.runtimeMemoryTotalBytes;
    }

    /**
     * The amount of memory unused by the service in bytes.
     *
     * @return The amount of memory unused by the service in bytes.
     */
    public long getRuntimeMemoryFreeBytes() {
        return this.runtimeMemoryFreeBytes;
    }

    /**
     * The total amount of memory installed on the server.
     *
     * @return The total amount of memory installed on the server.
     */
    public long getPhysicalMemoryTotalBytes() {
        return this.physicalMemoryTotalBytes;
    }

    /**
     * The amount of memory free by the server.
     *
     * @return The amount of memory free by the server.
     */
    public long getPhysicalMemoryFreeBytes() {
        return this.physicalMemoryFreeBytes;
    }

    /**
     * The total amount of storage available to the service in bytes.
     *
     * @return The total amount of storage available to the service in bytes.
     */
    public long getLocalStorageTotalBytes() {
        return this.localStorageTotalBytes;
    }

    /**
     * The amount of storage used by the service in bytes.
     *
     * @return The amount of storage used by the service in bytes.
     */
    public long getLocalStorageUsedBytes() {
        return this.localStorageUsedBytes;
    }

    /**
     * The name of the network adapter being used by the service.
     *
     * @return The name of the network adapter being used by the service.
     */
    public String getNetworkAdapterName() {
        return this.networkAdapterName;
    }

    /**
     * The local (LAN) IP address of the host server.
     *
     * @return The local (LAN) IP address of the host server.
     */
    public String getIpAddressLocal() {
        return this.ipAddressLocal;
    }

    /**
     * The external IP address of the host server.
     *
     * @return The external IP address of the host server.
     */
    public String getIpAddressExternal() {
        return this.ipAddressExternal;
    }

    /**
     * The hostname of the host server.
     *
     * @return The hostname of the host server.
     */
    public String getHostname() {
        return this.hostname;
    }

    /**
     * The MAC address of the network adapter that the service is using.
     *
     * @return The MAC address of the network adapter that the service is using.
     */
    public byte[] getMacAddress() {
        return this.macAddress;
    }
}
