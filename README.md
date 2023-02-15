# CloudEvents Discovery Endpoints for Java

** Experimental. **

This repository contains a Java implementation of the CloudEvents Discovery
endpoints abstractions for producers, consumers, and subscribers and is used by the `ceregistry` tool in generated code.

The HTTP transport is supported in the endpoints package, the AMQP and MQTT
transports the respective extension packages.

## Overview

In the CloudEvents Discovery specification, a producer endpoint is where
CloudEvents are published. A consumer endpoint is where CloudEvents are
retrieved. There are two perspectives on either end of the communication channel
for a producer or consumer endpoint.

The producer connects to the endpoint and transfers events to the endpoint. The
endpoint provider accepts the events and makes them available for consumption.
Equivalently, the consumer connects to the endpoint and retrieves events from
the endpoint. The endpoint provider delivers the events to the consumer.
Endpoint providers are usually message brokers or web servers.

This repository contains the client abstractions for producers and consumers,
not for the endpoint providers.

The common message model for all producer and consumer endpoint implementations
is a CloudEvent. The endpoint implementation is responsible for converting the
CloudEvent from/to a transport specific message.

Any endpoint implementation may have protocol-specific options and
protocol-specific delivery subscriber hooks that allow delivery "raw" messages
to subscribers.

## Producer Endpoints

A producer endpoint implementation typically attaches to a network or messaging
infrastructure and sends events.

The factory method `ProducerEndpoint.create()` is used to create a producer
endpoint instance.

The `ProducerEndpoint.sendAsync(CloudEvent, EventFormat, Encoding)` method is
used to send a CloudEvent using a specific event format and encoding. The send
operation is asynchronous and returns a future that will be completed when the
send operation is complete.

Concrete producer endpoint implementations can be registered with the factory at
runtime by calling the
`ProducerEndpoint.addProducerEndpointFactoryHook(ProducerEndpointFactoryHandler)`.
The factory will then use the registered hooks to create an instance of the
appropriate producer endpoint implementation. Examples of such hooks are the
MQTT and AMQP implementations explained below.

## Consumer Endpoints

A consumer endpoint implementation typically attaches to a network or messaging
infrastructure and receives events from producer endpoints.

The factory method `ConsumerEndpoint.create` is used to create a consumer
endpoint instance.

Consumer endpoints host a receive loop that receives events from producer
endpoints and delivers them to subscribers. The receive loop is typically
implemented as a separate thread. The receive loop is started by calling the
`ConsumerEndpoint.startAsync()` method and stopped by calling the
`ConsumerEndpoint.stopAsync()` method.

The `ConsumerEndpoint.subscribe(DispatchCloudEventAsync)` and
`ConsumerEndpoint.unsubscribe(DispatchCloudEventAsync)` methods are used to
register and unregister subscribers. The receive loop will deliver events to all
registered subscribers.

Concrete consumer endpoint implementations can be registered with the factory at
runtime by calling the
`ConsumerEndpoint.addConsumerEndpointFactoryHook(ConsumerEndpointFactoryHandler)`.
The factory will then use the registered hooks to create an instance of the
appropriate consumer endpoint implementation. Examples of such hooks are the
MQTT and AMQP implementatiosn explained below.

## HTTP 

The HTTP provider use Eclipse Vert.x and the HTTP extensions of the CloudEvents
SDK for Java to implement the HTTP protocol support.

The HTTP protocol support must be registered with the factory by calling the
static `HttpProtocol.register()` method once during application startup.

The package `io.cloudevents.experimental.endpoints.http` contains the HTTP
protocol support and is included with the base class library in the same JAR.
Declare the dependency as:
    
```xml
<dependency>
    <groupId>io.cloudevents.experimental.endpoints</groupId>
    <artifactId>endpoints</artifactId>
    <version>latest</version>
</dependency>
```

The name of the HTTP protocol is "http" and there are no protocol-specific
options.

### HTTP Producer endpoint

The HTTP producer endpoint implementation supports sending CloudEvents to
producer endpoints using the HTTP POST method. 

Example for CloudEvents:

```java
// Create a producer endpoint that connects to the HTTP server at
// "http://localhost:8080" and sends events to the "/myapp" path.

var endpoint = ProducerEndpoint.create(
                     "http",
                     null,
                     List.of(URI.create("http://localhost:8080/myapp")));

// Send a CloudEvent message to the consumer endpoint.
var event = CloudEventBuilder.v1()
                 .withId(UUID.randomUUID().toString())
                 .withSource(URI.create("/myapp"))
                 .withType("myevent")
                 .withData("text/plain", "Hello, world!".getBytes())
                 .build();

endpoint.sendAsync(event, EventFormatProvider.getInstance().resolveFormat("json"), Encoding.binary()).join();
```

## AMQP 1.0

The AMQP providers use the Apache Qpid Proton-J library and the AMQP extensions
of the CloudEvents SDK for Java to implement the AMQP 1.0 protocol support.

