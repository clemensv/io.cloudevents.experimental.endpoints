
package io.cloudevents.experimental.endpoints.test.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;
import org.junit.jupiter.api.Test;

public class PlainEndpointCredentialTest {
	@Test
	public void testConstructor() {
		PlainEndpointCredential plainEndpointCredential = new PlainEndpointCredential("clientId", "clientSecret");
		assertEquals("clientId", plainEndpointCredential.getClientId());
		assertEquals("clientSecret", plainEndpointCredential.getClientSecret());
	}
}