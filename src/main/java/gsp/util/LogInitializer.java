package gsp.util;

import java.io.File;
import java.net.URI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Utility for bootstrapping and initializing custom logging paths for Log4j2 and P6Spy frameworks.
 */
public class LogInitializer {

	private LogInitializer() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Initializes the logging configuration.
	 * Must be called once at the application startup.
	 */
	public static void init() {
		try {
			URI jarUri = LogInitializer.class.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI();
			File currentDir = new File(jarUri).getParentFile();
			
			// Navigate to the project root directory when running from the IDE
			if (currentDir != null && ("classes".equals(currentDir.getName()) || "target".equals(currentDir.getName()))) {
				while (currentDir != null && ("classes".equals(currentDir.getName()) || "target".equals(currentDir.getName()))) {
					currentDir = currentDir.getParentFile();
				}
			}
			
			File logFolder = new File(currentDir, "logs");
			String logDirPath = logFolder.getAbsolutePath();

			configureLog4j2(logDirPath);
			configureP6Spy(logDirPath);
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to initialize custom logging paths, falling back to defaults.");
			e.printStackTrace();
		}
	}

	/**
	 * Configures system properties for Log4j2 and forces a configuration reload.
	 * 
	 * @param logDirPath the absolute path to the logs directory
	 */
	private static void configureLog4j2(String logDirPath) {
		// Set system property for interpolation within log4j2.xml
		System.setProperty("log.dir", logDirPath);
		System.out.println("[INFO] Log4j2 directory is set to: " + logDirPath);
		
		// Force Log4j2 to reload using the updated system property
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.reconfigure();
	}

	/**
	 * Configures system properties to override P6Spy log destinations.
	 * 
	 * @param logDirPath the absolute path to the logs directory
	 */
	private static void configureP6Spy(String logDirPath) {
		// Override spy.properties layout before database initialization
		String p6spyFilePath = logDirPath + File.separator + "p6spy.log";
		System.setProperty("p6spy.config.logfile", p6spyFilePath);
		System.out.println("[INFO] P6Spy log file path is redirected to: " + p6spyFilePath);
	}
}
