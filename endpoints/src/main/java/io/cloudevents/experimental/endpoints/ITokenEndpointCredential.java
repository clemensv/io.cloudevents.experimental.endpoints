
package io.cloudevents.experimental.endpoints;

import java.util.concurrent.CompletableFuture;

/**
 * A credential for a token endpoint.
 */
public interface ITokenEndpointCredential extends IEndpointCredential {
    /**
     * The token endpoint.
     */
    public CompletableFuture<String> getTokenAsync();
}
