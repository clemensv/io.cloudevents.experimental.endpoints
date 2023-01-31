package io.cloudevents.experimental.endpoints;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.http.vertx.VertxMessageFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class HttpProducerEndpoint extends ProducerEndpoint {

	final Vertx _vertx = Vertx.vertx();
    final WebClient _webClient = WebClient.create(_vertx);
	private Logger _logger;
	private IEndpointCredential _credential;
	private List<URI> _endpoints;

	public HttpProducerEndpoint(Logger logger, IEndpointCredential credential, Map<String, String> options, List<URI> endpoints) {
		this._logger = logger;
		this._credential = credential;
        this._endpoints = endpoints;
	}

	@Override
	public void sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) throws Exception {
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
		}
	}

	@Override
	public void close() throws Exception {
		_webClient.close();
	}

}