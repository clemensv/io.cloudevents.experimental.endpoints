package io.cloudevents.experimental.endpoints.mqtt;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.jul.Log4jBridgeHandler;
import org.apache.logging.log4j.LogManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
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
public class TestMosquittoE2E {

    private static final Logger logger = LogManager.getLogger(TestMosquittoE2E.class.getName());

    @BeforeAll
    public static void setup() {
        MqttProtocol.register();
        Log4jBridgeHandler.install(true, null, false);
    }

    @Container
    private static GenericContainer<?> environment = new GenericContainer<>(
            "eclipse-mosquitto:2.0.15")
            .withExposedPorts(1883)
            .withClasspathResourceMapping("mosquitto-config", "/mosquitto/config", BindMode.READ_ONLY)
            .withFileSystemBind(System.getProperty("java.io.tmpdir"), "/mosquitto/log")
            .withLogConsumer(outputFrame -> {
                logger.info(outputFrame.getUtf8String());
            })
            .waitingFor(Wait.forListeningPort());

    @Test
    public void testV3Structured() throws Exception {
        testServer("MQTT/3.1.1", Encoding.STRUCTURED);
    }

    @Test
    public void testV5Binary() throws Exception {
        testServer("MQTT/5.0", Encoding.BINARY);
    }

    @Test
    public void testV5Structured() throws Exception {
        testServer("MQTT/5.0", Encoding.STRUCTURED);
    }

    @Test
    public void testDefault() throws Exception {
        testServer("MQTT", Encoding.STRUCTURED);
    }

    void testServer(String protocolVersion, Encoding encoding) throws Exception {

        PlainEndpointCredential credential = new PlainEndpointCredential("test", "password");
        List<URI> endpoint = List.of(
                new URI(String.format("mqtt://%s:%d", environment.getHost(), environment.getFirstMappedPort())));
        Map<String, String> options = Map.of("topic", "t1");
        var producer = ProducerEndpoint.create(credential, protocolVersion, options, endpoint);
        CompletableFuture<CloudEvent> pendingReceive = new CompletableFuture<>();
        var consumer = ConsumerEndpoint.create(credential, protocolVersion, options, endpoint);
        consumer.subscribe((cloudEvent) -> {
            pendingReceive.complete(cloudEvent);
        });
        
        logger.info("Consumer start");
        consumer.startAsync().thenCompose((v) -> {
            logger.info("Sending");
            var event = new CloudEventBuilder()
                    .withId("1234")
                    .withSource(URI.create("/hi/here"))
                    .withType("test")
                    .withData("text/plain", "Hello World!".getBytes())
                    .build();
            return producer.sendAsync(event, encoding, new JsonFormat());
        }).thenComposeAsync((v) -> {
            logger.info("Waiting");
            return pendingReceive;
        }).thenComposeAsync((v) -> {
            logger.info("Stopping");
            return consumer.stopAsync();
        }, MqttThreadPool.getExecutor()).thenAcceptAsync((v) -> {
            logger.info("Closing");
            try {
                consumer.close();
                producer.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }            
        }, MqttThreadPool.getExecutor()).join();
        logger.info("Consumer done");
    }
}
