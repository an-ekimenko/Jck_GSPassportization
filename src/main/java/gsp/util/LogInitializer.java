package gsp.util;

import java.io.File;
import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class LogInitializer {

	private LogInitializer() {
		throw new UnsupportedOperationException("Utility class");
	}
	/**
	• 
	Initializes the logging configuration.
	• 
	Must be called once at the application startup.
	*/
	public static void init() {
		
		try {
			URI jarUri = LogInitializer.class.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI();
			File currentDir = new File(jarUri).getParentFile();
			
			// Navigate to the project root directory if running from the IDE (target/classes)
			if (currentDir != null && (currentDir.getName().equals("classes") || currentDir.getName().equals("target"))) {
				while (currentDir != null && (currentDir.getName().equals("classes") || currentDir.getName().equals("target"))) {
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

	private static void configureLog4j2(String logDirPath) {
		// Set the system property for configuration interpolation within log4j2.xml
		System.setProperty("log.dir", logDirPath);
		System.out.println("[INFO] Log4j2 directory is set to: " + logDirPath);
		
		// Force Log4j2 to reload the configuration using the updated system property
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.reconfigure();
	}

	private static void configureP6Spy(String logDirPath) {
		// Override the spy.properties layout by setting the logpath system property before DB initialization
		String p6spyFilePath = logDirPath + File.separator + "p6spy.log";
		System.setProperty("p6spy.config.logfile", p6spyFilePath);
		System.out.println("[INFO] P6Spy log file path is redirected to: " + p6spyFilePath);
	}
}