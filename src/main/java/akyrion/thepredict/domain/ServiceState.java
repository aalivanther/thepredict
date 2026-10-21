package akyrion.thepredict.domain;

/** State of the remote decision service. */
public enum ServiceState {
    /** No credentials or model are configured. */
    UNCONFIGURED,
    /** The configuration is invalid. */
    INVALID_CONFIGURATION,
    /** The service is being checked. */
    CHECKING,
    /** The service accepts requests. */
    READY,
    /** Requests are paused after failures or a retry-after. */
    PAUSED,
    /** The credentials were rejected. */
    INVALID_CREDENTIALS,
    /** The configured model does not exist. */
    INVALID_MODEL
}
