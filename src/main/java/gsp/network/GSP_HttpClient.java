package gsp.network;
import com.google.inject.Inject;
import gsp.config.GSPApiConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public class GSP_HttpClient {
	
	private final CloseableHttpClient httpClient;
	private final GSPApiConfig config;
	
	private static final Logger logger = LogManager.getLogger(GSP_HttpClient.class);

	@Inject
	public GSP_HttpClient(CloseableHttpClient httpClient, GSPApiConfig config) {
		this.httpClient = httpClient;
		this.config = config;
	}
	
	/**
	• 
	Dynamically generates the appropriate HTTP Authorization header based on configuration profiles.
	• 
	@return the encoded authentication token string, or null if no credentials are required
	 */
	private String buildAuthHeader() {
		GSPApiConfig.AuthConfig auth = config.getAuth();
		if (auth == null || auth.getType() == null) {
			logger.warn("Authentication configuration is missing. API requests will be unauthenticated.");
			return null;
		}
		String authType = auth.getType().toLowerCase();
		switch (authType) {
		case "basic":
			String username = auth.getUsername();
			String password = auth.getPassword();
			String authString = username + ":" + password;
			String encodedAuth = Base64.getEncoder()
					.encodeToString(authString.getBytes(StandardCharsets.UTF_8));
			return "Basic " + encodedAuth;
			
		case "token":
			return "Bearer " + auth.getToken();
			
		case "none":
			logger.debug("Executing requests without authentication headers (auth type: none)");
			return null;
			
		default:
			logger.warn("Unsupported authentication type detected: '{}'. No auth headers applied.", authType);
			return null;
		}
	}
	
	/**
	• 
	Executes an HTTP GET request to fetch the raw JSON payload from the configured API endpoint.
	• 
	@return the raw JSON response string
	• 
	@throws Exception if an error occurs during execution or validation
	 */
	public String fetchRawJson() throws Exception {
		
		String urlString = config.getEndpoint();
		
		// Abort early if the endpoint URI is missing or malformed
		if (urlString == null || urlString.trim().isEmpty()) {
			throw new IllegalStateException("API endpoint URI is not configured in the properties file.");
		}
		String authHeader = buildAuthHeader();
		ClassicHttpRequest request = ClassicRequestBuilder.get(urlString)
				.setHeader("Accept", "application/json")
				.build();
		if (authHeader != null) {
			request.setHeader("Authorization", authHeader);
		}
		logger.info("Executing API request to: {}", urlString);
		
		// Transient network failures are handled seamlessly inside the pre-configured retry pipeline
		return httpClient.execute(request, response -> {
			int status = response.getCode();
			logger.info("HTTP response status code received: {}", status);

			if (status >= 200 && status < 300) {
				if (response.getEntity() == null) {
					throw new RuntimeException("The server returned a successful status code " 
							+ status + " but an empty body.");
				}
				/*
				 * The response handler guarantees that the content stream is fully consumed
				 * and the underlying socket connection is released back to the connection pool,
				 * preventing potential connection leaks.
				 */
				return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			} else {
				throw new RuntimeException("The server returned an error status code: " + status 
						+ ". Reason: " + response.getReasonPhrase());
			}
		});
	}
}