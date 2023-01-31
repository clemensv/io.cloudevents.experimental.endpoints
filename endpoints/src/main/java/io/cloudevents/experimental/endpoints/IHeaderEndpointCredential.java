package io.cloudevents.experimental.endpoints;

import java.util.Map;

/**
 * Provides an interface for endpoint credentials.
 */
public interface IHeaderEndpointCredential extends IEndpointCredential {
    /**
     * The headers.
     */
    public Map<String, String> getHeaders();
}
