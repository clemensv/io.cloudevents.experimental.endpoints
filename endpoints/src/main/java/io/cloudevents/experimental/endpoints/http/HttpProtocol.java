
package io.cloudevents.experimental.endpoints.http;

public class HttpProtocol
{
    public static final String NAME = "http";

    /**
     * Registers the HTTP protocol.
     */
    public static void register()
    {
        HttpProducerEndpoint.register();
    }
}
