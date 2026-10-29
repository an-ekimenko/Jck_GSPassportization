package gsp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing a gas station complex mapped 
 * directly from the raw top-level JSON response arrays.
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
public class GSP_Data {

    @JsonProperty("complex") 
    private Integer id;
    
    @JsonProperty("complexdesc") 
    private String description;
    
    @JsonProperty("mailaddress") 
    private String address;
    
    @JsonProperty("fuels") 
    private String fuels; 
    
    @JsonProperty("district") 
    private String district;
    
    @JsonProperty("latitude") 
    private Double latitude;
    
    @JsonProperty("longitude") 
    private Double longitude;
}

