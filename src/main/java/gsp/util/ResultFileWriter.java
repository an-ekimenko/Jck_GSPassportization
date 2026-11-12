package gsp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gsp.config.ConfigPathResolver;
import gsp.model.ExecutionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Utility class for serializing and writing execution results into the application's 'exchange' directory.
 */
public class ResultFileWriter {

    private static final Logger logger = LogManager.getLogger(ResultFileWriter.class);
    private static final String EXCHANGE_DIR = "exchange";
    private static final String RESULT_FILE_NAME = "result.yaml";

    private static final ObjectMapper yamlMapper = new ObjectMapper(
            new YAMLFactory()
                    // Omit the "---" document start marker for clean YAML output;
                    // comment out the line below if the marker is required by the target consumer.
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    )
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ResultFileWriter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Writes the execution summary to 'exchange/result.yaml' relative to the application root directory.
     *
     * @param returnedCode     the execution status code (e.g., 0 for success, non-zero for error)
     * @param recordsProcessed the total number of records processed during the run
     */
    public static void writeResult(int returnedCode, int recordsProcessed) {
        try {
            File exchangeDir = ConfigPathResolver.locateOrCreateDir(EXCHANGE_DIR);
            File resultFile = new File(exchangeDir, RESULT_FILE_NAME);

            ExecutionResult result = new ExecutionResult(
                    LocalDateTime.now(),
                    returnedCode,
                    recordsProcessed
            );

            yamlMapper.writeValue(resultFile, result);
            logger.info("Result file successfully written to: {}", resultFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Failed to write {} to exchange directory", RESULT_FILE_NAME, e);
            throw new RuntimeException("Error writing result.yaml", e);
        }
    }
}
