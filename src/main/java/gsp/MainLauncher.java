package gsp;

import com.google.inject.Guice;
import com.google.inject.Injector;
import gsp.util.LogInitializer;

/**
 * Main application bootstrapper responsible for setting up execution context and launching the pipeline.
 */
public class MainLauncher {

	public static void main(String[] args) {
		// Configure environment paths and initialize loggers before any other operations
		LogInitializer.init(); 
        
		// Initialize the Guice dependency injection container
		Injector injector = Guice.createInjector(new GSPAppModule());
        
		try {
			GSP_App app = injector.getInstance(GSP_App.class);
            
			// Execute the main data synchronization pipeline
			app.run();
            
			// Notify the OS scheduler of successful execution completion
			System.out.println("[INFO] Application lifecycle finished. Exiting process with code 0...");
			System.exit(0);
            
		} catch (Exception e) {
			// Notify the OS scheduler of process failure due to unexpected error
			System.err.println("[FATAL] Unhandled error during application execution path.");
			e.printStackTrace();
			System.exit(1);
		}
	}
}
