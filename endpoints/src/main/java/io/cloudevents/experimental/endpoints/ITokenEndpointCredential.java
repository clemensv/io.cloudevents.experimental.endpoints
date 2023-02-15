
package io.cloudevents.experimental.endpoints;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * This interface is used to configure a token-based endpoint credential.
 */
public interface ITokenEndpointCredential extends IEndpointCredential {
    /**
     * Asks for the token string to be used for authentication.
     * @param endpointUri The endpoint URI for which the token is requested.
     * @return A future that will be completed with the token string. The token
     * string should be "complete" in the sense that it includes any required
     * prefix, such as "Bearer " for OAuth2 tokens.
     */
    public CompletableFuture<String> getTokenAsync(URI endpointUri);
}
