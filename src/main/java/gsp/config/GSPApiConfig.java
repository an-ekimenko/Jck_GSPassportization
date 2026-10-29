package gsp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;

/**
 * Configuration DTO for the Gas Station Passportization API connection settings.
 * Stores endpoint URLs and parameters for various authentication strategies.
 */
@Data
@JsonRootName("gs_passportization-api")
public class GSPApiConfig {

	@JsonProperty("endpoint")
	private String endpoint;

	@JsonProperty("auth")
	private AuthConfig auth;

	/**
	 * Authentication parameters for the target API.
	 * Supports credential-based and token-based strategies.
	 */
	@Data
	public static class AuthConfig {
		
		@JsonProperty("type")
		private String type;

		@JsonProperty("username")
		private String username;

		@JsonProperty("password")
		private String password;

		@JsonProperty("token")
		private String token;
	}
}