The AMQP protocol support must be registered with the factory by calling the
static `AmqpProtocol.register()` method once during application startup.

The package `io.cloudevents.experimental.endpoints.amqp` contains the AMQP 1.0 protocol support. Declare the dependency as:
    
```xml
<dependency>
    <groupId>io.cloudevents.experimental.endpoints</groupId>
    <artifactId>endpoints-amqp</artifactId>
    <version>latest</version>
</dependency>
```

The name of the AMQP protocol is "amqp".

The "options" parameter to the factory method is optionally used to specify the
AMQP "node" (queue, topic, subscription, etc.) to which the consumer endpoint
will attach. The node name can also be specified as a path in the endpoint URI.
If the node name is specified in both the options and the URI, the value in the
options takes precedence.

### AMQP Producer endpoint

Example for CloudEvents:

```java
// Create a producer endpoint that connects to the AMQP broker at
// "amqp://localhost:5672" and attaches to the "myqueue" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = ProducerEndpoint.create(
                     credential, "amqp",
                     Map.of("node", "myqueue"),
                     List.of(URI.create("amqp://localhost:5672")));

// Send a CloudEvent message to the consumer endpoint.
var event = CloudEventBuilder.v1()
                 .withId(UUID.randomUUID().toString())
                 .withSource(URI.create("/myapp"))
                 .withType("myevent")
                 .withData("text/plain", "Hello, world!".getBytes())
                 .build();

endpoint.sendAsync(event, Encoding.BINARY, EventFormatProvider.getInstance().resolveFormat("json")).join();
```

In addition to the CloudEvents message model, the AMQP producer endpoint
implementation also supports sending "raw" AMQP messages to the consumer
endpoint. `AmqpProducerEndpoint.sendAsync(Message)` method can be used to send a
raw AMQP message to the consumer endpoint. The message does not need to be a
CloudEvent message.

Example for AMQP:

```java
// Create a producer endpoint that connects to the AMQP broker at
// "amqp://localhost:5672" and attaches to the "myqueue" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = (AmqpProducerEndpoint)ProducerEndpoint.create(
                     credential, "amqp",
                     Map.of("node", "myqueue"),
                     List.of(URI.create("amqp://localhost:5672")));
 *
// Send a raw AMQP message to the consumer endpoint.
var message = Proton.message();
message.setAddress("myqueue");
message.setBody(new AmqpValue("Hello, world!"));
endpoint.sendAsync(message).join();
}

```

### AMQP Consumer endpoint

If the message is CloudEvents compliant, either in binary or structured mode as
indicated by the content-type, it will be converted and delivered to the
CloudEvents subscribers.

If any of the CloudEvents subscribers or the AMQP subscribers throw an
exception, the message will be automatically released. If all CloudEvents
subscribers return normally, the message will be automatically accepted.

CloudEvents Example:

``` java
// Create a consumer endpoint that connects to the AMQP broker at
// "amqp://localhost:5672" and attaches to the "myqueue" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = ConsumerEndpoint.create(
                     credential, "amqp", 
                     Map.of("node", "myqueue"), 
                     List.of(URI.create("amqp://localhost:5672")));
endpoint.subscribe((cloudEvent) -> {
   // Handle the event
});
endpoint.startAsync();
``` 

The AMQP consumer endpoint also supports AMQP messages directly. AMQP messages
are first delivered to subscribers who registered by calling the
`AmqpConsumerEndpoint.subscribe(DispatchMessageAsync)` method. The subscriber
provides a callback interface implementation. The
`DispatchMessageAsync.onMessage(Message, MessageContext)` callback method is
invoked with the AMQP message and the `MessageContext` object that allows the
subscriber to control the message disposition.

If subscribers exist at this level, the message will only be made available to
the CloudEvents subscribers if none of the subscribers calls any of the
disposition methods on the context. Only one disposition method may be called
once per message. An AMQP subscriber must call one of the disposition methods if
it handled the message. There is no auto-accept behavior.

If the message has not been disposed by an AMQP subscriber and if it is not
CloudEvents compliant, it will be automatically rejected because it cannot be
dispatched by this endpoint.

AMQP Example:

``` java
// Create a consumer endpoint that connects to the AMQP broker at
// "amqp://localhost:5672" and attaches to the "myqueue" node.
// Cast the endpoint to the AMQP-specific subclass to access the
// AMQP-specific methods.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = (AmqpConsumerEndpoint)ConsumerEndpoint.create(
                     credential, "amqp", 
                     Map.of("node", "myqueue"), 
                     List.of(URI.create("amqp://localhost:5672")));
endpoint.subscribe((message, context) -> {
    // Handle the message
    context.accept();
});
endpoint.startAsync();
```

## MQTT 5.0 and 3.1.1

The MQTT protocol support must be registered with the factory by calling the
static `MqttProtocol.register()` method once during application startup.

The package `io.cloudevents.experimental.endpoints.mqtt` contains the MQTT
protocol support. Declare the dependency as:
    
