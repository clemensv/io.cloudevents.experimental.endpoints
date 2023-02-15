
package io.cloudevents.experimental.endpoints;

public class PlainEndpointCredential implements IEndpointCredential {
	private String clientId;
	private String clientSecret;

	/**
	 * Creates a new plain endpoint credential.
	 * 
	 * @param clientId     The client ID.
	 * @param clientSecret The client secret.
	 */
	public PlainEndpointCredential(String clientId, String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	/**
	 * The client ID.
	 */
	public String getClientId() {
		return clientId;
	}

	/**
	 * The client secret.
	 */
	public String getClientSecret() {
		return clientSecret;
	}
}