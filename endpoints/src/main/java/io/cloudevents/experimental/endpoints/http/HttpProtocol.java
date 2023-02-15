
package io.cloudevents.experimental.endpoints.http;

/**
 * The HTTP protocol.
 */
public class HttpProtocol
{
    /**
     * The name of the HTTP protocol.
     */
    public static final String NAME = "http";

    /**
     * Registers the HTTP protocol.
     */
    public static void register()
    {
        HttpProducerEndpoint.register();
    }
}
