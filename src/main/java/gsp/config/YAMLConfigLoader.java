package gsp.config;

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YAMLConfigLoader {

	private static final Logger logger = LogManager.getLogger();
	private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
	
	private YAMLConfigLoader() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	• 
	Loads the configuration from a YAML file and deserializes it into a DTO class.
	• 
	@param fileName the name of the configuration file (e.g., "properties.yaml")
	• 
	@return the configured GSPApiConfig object
	*/
	public static GSPApiConfig load(String fileName) {
		try {
			// Resolve the path to the configuration file within the root 'config' directory
			File externalFile = new File("config" + File.separator + fileName);
			if (externalFile.exists() && externalFile.isFile()) {
				logger.info("Loading configuration from external folder: {}", externalFile.getAbsolutePath());
				return yamlMapper.readValue(externalFile, GSPApiConfig.class);
			} else {
				throw new FileNotFoundException(
						String.format("Critical configuration file is missing: '%s'. " +
								"Application cannot start without a valid configuration. " +
								"Please create it based on the '%s.dist' template.", 
								externalFile.getAbsolutePath(), externalFile.getAbsolutePath())
						);
			}
		} catch (Exception e) {
			logger.fatal("Application initialization failed due to configuration error", e);
			throw new RuntimeException("Fatal error during application startup", e);
		}
	}
}