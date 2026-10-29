package gsp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import gsp.hibernate.GSP_DAO;
import gsp.jackson_json.GSP_DataDeserializer;
import gsp.model.GSP_Data;
import gsp.model.GSP_Entity;
import gsp.network.GSP_HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GSP_App Integration Pipeline Tests")
class GSP_AppTest {

    @Mock
    private GSP_HttpClient mockHttpClient;

    @Mock
    private GSP_DAO mockGspDao;

    private ObjectMapper objectMapper;
    private GSP_App gspApp;

    @BeforeEach
    void setUp() {
        // Build a real production-grade ObjectMapper synchronized with GSPAppModule settings
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
        objectMapper.registerModule(module);

        // Inject mocks and real mapper into the application orchestrator
        gspApp = new GSP_App(mockHttpClient, mockGspDao, objectMapper);
    }

    @Test
    @DisplayName("Should process payload, map to entities, filter duplicates, and trigger batch save")
    void testSuccessfulPipelineExecutionWithDeduplication() throws Exception {
        // Arrange: Prepare mock JSON payload with explicit duplicate business keys (complex: "122")
        String mockApiJson = """
            [
              {
                "complex": "122",
                "latitude": "53.637231",
                "longitude": "23.878212",
                "mailaddress": "Address 1",
                "district": "District A"
              },
              {
                "complex": "122",
                "latitude": "53.637231",
                "longitude": "23.878212",
                "mailaddress": "Address 1 (Duplicate Entry)",
                "district": "District A"
              },
              {
                "complex": "387",
                "latitude": "55.123456",
                "longitude": "25.654321",
                "mailaddress": "Address 2",
                "district": "District B"
              }
            ]
            """;

        when(mockHttpClient.fetchRawJson()).thenReturn(mockApiJson);

        // Act: Execute the orchestrator pipeline
        gspApp.run();

        // Assert: Verify that GSP_DAO received data exactly 1 time
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GSP_Entity>> entityListCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockGspDao, times(1)).saveOrUpdateBatch(entityListCaptor.capture());

        List<GSP_Entity> capturedEntities = entityListCaptor.getValue();
        
        // In-memory stream deduplication must filter out the second "122" record
        assertNotNull(capturedEntities);
        assertEquals(2, capturedEntities.size(), "Pipeline failed to filter out duplicate business keys!");

        // Validate first entity mapping parameters
        GSP_Entity entity1 = capturedEntities.get(0);
        assertEquals(122, entity1.getId());
        assertEquals(53.637231, entity1.getLatitude());
        assertEquals("Address 1", entity1.getAddress());
        assertEquals("District A", entity1.getDistrict());

        // Validate second entity mapping parameters
        GSP_Entity entity2 = capturedEntities.get(1);
        assertEquals(387, entity2.getId());
        assertEquals(55.123456, entity2.getLatitude());
        assertEquals("Address 2", entity2.getAddress());
        assertEquals("District B", entity2.getDistrict());
    }

    @Test
    @DisplayName("Should abort pipeline execution early and skip database write when API returns empty payload")
    void testPipelineAbortsOnEmptyPayload() throws Exception {
        // Arrange: Simulate blank response from remote endpoint
        when(mockHttpClient.fetchRawJson()).thenReturn("   ");

        // Act & Assert: Expect RuntimeException wrapping the root failure cause
        RuntimeException exception = assertThrows(RuntimeException.class, () -> gspApp.run());
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("Received empty payload"));

        // Verify that database layer was completely bypassed
        verify(mockGspDao, never()).saveOrUpdateBatch(anyList());
    }

    @Test
    @DisplayName("Should skip database invocation when payload contains no valid entries after filtering steps")
    void testPipelineSkipsWriteWhenNoUniqueRecordsExist() throws Exception {
        // Arrange: API payload containing empty array structure "[]"
        when(mockHttpClient.fetchRawJson()).thenReturn("[]");

        // Act
        gspApp.run();

        // Assert: The execution completes gracefully, but DAO is never invoked
        verify(mockGspDao, never()).saveOrUpdateBatch(anyList());
    }
}