```xml
<dependency>
    <groupId>io.cloudevents.experimental.endpoints</groupId>
    <artifactId>endpoints-mqtt</artifactId>
    <version>latest</version>
</dependency>
```

The "options" parameter to the factory method is optionally used to specify
MQTT-specific options. The following options are supported:

- `"topic"` - the MQTT topic to subscribe to. If not specified, the topic is taken
  from the "path" portion of the endpoint URI.
- `"qos"` - the MQTT quality of service level to use. If not specified, the
  default value of 0 is used.

The "protocol" parameter to the factory method is used to specify the MQTT
protocol version to use. The following values are supported:

- `"mqtt"` - MQTT 5.0
- `"mqtt/3.1.1"` - MQTT 3.1.1
- `"mqtt/5.0"` - MQTT 5.0

The "endpoints" parameter to the factory method is used to specify the MQTT
broker endpoints to connect to. Only one endpoint is permitted. The URI scheme
must be "mqtt" or "mqtts". If the scheme is "mqtts", the endpoint is configured
to use TLS.

### MQTT Producer endpoint

Example for CloudEvents:

``` java
// Create a producer endpoint that connects to the MQTT broker at
// "mqtt://localhost:1883" and attaches to the "mytopic" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = ConsumerEndpoint.create(
                     credential, "mqtt", 
                     Map.of("topic", "mytopic"), 
                     List.of(URI.create("mqtt://localhost:1883")));

// Send a CloudEvent message to the consumer endpoint.
var event = CloudEventBuilder.v1()
                 .withId(UUID.randomUUID().toString())
                 .withSource(URI.create("/myapp"))
                 .withType("myevent")
                 .withData("text/plain", "Hello, world!".getBytes())
                 .build();

endpoint.sendAsync(event, Encoding.BINARY, EventFormatProvider.getInstance().resolveFormat("json")).join();
```

In addition to the CloudEvents message model, the MQTT producer endpoint
supports MQTT messages (PUBLISH packets) directly.

As with the underlying Paho MQTT library, MQTT 5.0 and MQTT 3.1.1 are handled 
separately.

Example for MQTT 5.0 messages:

``` java
// Create a producer endpoint that connects to the MQTT broker at
// "mqtt://localhost:1883" and attaches to the "mytopic" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = ConsumerEndpoint.create(
                     credential, "mqtt", 
                     Map.of("topic", "mytopic"), 
                     List.of(URI.create("mqtt://localhost:1883")));

// Send an MQTT message to the consumer endpoint.
var message = new MqttMessage("Hello, world!".getBytes());
message.setQos(0);
message.setRetained(false);

endpoint.sendAsync(message).join();
```

### MQTT Consumer endpoint

If the message is CloudEvents compliant, either in binary or structured mode as
indicated by the content-type, it will be converted and delivered to the
CloudEvents subscribers.

If the QoS level is 1 or 2, the message will be acknowledged by the endpoint
after it has been delivered to all subscribers and no exception has been thrown.

CloudEvents Example:

```java
// Create a consumer endpoint that connects to the MQTT broker at
// "mqtt://localhost:1883" and attaches to the "mytopic" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = ConsumerEndpoint.create(
                     credential, "mqtt",
                     Map.of("topic", "mytopic"),
                     List.of(URI.create("mqtt://localhost:1883")));
endpoint.subscribe((cloudEvent) -> {
   // Handle the event
});
endpoint.startAsync();
```

In addition to the CloudEvents message model, the MQTT consumer endpoint also
supports MQTT messages (PUBLISH packets) directly.

As with the underlying Paho MQTT library, MQTT 5.0 and MQTT 3.1.1 are handled
separately.

For MQTT 5.0, MQTT messages are first delivered to subscribers who registered by
calling the `MqttConsumerEndpoint.subscribe(DispatchMqttV5MessageAsync)` method. The
subscriber provides a callback interface implementation. The `DispatchMqttV5MessageAsync.onMessage(org.eclipse.paho.mqttv5.common.MqttMessage)
` callback method is invoked with the MQTT message.

For MQTT 3.1.1, MQTT messages are first delivered to subscribers who registered
by calling the `MqttConsumerEndpoint.subscribe(DispatchMqttV3MessageAsync)` method. The
subscriber provides a callback interface implementation. The `DispatchMqttV3MessageAsync.onMessage(MqttMessage)` callback method is invoked
with the MQTT message.

MQTT 5.0 Example:

```java
// Create a consumer endpoint that connects to the MQTT broker at
// "mqtt://localhost:1883" and attaches to the "mytopic" node.

var credential = new PlainEndpointCredential("username", "password");
var endpoint = ConsumerEndpoint.create(
                     credential, "mqtt",
                     Map.of("topic", "mytopic"),
                     List.of(URI.create("mqtt://localhost:1883")));

// Register a subscriber for MQTT messages
endpoint.subscribe(new DispatchMqttV5MessageAsync() {
   public void onMessage(org.eclipse.paho.mqttv5.common.MqttMessage message) {
     // Handle the message
  }
});
endpoint.startAsync();
```
