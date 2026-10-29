package gsp.jackson_json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import gsp.model.GSP_Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GSP_DataDeserializer Architecture Unit Tests")
class GSP_DataDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Enforce the same configuration rules as defined in GSPAppModule
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    @DisplayName("Should successfully parse pristine valid JSON structures matching production API payload")
    void testValidStandardPayload() throws Exception {
        // Updated payload utilizing actual mock data structures extracted from production API
        String json = """
            [
              {
                "azsnum": "72",
                "complex": "122",
                "complexdesc": "АЗК 72 N 72",
                "latitude": "53.637231",
                "longitude": "23.878212",
                "unit": "РУП \\"БЕЛОРУСНЕФТЬ-ГРОДНООБЛНЕФТЕПРОДУКТ\\"",
                "unitshort": "Гроднооблнефтепродукт",
                "unitcode": "122",
                "mailaddress": "Гродненский р-н, Коптевский с\\\\с, 8",
                "area": "Гродненская",
                "district": "Гродненский",
                "towntype": "",
                "town": "",
                "streettype": "Шоссе",
                "street": "Коптевский с\\\\с",
                "building": "8",
                "zipcode": "231702",
                "fuels": ["АИ-95", "ДТ", "АИ-92", "АИ-98"]
              }
            ]
            """;

        List<GSP_Data> result = objectMapper.readValue(json, new TypeReference<List<GSP_Data>>() {});

        assertNotNull(result);
        assertEquals(1, result.size());
        GSP_Data data = result.get(0);

        // Core assertions mapping directly to Jck_GS_Pasportization extraction requirements
        assertEquals(122, data.getId());
        assertEquals(53.637231, data.getLatitude(), 0.000001);
        assertEquals(23.878212, data.getLongitude(), 0.000001);
        assertEquals("Гродненский р-н, Коптевский с\\с, 8", data.getAddress());
        assertEquals("Гродненский", data.getDistrict());
        assertEquals("АИ-95, ДТ, АИ-92, АИ-98", data.getFuels());
    }


    @ParameterizedTest(name = "[{index}] Should parse raw coordinate \"{0}\" into Double value {1}")
    @CsvSource({
        "55.7558,           55.7558",
        "'55,7558',         55.7558",
        "'  37.6173  ',     37.6173",
        "'55,7558 degrees', 55.7558"
    })
    @DisplayName("Should sanitize mixed coordinate formats and extract numeric data")
    void testCoordinateNormalization(String rawValue, Double expectedCoordinate) throws Exception {
        // Construct a dynamic single-item JSON with the parameterized coordinate
        String json = String.format("[{\"latitude\": %s, \"longitude\": %s}]", 
                escapeJsonFieldValue(rawValue), 
                escapeJsonFieldValue(rawValue));

        List<GSP_Data> result = objectMapper.readValue(json, new TypeReference<List<GSP_Data>>() {});
        GSP_Data data = result.get(0);

        assertEquals(expectedCoordinate, data.getLatitude(), 0.0001);
        assertEquals(expectedCoordinate, data.getLongitude(), 0.0001);
    }

    @ParameterizedTest(name = "[{index}] Should extract ID {1} from raw complex string \"{0}\"")
    @CsvSource({
        "100,               100",
        "-450,              450",
        "'ID-99954-XYZ',    99954",
        "'000123',          123"
    })
    @DisplayName("Should extract valid positive integer identifiers and enforce absolute values")
    void testComplexIdExtraction(String rawComplex, Integer expectedId) throws Exception {
        String json = String.format("[{\"complex\": %s}]", escapeJsonFieldValue(rawComplex));

        List<GSP_Data> result = objectMapper.readValue(json, new TypeReference<List<GSP_Data>>() {});
        
        assertEquals(expectedId, result.get(0).getId());
    }

    @ParameterizedTest(name = "[{index}] Should safely return null for invalid complex token: \"{0}\"")
    @NullAndEmptySource
    @CsvSource({
        "'   '",
        "'NoDigitsHere'",
        "'99999999999999999999'" // Triggers structural long/integer overflow inside parsing logic
    })
    @DisplayName("Should gracefully handle missing nodes, empty strings, and numerical overflows without crashing")
    void testComplexIdResilienceEdgeCases(String edgeCaseValue) throws Exception {
        String json = String.format("[{\"complex\": %s}]", escapeJsonFieldValue(edgeCaseValue));

        List<GSP_Data> result = objectMapper.readValue(json, new TypeReference<List<GSP_Data>>() {});
        
        assertNull(result.get(0).getId());
    }

    @ParameterizedTest(name = "[{index}] Should clean and join fuel arrays into: \"{1}\"")
    @MethodSource("provideFuelsTestData")
    @DisplayName("Should discard empty, blank, or duplicate elements when processing fuel arrays")
    void testFuelsArrayEdgeCases(List<String> inputFuels, String expectedResult) throws Exception {
        // Programmatically generate a clean JSON array structure using Jackson mapper
        String jsonArrayNode = objectMapper.writeValueAsString(inputFuels);
        String json = String.format("[{\"complex\": 777, \"fuels\": %s}]", jsonArrayNode);

        List<GSP_Data> result = objectMapper.readValue(json, new TypeReference<List<GSP_Data>>() {});
        
        assertEquals(expectedResult, result.get(0).getFuels());
    }

    /**
     * Data provider method for dynamic fuel array scenario validation.
     */
    private static Stream<Arguments> provideFuelsTestData() {
        return Stream.of(
            Arguments.of(List.of("A95", "Propane"), "A95, Propane"),
            Arguments.of(List.of("A95", "", "   ", "Diesel"), "A95, Diesel"),
            Arguments.of(List.of("", "   "), "")
        );
    }

    /**
     * Utility helper to safely simulate numerical raw nodes vs textual nodes in dynamic JSON strings.
     */
    private String escapeJsonFieldValue(String value) {
        if (value == null) {
            return "null";
        }
        // If it's a numeric digit sequence or clear primitive float representation, keep it raw, otherwise wrap in quotes
        if (value.trim().matches("-?\\d+([.,]\\d+)?")) {
            return value.trim();
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
