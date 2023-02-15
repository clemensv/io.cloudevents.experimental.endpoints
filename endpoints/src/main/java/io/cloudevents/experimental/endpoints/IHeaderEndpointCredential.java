package io.cloudevents.experimental.endpoints;

import java.util.Map;

/**
 * This interface is used to configure a header-based endpoint credential.
 * Such credentials may be used, for instance, by an HTTP implementation 
 * to authenticate with the endpoint by passing the given headers.
 */
public interface IHeaderEndpointCredential extends IEndpointCredential {
    /**
     * The header map.
     * @return The header map.
     */
    public Map<String, String> getHeaders();
}
