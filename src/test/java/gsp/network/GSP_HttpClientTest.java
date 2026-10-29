package gsp.network;

import gsp.config.GSPApiConfig;
import gsp.config.GSPApiConfig.AuthConfig;
import my.KeyStoreManager;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import org.apache.hc.core5.http.io.HttpClientResponseHandler;


import org.apache.hc.core5.http.ClassicHttpRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GSP_HttpClient Infrastructure Unit Tests")
class GSP_HttpClientTest {

    @Mock
    private GSPApiConfig config;

    @Mock
    private HttpClientBuilder httpClientBuilder;

    @Mock
    private CloseableHttpClient mockCloseableHttpClient;

    private GSP_HttpClient gspHttpClient;

    @BeforeEach
    void setUp() {
        gspHttpClient = new GSP_HttpClient(config, httpClientBuilder);
    }

    @Test
    @DisplayName("Should successfully execute request and generate Basic Auth header mapped in Base64")
    void testFetchWithBasicAuth() throws Exception {
        // Arrange
        String targetUrl = "https://gasstations.com";
        when(config.getEndpoint()).thenReturn(targetUrl);

        AuthConfig authConfig = new AuthConfig();
        authConfig.setType("basic");
        authConfig.setUsername("admin");
        authConfig.setPassword("p@ssword123");
        when(config.getAuth()).thenReturn(authConfig);

        // Stub standard Apache HttpClient internal build mechanics
        when(httpClientBuilder.setConnectionManager(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.build()).thenReturn(mockCloseableHttpClient);

        // Stub remote API successful string execution response
        String mockJsonResponse = "[{\"complex\": \"122\"}]";
     
        when(mockCloseableHttpClient.execute(any(ClassicHttpRequest.class), 
                							 any(HttpClientResponseHandler.class)))
                					.thenReturn(mockJsonResponse);

        // Mock static TLS management routines to bypass network handshake updates during test execution
        try (MockedStatic<KeyStoreManager> mockedKeyStore = mockStatic(KeyStoreManager.class)) {
            mockedKeyStore.when(KeyStoreManager::getSSLContext).thenReturn(SSLContext.getDefault());

            // Act
            String actualResponse = gspHttpClient.fetchRawJson();

            // Assert
            assertEquals(mockJsonResponse, actualResponse);

            // Capture the HTTP request structure right before it hits the wire
            ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
            verify(mockCloseableHttpClient).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));

            ClassicHttpRequest capturedRequest = requestCaptor.getValue();
            assertNotNull(capturedRequest);
            assertEquals("application/json", capturedRequest.getFirstHeader("Accept").getValue());

            // Validate Base64 encoding structure: "Basic " + base64("admin:p@ssword123")
            String expectedAuthToken = "Basic " + Base64.getEncoder()
                    .encodeToString("admin:p@ssword123".getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedAuthToken, capturedRequest.getFirstHeader("Authorization").getValue());
        }
    }

    @Test
    @DisplayName("Should successfully execute request and apply Bearer Token authentication headers")
    void testFetchWithBearerTokenAuth() throws Exception {
        // Arrange
        String targetUrl = "https://gasstations.com";
        when(config.getEndpoint()).thenReturn(targetUrl);

        AuthConfig authConfig = new AuthConfig();
        authConfig.setType("token");
        authConfig.setToken("secret-jwt-token-string");
        when(config.getAuth()).thenReturn(authConfig);

        when(httpClientBuilder.setConnectionManager(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.build()).thenReturn(mockCloseableHttpClient);
        when(mockCloseableHttpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenReturn("[]");

        try (MockedStatic<KeyStoreManager> mockedKeyStore = mockStatic(KeyStoreManager.class)) {
            mockedKeyStore.when(KeyStoreManager::getSSLContext).thenReturn(SSLContext.getDefault());

            // Act
            gspHttpClient.fetchRawJson();

            // Assert
            ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
            verify(mockCloseableHttpClient).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));

            ClassicHttpRequest capturedRequest = requestCaptor.getValue();
            assertEquals("Bearer secret-jwt-token-string", capturedRequest.getFirstHeader("Authorization").getValue());
        }
    }

    @Test
    @DisplayName("Should omit Authorization headers entirely when authentication scheme is set to none")
    void testFetchWithoutAuthenticationHeaders() throws Exception {
        // Arrange
        String targetUrl = "https://gasstations.com";
        when(config.getEndpoint()).thenReturn(targetUrl);

        AuthConfig authConfig = new AuthConfig();
        authConfig.setType("none");
        when(config.getAuth()).thenReturn(authConfig);

        when(httpClientBuilder.setConnectionManager(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.build()).thenReturn(mockCloseableHttpClient);
        when(mockCloseableHttpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenReturn("[]");

        try (MockedStatic<KeyStoreManager> mockedKeyStore = mockStatic(KeyStoreManager.class)) {
            mockedKeyStore.when(KeyStoreManager::getSSLContext).thenReturn(SSLContext.getDefault());

            // Act
            gspHttpClient.fetchRawJson();

            // Assert
            ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
            verify(mockCloseableHttpClient).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));

            ClassicHttpRequest capturedRequest = requestCaptor.getValue();
            assertNull(capturedRequest.getFirstHeader("Authorization"));
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException early when required endpoint configuration profile is missing")
    void testThrowsExceptionWhenEndpointIsMissing() {
        // Arrange
        when(config.getEndpoint()).thenReturn(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> gspHttpClient.fetchRawJson());
        assertTrue(exception.getMessage().contains("API endpoint URI is not configured"));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when selected auth scheme credentials are left blank")
    void testThrowsExceptionWhenCredentialsAreMissing() {
        // Arrange
        when(config.getEndpoint()).thenReturn("https://gasstations.com");

        AuthConfig authConfig = new AuthConfig();
        authConfig.setType("basic");
        authConfig.setUsername(null); // Explicit violation: username is missing
        authConfig.setPassword("password");
        when(config.getAuth()).thenReturn(authConfig);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> gspHttpClient.fetchRawJson());
        assertTrue(exception.getMessage().contains("Basic auth credentials (username/password) are missing"));
    }
}
