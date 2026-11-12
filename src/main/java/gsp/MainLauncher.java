package gsp;

import com.google.inject.Guice;
import com.google.inject.Injector;
import gsp.util.LogInitializer;
import gsp.util.ResultFileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main application bootstrapper responsible for setting up execution context and launching the pipeline.
 */
public class MainLauncher {

	private static final Logger logger = LogManager.getLogger(MainLauncher.class);

	public static void main(String[] args) {
		// Configure environment paths and initialize loggers before any other operations
		LogInitializer.init();

		try {
			// Initialize the Guice dependency injection container
			Injector injector = Guice.createInjector(new GSPAppModule());
			GSP_App app = injector.getInstance(GSP_App.class);

			// Execute the main data synchronization pipeline and retrieve processed records count
			int recordsProcessed = app.run();

			// Write success summary to exchange/result.yaml
			ResultFileWriter.writeResult(0, recordsProcessed);

			// Notify the OS scheduler of successful execution completion
			logger.info("Application lifecycle finished. Records processed: {}. Exiting process with code 0...", recordsProcessed);
			System.exit(0);

		} catch (Exception e) {
			// Write failure summary to exchange/result.yaml before terminating
			logger.fatal("Unhandled error during application execution path.", e);
			try {
				ResultFileWriter.writeResult(1, 0);
			} catch (Exception writeEx) {
				logger.error("Failed to write failure status to result.yaml", writeEx);
			}

			// Notify the OS scheduler of process failure due to unexpected error
			System.exit(1);
		}
	}
}
