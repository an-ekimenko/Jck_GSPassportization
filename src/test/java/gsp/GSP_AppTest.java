package gsp;

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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GSP_AppTest {

    @Mock
    private GSP_HttpClient httpClient;

    @Mock
    private GSP_DAO gspDao;

    @Captor
    private ArgumentCaptor<List<GSP_Entity>> entityListCaptor;

    private GSP_App gspApp;

    @BeforeEach
    void setUp() {
         ObjectMapper objectMapper = new ObjectMapper();
         SimpleModule module = new SimpleModule();
         module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
         objectMapper.registerModule(module);
         gspApp = new GSP_App(httpClient, gspDao, objectMapper);
   }

    @Test
    @DisplayName("Should fetch, deduplicate by complex ID, persist batch, and return unique record count")
    void run_ValidPayloadWithDuplicates_DeduplicatesAndReturnsProcessedCount() throws Exception {
        // Arrange: 3 records in JSON matching GSP_DataDeserializer schema; 2 share the same 'complex' ID (101)
        String sampleJson = "["
                + "{\"complex\": 101, \"mailaddress\": \"Main St 1\", \"fuels\": [\"AI-95\", \"AI-98\"], \"latitude\": 52.42, \"longitude\": 31.01, \"district\": \"Central\"},"
                + "{\"complex\": \"101\", \"mailaddress\": \"Duplicate St\", \"fuels\": [\"AI-92\"], \"latitude\": 52.42, \"longitude\": 31.01, \"district\": \"Central\"},"
                + "{\"complex\": 202, \"mailaddress\": \"Second Ave 5\", \"fuels\": [\"DT\"], \"latitude\": 52.45, \"longitude\": 31.05, \"district\": \"North\"}"
                + "]";

        when(httpClient.fetchRawJson()).thenReturn(sampleJson);

        // Act
        int processedCount = gspApp.run();

        // Assert: only 2 unique entities should be saved and counted
        assertEquals(2, processedCount, "Returned count must match the number of deduplicated entities");

        verify(gspDao, times(1)).saveOrUpdateBatch(entityListCaptor.capture());
        List<GSP_Entity> persistedEntities = entityListCaptor.getValue();
        assertEquals(2, persistedEntities.size());

        // Verify first entity fields mapped by GSP_DataDeserializer
        GSP_Entity first = persistedEntities.get(0);
        assertEquals(101, first.getId());
        assertEquals("Main St 1", first.getAddress());
        assertEquals("AI-95, AI-98", first.getFuels());
        assertEquals("Central", first.getDistrict());

        // Verify second entity ID
        assertEquals(202, persistedEntities.get(1).getId());
    }


    @Test
    @DisplayName("Should return 0 and skip database persistence when API returns an empty JSON array")
    void run_EmptyJsonArray_ReturnsZeroAndSkipsDao() throws Exception {
        // Arrange
        when(httpClient.fetchRawJson()).thenReturn("[]");

        // Act
        int processedCount = gspApp.run();

        // Assert
        assertEquals(0, processedCount);
        verifyNoInteractions(gspDao);
    }

    @Test
    @DisplayName("Should throw RuntimeException when API returns blank or null payload")
    void run_BlankPayload_ThrowsRuntimeException() throws Exception {
        // Arrange
        when(httpClient.fetchRawJson()).thenReturn("   ");

        // Act & Assert
        assertThrows(RuntimeException.class, () -> gspApp.run());
        verifyNoInteractions(gspDao);
    }

    @Test
    @DisplayName("Should propagate RuntimeException when HTTP client fails")
    void run_HttpClientThrowsException_WrapsAndThrowsRuntimeException() throws Exception {
        // Arrange
        when(httpClient.fetchRawJson()).thenThrow(new RuntimeException("Connection timeout"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> gspApp.run());
        verifyNoInteractions(gspDao);
    }
}

