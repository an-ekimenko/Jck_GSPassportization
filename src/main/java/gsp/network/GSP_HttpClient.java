package gsp.network;

import jakarta.inject.Inject;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gsp.config.GSPApiConfig;

public class GSP_HttpClient {
	
	private final CloseableHttpClient httpClient;
    private final GSPApiConfig config;
    private static final Logger logger = LogManager.getLogger();

    @Inject
    public GSP_HttpClient(CloseableHttpClient httpClient, GSPApiConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }
    
    /**
     * Method for assembling an authorization header based on configuration.
     */
    private String buildAuthHeader() {
        GSPApiConfig.AuthConfig auth = config.getAuth();
        
        if (auth == null || auth.getType() == null) {
            logger.warn("Authentication configuration is missing. Proceeding without auth headers.");
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
                logger.debug("Executing request without authentication headers [auth type: none]");
                return null;

            default:
                logger.warn("Unsupported authentication type detected: '{}'. No auth headers applied.", authType);
                return null;
        }
    }
    
    public String fetchRawJson() throws Exception {
    	
    	String urlString = config.getEndpoint();
        
        String authHeader = buildAuthHeader();
        
        // Initialize the structural HTTP GET request
        ClassicHttpRequest request = ClassicRequestBuilder.get(urlString)
                .setHeader("Accept", "application/json")
                .build();

        if (authHeader != null) {
            request.setHeader("Authorization", authHeader);
        }
        
        logger.info("Executing API request to: " + urlString);

        // Execute the request; transient failures are handled seamlessly inside the retry pipeline
        return httpClient.execute(request, response -> {
            int status = response.getCode();
            System.out.println("\nHTTP response code = " + status);
            
            if (status >= 200 && status < 300) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } else {
                throw new RuntimeException("The server returned an error code: " + status 
                        + ". Headers: " + response.getHeaders().toString());
            }
        });
    }

}
