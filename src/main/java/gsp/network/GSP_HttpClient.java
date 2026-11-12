package gsp.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import gsp.config.GSPApiConfig;
import my.CertUpdater;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client wrapper responsible for interacting with the GSP API over HTTP.
 * Supports none, basic, static token, and OAuth2 Client Credentials authentication.
 */
public class GSP_HttpClient {

    private final GSPApiConfig config;
    private final HttpClientBuilder httpClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CloseableHttpClient httpClient;

    // Cache fields for the dynamic OAuth2 access token
    private String cachedOAuth2Token;
    private Instant tokenExpiresAt = Instant.EPOCH;

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
     * @return the encoded authentication token string, or null if auth_type is explicitly set to "none"
     * @throws Exception if auth_type is missing/unsupported, credentials are incomplete, or OAuth2 token exchange fails
     */
    private String buildAuthHeader() throws Exception {
        String rawAuthType = config.getAuthType();
        String authType = (rawAuthType != null) ? rawAuthType.trim().toLowerCase() : "";
        GSPApiConfig.AuthConfig auth = config.getAuth();

        try {
            return switch (authType) {
                case "basic" -> {
                    if (auth == null || auth.getBasic() == null
                            || auth.getBasic().getUsername() == null
                            || auth.getBasic().getPassword() == null) {
                        throw new IllegalStateException("Basic auth credentials (username/password) are missing in configuration.");
                    }
                    String authString = auth.getBasic().getUsername() + ":" + auth.getBasic().getPassword();
                    yield "Basic " + Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
                }
                case "token" -> {
                    if (auth == null || auth.getStaticToken() == null || auth.getStaticToken().trim().isEmpty()) {
                        throw new IllegalStateException("Static Bearer token is missing in configuration.");
                    }
                    yield "Bearer " + auth.getStaticToken().trim();
                }
                case "oauth2" -> {
                    if (auth == null || auth.getOauth2() == null) {
                        throw new IllegalStateException("OAuth2 configuration block is missing.");
                    }
                    yield "Bearer " + getOrRefreshOAuth2Token(auth.getOauth2());
                }
                case "none" -> {
                    logger.debug("Executing requests without authentication headers (auth_type: none)");
                    yield null;
                }
                default -> throw new IllegalStateException(
                        authType.isEmpty()
                                ? "Authentication type (auth_type) is missing or empty in configuration."
                                : "Unsupported authentication type detected: '" + rawAuthType + "'"
                );
            };
        } catch (IllegalStateException e) {
            logger.error("Authorization header build failed: {}", e.getMessage());
            throw e;
        }
    }


    /**
     * Returns a valid cached OAuth2 access token or requests a new one from the token endpoint.
     *
     * @param oauth2Config the OAuth2 client credentials configuration
     * @return a valid Bearer access token
     * @throws Exception if the token request or JSON response parsing fails
     */
    private synchronized String getOrRefreshOAuth2Token(GSPApiConfig.OAuth2Config oauth2Config) throws Exception {
        // Return the cached token if it is still valid (applying a 30-second safety buffer before expiration)
        if (cachedOAuth2Token != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedOAuth2Token;
        }

        String tokenEndpoint = oauth2Config.getTokenEndpoint();
        String clientId = oauth2Config.getClientId();
        String clientSecret = oauth2Config.getClientSecret();
        String scope = oauth2Config.getScope();

        if (tokenEndpoint == null || clientId == null || clientSecret == null) {
            throw new IllegalStateException("OAuth2 credentials (token_endpoint, client_id, client_secret) are incomplete.");
        }

        logger.info("Requesting new OAuth2 access token from: {}", tokenEndpoint);

        // Prepare form parameters for the Client Credentials Grant request
        List<NameValuePair> formParams = new ArrayList<>();
        formParams.add(new BasicNameValuePair("grant_type", "client_credentials"));
        formParams.add(new BasicNameValuePair("client_id", clientId));
        formParams.add(new BasicNameValuePair("client_secret", clientSecret));
        if (scope != null && !scope.trim().isEmpty()) {
            formParams.add(new BasicNameValuePair("scope", scope.trim()));
        }

        ClassicHttpRequest tokenRequest = ClassicRequestBuilder.post(tokenEndpoint)
                .setHeader("Accept", "application/json")
                .setEntity(new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8))
                .build();

