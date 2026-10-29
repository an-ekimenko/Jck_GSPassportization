package gsp.network;

import com.google.inject.Inject;
import gsp.config.GSPApiConfig;
import my.CertUpdater;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Client wrapper responsible for interacting with the GSP API over HTTP.
 */
public class GSP_HttpClient {
    
    private final GSPApiConfig config;
    private final HttpClientBuilder httpClientBuilder;
    private CloseableHttpClient httpClient;
    
    private static final Logger logger = LogManager.getLogger(GSP_HttpClient.class);

    /**
     * Constructs a new GSP_HttpClient with injected dependencies.
     * 
     * @param config the configuration profile containing credentials and endpoints
     * @param httpClientBuilder the builder instance used for deferred client initialization
     */
    @Inject
    public GSP_HttpClient(GSPApiConfig config, HttpClientBuilder httpClientBuilder) {
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;
    }
    
    /**
     * Dynamically generates the HTTP Authorization header based on configuration profiles.
     *  
     * @return the encoded authentication token string, or null if no credentials are required
     * @throws IllegalStateException if required credentials for the active auth type are missing
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
                
                if (username == null || password == null) {
                    throw new IllegalStateException("Basic auth credentials (username/password) are missing in configuration.");
                }
                
                String authString = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
                return "Basic " + encodedAuth;
                
            case "token":
                String token = auth.getToken();
                if (token == null || token.trim().isEmpty()) {
                    throw new IllegalStateException("Bearer token is missing in configuration.");
                }
                return "Bearer " + token;
                
            case "none":
                logger.debug("Executing requests without authentication headers (auth type: none)");
                return null;
                
            default:
                logger.warn("Unsupported authentication type detected: '{}'. No auth headers applied.", authType);
                return null;
        }
    }
    
    /**
     * Executes an HTTP GET request to fetch the raw JSON payload from the configured API endpoint.
     * 
     * @return the raw JSON response string
     * @throws Exception if an error occurs during execution, connection, or response validation
     */
    public String fetchRawJson() throws Exception {
        String urlString = config.getEndpoint();
        
        // Abort early if the endpoint URI is missing or empty
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalStateException("API endpoint URI is not configured in the properties file.");
        }

        // Lazy initialization block to ensure certificates are loaded before the client is built
        synchronized (this) {
            if (this.httpClient == null) {
                // Run the certificate update routine once before the first request
                triggerCertUpdate(urlString);

                // Fetch the updated SSLContext from the KeyStoreManager
                SSLContext sslContext = my.KeyStoreManager.getSSLContext();
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
                
                PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build();

                // Initialize the CloseableHttpClient with the active security context
                this.httpClient = httpClientBuilder
                        .setConnectionManager(connManager)
                        .build();
                
                logger.info("HTTP infrastructure successfully initialized with active TLS certificates.");
            }
        }

        String authHeader = buildAuthHeader();
        ClassicHttpRequest request = ClassicRequestBuilder.get(urlString)
                .setHeader("Accept", "application/json")
                .build();

        if (authHeader != null) {
            request.setHeader("Authorization", authHeader);
        }

        logger.info("Executing API request to: {}", urlString);
        
        return httpClient.execute(request, response -> {
            int status = response.getCode();
            logger.info("HTTP response status code received: {}", status);

            if (status >= 200 && status < 300) {
                if (response.getEntity() == null) {
                    throw new RuntimeException("The server returned a successful status code " 
                            + status + " but an empty body.");
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } else {
                throw new RuntimeException("The server returned an error status code: " + status 
                        + ". Reason: " + response.getReasonPhrase());
            }
        });
    }

    /**
     * Evaluates active configuration criteria to run specific TLS certificate lifecycle updates.
     * 
     * @param urlString the destination endpoint string used to scope the certificate operation
     */
    private void triggerCertUpdate(String urlString) {
        GSPApiConfig.AuthConfig auth = config.getAuth();
        if (auth != null && auth.getType() != null) {
            String authType = auth.getType().toLowerCase();
            if ("basic".equals(authType) && auth.getUsername() != null && auth.getPassword() != null) {
                CertUpdater.updateByBasicAuth(urlString, auth.getUsername(), auth.getPassword());
            } else if ("token".equals(authType) && auth.getToken() != null) {
                CertUpdater.updateByToken(urlString, auth.getToken());
            } else if ("none".equals(authType)) {
                // Execute certificate update for public endpoints that do not require credentials.
                CertUpdater.updateWithoutAuth(urlString);
            }
        }
    }

}

