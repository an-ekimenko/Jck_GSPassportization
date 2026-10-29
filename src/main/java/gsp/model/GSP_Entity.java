package gsp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent JPA entity mapping to the GS_Passportization staging table.
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
@Entity 
@Table(name = "GS_Passportization")
public class GSP_Entity {
	
	@Id 
	@Column(name = "IdMDM")
	private Integer id;

	@Column(name = "Description") 
	private String description;

	@Column(name = "Address") 
	private String address;

	@Column(name = "Fuels") 
	private String fuels;
    
	@Column(name = "Latitude") 
	private Double latitude;

	@Column(name = "Longitude") 
	private Double longitude;
    
	@Column(name = "District") 
	private String district;
}