        return httpClient.execute(tokenRequest, response -> {
            int status = response.getCode();
            String body = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : "";

            if (status >= 200 && status < 300) {
                JsonNode jsonNode = objectMapper.readTree(body);
                JsonNode tokenNode = jsonNode.get("access_token");

                if (tokenNode == null || tokenNode.asText().isEmpty()) {
                    throw new RuntimeException("OAuth2 response did not contain 'access_token'. Body: " + body);
                }

                this.cachedOAuth2Token = tokenNode.asText();
                // Default to a 3600-second lifetime if 'expires_in' is omitted by the authorization server
                long expiresIn = jsonNode.has("expires_in") ? jsonNode.get("expires_in").asLong(3600) : 3600;
                this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn);

                logger.info("OAuth2 access token successfully acquired (expires in {}s).", expiresIn);
                return this.cachedOAuth2Token;
            } else {
                throw new RuntimeException("Failed to obtain OAuth2 token. HTTP status: " + status + ", Body: " + body);
            }
        });
    }

    /**
     * Executes an HTTP GET request to fetch the raw JSON payload from the configured API endpoint.
     *
     * @return the raw JSON response string
     * @throws Exception if an error occurs during execution, connection, or response validation
     */
    public String fetchRawJson() throws Exception {
        String urlString = config.getDataEndpoint();

        // Abort early if the endpoint URI is missing or empty
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalStateException("API data_endpoint URI is not configured in the properties file.");
        }

        // Lazy initialization block to ensure certificates and timeouts are configured before the client is built
        synchronized (this) {
            if (this.httpClient == null) {
                triggerCertUpdate(urlString);

                // Resolve timeouts from configuration (with safe defaults)
                GSPApiConfig.ConnectionConfig connCfg = (config.getConnection() != null)
                        ? config.getConnection()
                        : new GSPApiConfig.ConnectionConfig();

                Timeout connectTimeout = Timeout.ofMilliseconds(connCfg.getConnectTimeoutMs());
                Timeout responseTimeout = Timeout.ofMilliseconds(connCfg.getResponseTimeoutMs());

                // 1. Configure TCP/TLS connection establishment timeout
                ConnectionConfig connectionConfig = ConnectionConfig.custom()
                        .setConnectTimeout(connectTimeout)
                        .build();

                SSLContext sslContext = my.KeyStoreManager.getSSLContext();
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);

                PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();

                // 2. Configure default response (socket read) timeout for all requests
                RequestConfig defaultRequestConfig = RequestConfig.custom()
                        .setResponseTimeout(responseTimeout)
                        .build();

                // Initialize the CloseableHttpClient with security context and global timeouts
                this.httpClient = httpClientBuilder
                        .setConnectionManager(connManager)
                        .setDefaultRequestConfig(defaultRequestConfig)
                        .build();

                logger.info("HTTP client initialized (connectTimeout={}ms, responseTimeout={}ms).",
                        connCfg.getConnectTimeoutMs(), connCfg.getResponseTimeoutMs());
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
    	String rawAuthType = config.getAuthType();
        if (rawAuthType == null) {
        	return;
        }
        String authType = rawAuthType.trim().toLowerCase();
        GSPApiConfig.AuthConfig auth = config.getAuth();
        if ("basic".equals(authType) && auth != null && auth.getBasic() != null) {
        	String username = auth.getBasic().getUsername();
        	String password = auth.getBasic().getPassword();
        	if (username != null && password != null) {
        		CertUpdater.updateByBasicAuth(urlString, username, password);
        	}
        } else if ("token".equals(authType) && auth != null && auth.getStaticToken() != null) {
        	CertUpdater.updateByToken(urlString, auth.getStaticToken());
        } else if ("oauth2".equals(authType) && auth != null && auth.getOauth2() != null) {
        	// Update TLS certificates for both the identity provider and the target data endpoint
        	String tokenEndpoint = auth.getOauth2().getTokenEndpoint();
        	if (tokenEndpoint != null && !tokenEndpoint.trim().isEmpty()) {
        		CertUpdater.updateWithoutAuth(tokenEndpoint);
        	}
        	CertUpdater.updateWithoutAuth(urlString);
        } else if ("none".equals(authType)) {
        	// Execute certificate update for public endpoints that do not require credentials
        	CertUpdater.updateWithoutAuth(urlString);
        }
    }
}