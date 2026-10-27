package gsp;

import com.google.inject.Guice;
import com.google.inject.Injector;

import gsp.util.LogInitializer;

public class MainLauncher {

	public static void main(String[] args) {

		// Configure the environment and logging ONCE per application launch.
        LogInitializer.init(); 
        
        // Launching the DI container. 
        Injector injector = Guice.createInjector(new GSPAppModule());
        
        try {
            GSP_App app = injector.getInstance(GSP_App.class);
            
            // Execute the linear data synchronization pipeline
            app.run();
            
            // Clean exit: inform the OS/scheduler that the execution completed successfully
            System.out.println("[INFO] Application lifecycle finished. Exiting process with code 0...");
            System.exit(0);
            
        } catch (Exception e) {
            // Emergency exit: inform the OS/scheduler that the execution failed catastrophically
            System.err.println("[FATAL] Catastrophic failure during synchronization execution path.");
            e.printStackTrace();
            System.exit(1);
        }
	}

}
