package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.jul.Log4jBridgeHandler;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;
import io.cloudevents.jackson.JsonFormat;

@Testcontainers
public class TestArtemisE2E {

    private static final Logger logger = LogManager.getLogger(TestArtemisE2E.class.getName());
    
    @BeforeAll
    public static void setup() {
        Log4jBridgeHandler.install(true, null, false);
    }

    @Container
    private static GenericContainer<?> environment = new GenericContainer<>(
            "quay.io/artemiscloud/activemq-artemis-broker")
            .withEnv("AMQ_USER", "admin")
            .withEnv("AMQ_PASSWORD", "admin")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now live.*", 1));

    @Test
    void testBinary() throws Exception {
        testServer(Encoding.BINARY);
    }

    @Test
    void testStructured() throws Exception {
        testServer(Encoding.STRUCTURED);
    }

    void testServer(Encoding encoding) throws Exception {
        AmqpProtocol.register();
        
        PlainEndpointCredential credential = new PlainEndpointCredential("admin", "admin");
        List<URI> endpoint = List.of(
                new URI(String.format("amqp://%s:%d/queue", environment.getHost(), environment.getFirstMappedPort())));
        Map<String, String> options = Map.of("node", "queue");
        var producer = ProducerEndpoint.create(credential, AmqpProtocol.NAME, options, endpoint);

        CompletableFuture<CloudEvent> future = new CompletableFuture<>();
        var consumer = ConsumerEndpoint.create(credential, AmqpProtocol.NAME, options, endpoint);
        consumer.subscribe((cloudEvent) -> {
            future.complete(cloudEvent);
        });
        consumer.startAsync().get();

        var event = new CloudEventBuilder()
                .withId("1234")
                .withSource(URI.create("/hi/here"))
                .withType("test")
                .withData("text/plain", "Hello World!".getBytes())
                .build();
        producer.sendAsync(event, encoding, new JsonFormat()).get();

        try {
            future.get(10000, java.util.concurrent.TimeUnit.MILLISECONDS);
            logger.info("Received event: " + future.get());
        }
        finally {
            consumer.stopAsync().get();
            consumer.close();
            producer.close();
        }
    }
}
