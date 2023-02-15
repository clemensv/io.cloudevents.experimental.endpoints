package io.cloudevents.experimental.endpoints.http;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.IHeaderEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;
import io.cloudevents.http.vertx.VertxMessageFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * This class is a producer endpoint implementation that uses HTTP as the
 * transport.
 * 
 * The implementation uses the Vert.x Web Client and the CloudEvents SDK Vert.x 
 * message factory.
 * 
 * The implementation supports both structured and binary encoding.
 */
public class HttpProducerEndpoint extends ProducerEndpoint {

	final Vertx _vertx = Vertx.vertx();
    final WebClient _webClient = WebClient.create(_vertx);
	
	private IEndpointCredential _credential;
	private List<URI> _endpoints;

    public HttpProducerEndpoint(IEndpointCredential credential, Map<String, String> options, List<URI> endpoints) {
        this._credential = credential;
        this._endpoints = endpoints;
    }
    
    public static void register() {

        addProducerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.toLowerCase().startsWith("http")) {
                return new HttpProducerEndpoint(credential, options, endpoints);
            }
            return null;
        });
    }

	@Override
	public CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) {
        for (URI endpoint : _endpoints) {
            Future<HttpResponse<Buffer>> responseFuture;
            var request = _webClient.postAbs(endpoint.toString());
            if (this._credential instanceof IHeaderEndpointCredential) {
                for (Map.Entry<String, String> header : ((IHeaderEndpointCredential) this._credential).getHeaders()
                        .entrySet()) {
                    request.putHeader(header.getKey(), header.getValue());
                }
            }
            var writer = VertxMessageFactory.createWriter(request);

            if (contentMode == Encoding.BINARY) {
                responseFuture = writer.writeBinary(cloudEvent); // Use binary mode.
            } else {
                responseFuture = writer.writeStructured(cloudEvent, JsonFormat.CONTENT_TYPE);
            }

            return responseFuture.toCompletionStage().toCompletableFuture().thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return null;
                } else {
                    throw new RuntimeException(
                            "Failed to send event to " + endpoint + ": " + response.statusCode() + " "
                                    + response.statusMessage());
                }
            });
        }
        return CompletableFuture.completedFuture(null);
	}

	@Override
    public void close() {
        _webClient.close();
        _vertx.close();
	}

}




