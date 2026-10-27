package gsp;

import java.io.File;
import java.io.FileNotFoundException;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;

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

import gsp.config.GSPApiConfig;
import gsp.config.YAMLConfigLoader;
import gsp.jackson_json.GSP_DataDeserializer;
import gsp.model.GSP_Data;
import gsp.model.GSP_Entity;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class GSPAppModule extends AbstractModule {
	
	private static final Logger logger = LogManager.getLogger();

	@Provides @Singleton
	public GSPApiConfig provideConfig() {
		// Load application properties from the external YAML file at startup
		return YAMLConfigLoader.load("properties.yaml");
	}
	
	@Provides @Singleton
	public ObjectMapper provideObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
		// Register the custom deserializer for GSP_Data mapping
		SimpleModule module = new SimpleModule();
		module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
		mapper.registerModule(module);

		return mapper;
	}

	@Provides @Singleton
	public CloseableHttpClient provideHttpClient(GSPApiConfig config) {
		try {
			// Obtain the default SSLContext instance
			SSLContext sslContext = SSLContext.getDefault();
			
			// Configure connection manager with the custom SSL socket factory
			SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
			PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
					.setSSLSocketFactory(sslSocketFactory)
					.build();

			// Set uniform connect and response timeouts to 30 seconds
			RequestConfig requestConfig = RequestConfig.custom()
					.setConnectTimeout(Timeout.ofSeconds(30))
					.setResponseTimeout(Timeout.ofSeconds(30))
					.build();

			// Configure retry policy with 5 attempts and a 30-second delay interval
			DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(
					5, 
					TimeValue.ofSeconds(30)
			);

			// Initialize and return the fully configured Apache HttpClient instance
			return HttpClients.custom()
					.setConnectionManager(connManager)
					.setDefaultRequestConfig(requestConfig)
					.setRetryStrategy(retryStrategy)
					.build(); 
			
		} catch (Exception e) {
			logger.fatal("Failed to build a resilient Apache HttpClient instance", e);
			throw new RuntimeException("Critical error initializing HttpClient", e);
		}
	}
	
	@Provides @Singleton
	public SessionFactory provideSessionFactory() throws FileNotFoundException {
		logger.info("Initializing Hibernate SessionFactory infrastructure...");

		File hibernateFile = new File("config" + File.separator + "Hibernate.cfg.xml");
	    
	    try {
	        if (!hibernateFile.exists() || !hibernateFile.isFile()) {
	            throw new FileNotFoundException("Critical database configuration file is missing: " + 
	            		hibernateFile.getAbsolutePath());
	        }

	        Configuration configuration = new Configuration();
	        configuration.configure(hibernateFile);
	        
	        logger.info("Registering annotated domain entity models dynamically...");
	        configuration.addAnnotatedClass(GSP_Entity.class);
	        
	        logger.info("MS SQL metadata loaded. Building SessionFactory connections pool...");
	        return configuration.buildSessionFactory();

	    } catch (Exception e) {
	        logger.fatal("Database sub-system initialization failed catastrophically.", e);
	        throw new RuntimeException("Fatal error during Hibernate architecture startup", e);
	    }
	}
}
