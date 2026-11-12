package gsp.network;

import gsp.config.GSPApiConfig;
import my.CertUpdater;
import my.KeyStoreManager;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GSP_HttpClientTest {

    @Mock
    private HttpClientBuilder httpClientBuilder;

    @Mock
    private CloseableHttpClient mockHttpClient;

    private GSPApiConfig config;
    private GSP_HttpClient gspHttpClient;

    @BeforeEach
    void setUp() {
        config = new GSPApiConfig();
        config.setDataEndpoint("https://example.com");

        lenient().when(httpClientBuilder.setConnectionManager(any())).thenReturn(httpClientBuilder);
        lenient().when(httpClientBuilder.setDefaultRequestConfig(any())).thenReturn(httpClientBuilder);
        lenient().when(httpClientBuilder.build()).thenReturn(mockHttpClient);

        gspHttpClient = new GSP_HttpClient(config, httpClientBuilder);
    }

    @Test
    @DisplayName("Basic Auth: should encode username:password into Basic Authorization header")
    void fetchRawJson_BasicAuth_SetsBasicHeader() throws Exception {
        // Arrange
        config.setAuthType("basic");
        GSPApiConfig.AuthConfig auth = new GSPApiConfig.AuthConfig();
        GSPApiConfig.BasicAuthConfig basic = new GSPApiConfig.BasicAuthConfig();
        basic.setUsername("admin");
        basic.setPassword("secret123");
        auth.setBasic(basic);
        config.setAuth(auth);

        stubSingleResponse(200, "[{\"id\":1}]");

        // Act
        String result = executeWithMockedSecurity(() -> gspHttpClient.fetchRawJson());

        // Assert
        assertEquals("[{\"id\":1}]", result);
        ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        verify(mockHttpClient).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));

        Header authHeader = requestCaptor.getValue().getFirstHeader("Authorization");
        assertNotNull(authHeader);
        String expectedEncoded = Base64.getEncoder().encodeToString("admin:secret123".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expectedEncoded, authHeader.getValue());
    }

    @Test
    @DisplayName("Static Token Auth: should attach Bearer header with configured static_token")
    void fetchRawJson_StaticTokenAuth_SetsBearerHeader() throws Exception {
        // Arrange
        config.setAuthType("token");
        GSPApiConfig.AuthConfig auth = new GSPApiConfig.AuthConfig();
        auth.setStaticToken("my-static-jwt-token");
        config.setAuth(auth);

        stubSingleResponse(200, "[{\"id\":2}]");

        // Act
        String result = executeWithMockedSecurity(() -> gspHttpClient.fetchRawJson());

        // Assert
        assertEquals("[{\"id\":2}]", result);
        ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        verify(mockHttpClient).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));

        Header authHeader = requestCaptor.getValue().getFirstHeader("Authorization");
        assertNotNull(authHeader);
        assertEquals("Bearer my-static-jwt-token", authHeader.getValue());
    }

    @Test
    @DisplayName("OAuth2 Client Credentials: should exchange credentials for token, cache it, and send Bearer header")
    void fetchRawJson_OAuth2Auth_AcquiresTokenCachesAndSendsBearerHeader() throws Exception {
        // Arrange
        config.setAuthType("oauth2");
        GSPApiConfig.AuthConfig auth = new GSPApiConfig.AuthConfig();
        GSPApiConfig.OAuth2Config oauth2 = new GSPApiConfig.OAuth2Config();
        oauth2.setTokenEndpoint("https://example.com");
        oauth2.setClientId("gsp-client");
        oauth2.setClientSecret("gsp-secret");
        oauth2.setScope("gsp.read");
        auth.setOauth2(oauth2);
        config.setAuth(auth);

        // First call -> POST to token_endpoint; Second & Third calls -> GET to data_endpoint (reusing cached token)
        when(mockHttpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ClassicHttpRequest req = invocation.getArgument(0);
                    HttpClientResponseHandler<String> handler = invocation.getArgument(1);

                    ClassicHttpResponse mockResp = mock(ClassicHttpResponse.class);
                    when(mockResp.getCode()).thenReturn(200);

                    if ("POST".equalsIgnoreCase(req.getMethod())) {
                        String tokenJson = "{\"access_token\":\"dynamic-oauth2-token\",\"expires_in\":3600}";
                        when(mockResp.getEntity()).thenReturn(new StringEntity(tokenJson, StandardCharsets.UTF_8));
                    } else {
                        when(mockResp.getEntity()).thenReturn(new StringEntity("[{\"id\":3}]", StandardCharsets.UTF_8));
                    }
                    return handler.handleResponse(mockResp);
                });

        // Act: perform two data fetches to verify token caching
        String firstResult = executeWithMockedSecurity(() -> gspHttpClient.fetchRawJson());
        String secondResult = executeWithMockedSecurity(() -> gspHttpClient.fetchRawJson());

        // Assert
        assertEquals("[{\"id\":3}]", firstResult);
        assertEquals("[{\"id\":3}]", secondResult);

        ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        // Total HTTP calls = 3 (1 POST for token + 2 GETs for data; second GET reused cached token)
        verify(mockHttpClient, times(3)).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));

        List<ClassicHttpRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals("POST", capturedRequests.get(0).getMethod());
        assertEquals("/connect/token", capturedRequests.get(0).getPath());

        assertEquals("GET", capturedRequests.get(1).getMethod());
        assertEquals("Bearer dynamic-oauth2-token", capturedRequests.get(1).getFirstHeader("Authorization").getValue());

        assertEquals("GET", capturedRequests.get(2).getMethod());
        assertEquals("Bearer dynamic-oauth2-token", capturedRequests.get(2).getFirstHeader("Authorization").getValue());
    }

    @Test
    @DisplayName("None Auth: should omit Authorization header completely")
    void fetchRawJson_NoneAuth_OmitsAuthorizationHeader() throws Exception {
        // Arrange
        config.setAuthType("none");
        stubSingleResponse(200, "[]");

        // Act
        executeWithMockedSecurity(() -> gspHttpClient.fetchRawJson());

        // Assert
        ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        verify(mockHttpClient).execute(requestCaptor.capture(), any(HttpClientResponseHandler.class));
        assertNull(requestCaptor.getValue().getFirstHeader("Authorization"));
    }

    @Test
    @DisplayName("Validation: should throw IllegalStateException when auth_type is missing or unsupported")
    void fetchRawJson_MissingOrInvalidAuthType_ThrowsIllegalStateException() {
        config.setAuthType("");

        assertThrows(IllegalStateException.class, () ->
                executeWithMockedSecurity(() -> gspHttpClient.fetchRawJson())
        );
    }

    @Test
    @DisplayName("Validation: should throw IllegalStateException when data_endpoint is missing")
    void fetchRawJson_MissingEndpoint_ThrowsIllegalStateException() {
        config.setDataEndpoint(null);
        config.setAuthType("none");

        assertThrows(IllegalStateException.class, () -> gspHttpClient.fetchRawJson());
        verifyNoInteractions(mockHttpClient);
    }

    // --- Helper methods ---

    private void stubSingleResponse(int statusCode, String responseBody) throws Exception {
        when(mockHttpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<String> handler = invocation.getArgument(1);
                    ClassicHttpResponse mockResp = mock(ClassicHttpResponse.class);
                    when(mockResp.getCode()).thenReturn(statusCode);
                    if (responseBody != null) {
                        when(mockResp.getEntity()).thenReturn(new StringEntity(responseBody, StandardCharsets.UTF_8));
                    }
                    return handler.handleResponse(mockResp);
                });
    }

    private <T> T executeWithMockedSecurity(ThrowingSupplier<T> action) throws Exception {
        try (MockedStatic<KeyStoreManager> ksMock = mockStatic(KeyStoreManager.class);
             MockedStatic<CertUpdater> certMock = mockStatic(CertUpdater.class)) {

            ksMock.when(KeyStoreManager::getSSLContext).thenReturn(SSLContext.getDefault());
            return action.get();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
    	T get() throws Exception;
    }
}
