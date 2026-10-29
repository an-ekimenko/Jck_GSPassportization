package gsp.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.CodeSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility for resolving absolute paths to external application files and directories.
 * Supports both IDE development and standalone JAR execution environments.
 */
public class ConfigPathResolver {
	
	private static final Logger logger = LogManager.getLogger(ConfigPathResolver.class);
	
	private ConfigPathResolver() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	 * Resolves the absolute path to the specified file or directory relative to the application root.
	 * 
	 * @param relativePath the path relative to the application root (e.g., "config/properties.yaml")
	 * @return the verified File object
	 * @throws FileNotFoundException if the target file or directory does not exist
	 */
	public static File locate(String relativePath) throws FileNotFoundException {
		File externalFile = null;

		try {
			CodeSource codeSource = ConfigPathResolver.class.getProtectionDomain().getCodeSource();
			
			if (codeSource != null && codeSource.getLocation() != null) {
				// Convert URL to URI to handle character encoding and spaces properly
				File currentPath = new File(codeSource.getLocation().toURI());
				File baseDir = null;
				
				if ("classes".equals(currentPath.getName())) {
					// In IDE development (classes/), climb two levels up to reach the project root (target/ -> root/)
					File targetDir = currentPath.getParentFile();
					if (targetDir != null) {
						baseDir = targetDir.getParentFile();
					}
				} else {
					// In production (packaged JAR), the directory containing the JAR is the root
					baseDir = currentPath.getParentFile();
				}

				if (baseDir != null) {
					externalFile = new File(baseDir, relativePath);
				}
			}
		} catch (Exception e) {
			logger.error("Failed to analyze execution directory structure.", e);
		}

		// Ensure the file exists before returning the reference
		if (externalFile == null || !externalFile.exists()) {
			String pathDescription = (externalFile != null) ? externalFile.getAbsolutePath() : relativePath;
			throw new FileNotFoundException(
					String.format("Critical application resource is missing: '%s'.", pathDescription)
			);
		}

		return externalFile;
	}

	/**
	 * Locates, validates, and verifies a configuration file inside the 'config' directory.
	 * 
	 * @param fileName the name of the file inside the configuration directory
	 * @return the verified File object
	 * @throws FileNotFoundException if the 'config' directory or the file itself is missing or invalid
	 */
	public static File locateConfigFile(String fileName) throws FileNotFoundException {
		File externalFile;
		try {
			File configDir = locate("config");
			externalFile = new File(configDir, fileName);
		} catch (FileNotFoundException e) {
			throw new FileNotFoundException(
					"Critical configuration directory 'config' is missing. " +
					"The application cannot start without a valid environment setup. " +
					"Please ensure this directory exists in the root path."
			);
		}

		if (!externalFile.exists() || !externalFile.isFile()) {
			String pathDescription = externalFile.getAbsolutePath();
			throw new FileNotFoundException(
					String.format("Critical configuration file is missing: '%s'. " +
							"Application cannot start without a valid configuration. " +
							"Please create it based on the '%s.dist' template.", 
							pathDescription, pathDescription)
			);
		}

		return externalFile;
	}
}
