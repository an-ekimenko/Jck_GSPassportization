package gsp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing the execution summary written to result.yaml.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonRootName("output")
public class ExecutionResult {

    @JsonProperty("datetime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd:HH:mm:ss")
    private LocalDateTime datetime;

    @JsonProperty("returned_code")
    private int returnedCode;

    @JsonProperty("records_processed")
    private int recordsProcessed;
}
