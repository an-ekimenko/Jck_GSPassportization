package gsp;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.io.File;
import java.io.FileNotFoundException;

import gsp.config.ConfigPathResolver;
import gsp.config.GSPApiConfig;
import gsp.config.YAMLConfigLoader;
import gsp.jackson_json.GSP_DataDeserializer;
import gsp.model.GSP_Data;
import gsp.model.GSP_Entity;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;


/**
 * Guice module responsible for configuring core application infrastructure dependencies.
 */
public class GSPAppModule extends AbstractModule {
	
	private static final Logger logger = LogManager.getLogger(GSPAppModule.class);

	@Provides 
	@Singleton
	public GSPApiConfig provideConfig() {
		// Load application properties from the external YAML file at startup
		return YAMLConfigLoader.load("properties.yaml");
	}
	
	@Provides 
	@Singleton
	public ObjectMapper provideObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
		// Register the custom deserializer for GSP_Data mapping
		SimpleModule module = new SimpleModule();
		module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
		mapper.registerModule(module);

		return mapper;
	}

	@Provides 
	@Singleton
	public HttpClientBuilder provideHttpClientBuilder() {
		// Configure global request settings including connection and response timeouts
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(Timeout.ofSeconds(30))
				.setResponseTimeout(Timeout.ofSeconds(30))
				.build();

		// Configure retry mechanism for transient network failures
		DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(
				5, 
				TimeValue.ofSeconds(30)
		);

		// Return the builder instance; SSL components will be attached dynamically by GSP_HttpClient
		return HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.setRetryStrategy(retryStrategy);
	}

	@Provides 
	@Singleton
	public SessionFactory provideSessionFactory() throws FileNotFoundException {
		logger.info("Initializing Hibernate SessionFactory infrastructure...");
		StandardServiceRegistryBuilder registryBuilder = null;
		StandardServiceRegistry serviceRegistry = null;
	    
		try {
			// Dynamically locate the external configuration file
			File externalFile = ConfigPathResolver.locateConfigFile("Hibernate.cfg.xml");
	        
			Configuration configuration = new Configuration();
			configuration.configure(externalFile);
	        
			logger.info("Registering annotated domain entity models dynamically...");
			configuration.addAnnotatedClass(GSP_Entity.class);
	        
			logger.info("Building production ServiceRegistry container...");
			registryBuilder = new StandardServiceRegistryBuilder()
					.applySettings(configuration.getProperties());
			serviceRegistry = registryBuilder.build();

			logger.info("MS SQL metadata loaded. Building SessionFactory connections pool...");
			return configuration.buildSessionFactory(serviceRegistry);

		} catch (Exception e) {
			logger.fatal("Database sub-system initialization failed due to configuration error.", e);
			
			// Gracefully destroy the service registry on failure to prevent resource and descriptor leaks
			if (serviceRegistry != null) {
				StandardServiceRegistryBuilder.destroy(serviceRegistry);
			}
			
			throw new RuntimeException("Fatal error during Hibernate architecture startup", e);
		}
	}

}

