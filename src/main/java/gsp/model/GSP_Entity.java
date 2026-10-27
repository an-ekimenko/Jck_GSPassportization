package gsp.model;

import jakarta.persistence.Column;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database entity class representing the GS_Passportization table.
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
@Entity 
@Table(name = "GS_Passportization")
public class GSP_Entity {
	
    @Id @Column(name = "IdMDM")
    private Integer id;

    @Column(name = "Description") 
    private String description;

    @Column(name = "Address") 
    private String address;

    
    @Column(name = "Fuels") 
    private String fuels;
    
    @Column 
    private Double latitude;

    @Column 
    private Double longitude;
    
    @Column(name = "District") 
    private String district;
}
