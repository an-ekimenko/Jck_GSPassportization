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
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
        objectMapper.registerModule(module);

        gspApp = new GSP_App(mockHttpClient, mockGspDao, objectMapper);
    }

    @Test
    @DisplayName("Should process payload, map to entities, filter duplicates, and trigger batch save")
    void testSuccessfulPipelineExecutionWithDeduplication() throws Exception {
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

        gspApp.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GSP_Entity>> entityListCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockGspDao, times(1)).saveOrUpdateBatch(entityListCaptor.capture());

        List<GSP_Entity> capturedEntities = entityListCaptor.getValue();
        
        assertNotNull(capturedEntities);
        assertEquals(2, capturedEntities.size());

        GSP_Entity entity1 = capturedEntities.get(0);
        assertEquals(122, entity1.getId());
        assertEquals(53.637231, entity1.getLatitude());
        assertEquals("Address 1", entity1.getAddress());
        assertEquals("District A", entity1.getDistrict());

        GSP_Entity entity2 = capturedEntities.get(1);
        assertEquals(387, entity2.getId());
        assertEquals(55.123456, entity2.getLatitude());
        assertEquals("Address 2", entity2.getAddress());
        assertEquals("District B", entity2.getDistrict());
    }

    @Test
    @DisplayName("Should abort pipeline execution early and skip database write when API returns empty payload")
    void testPipelineAbortsOnEmptyPayload() throws Exception {
        // Arrange: Симулируем пустой ответ (пробелы) от удаленного эндпоинта
        when(mockHttpClient.fetchRawJson()).thenReturn("   ");

        // Act & Assert: Код оборачивает ошибку в RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> gspApp.run());
        
        // Проверяем, что корневой причиной является именно IllegalStateException
        assertNotNull(exception.getCause(), "Исключение не содержит вложенной причины!");
        assertTrue(exception.getCause() instanceof IllegalStateException, 
                "Ожидалась причина IllegalStateException, но была: " + exception.getCause().getClass().getName());
        assertTrue(exception.getCause().getMessage().contains("Received empty payload"),
                "Текст сообщения внутренней ошибки некорректен!");

        // Проверяем, что до сохранения в БД дело не дошло
        verify(mockGspDao, never()).saveOrUpdateBatch(anyList());
    }

}
