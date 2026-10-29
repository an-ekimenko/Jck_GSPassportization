package gsp.util;

import java.lang.invoke.MethodHandles;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gsp.model.GSP_Entity;

/**
 * Utility class for managing the Hibernate SessionFactory singleton instance.
 */
public class HibernateUtil {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    
    // Prevents threads from reading a partially initialized object
    private static volatile SessionFactory sessionFactory = null;
    private static ServiceRegistry serviceRegistry;

    private HibernateUtil() {}
	
    /**
     * Retrieves the thread-safe SessionFactory singleton instance.
     *
     * @return the configured SessionFactory
     */
    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            synchronized (HibernateUtil.class) {
                if (sessionFactory == null) {
                    try {
                        // Load Hibernate configuration file
                        Configuration configuration = new Configuration()
                                .configure(Thread.currentThread().getContextClassLoader()
                                .getResource("Hibernate.cfg.xml"));

                        // Register the annotated entity class
                        configuration.addAnnotatedClass(GSP_Entity.class);
            	
                        // Build the service registry
                        serviceRegistry = new StandardServiceRegistryBuilder()
                                .applySettings(configuration.getProperties())
                                .build();
                        
                        System.out.println("\t<ServiceRegistry>\n\t\t" + serviceRegistry.toString() + "\n\t</ServiceRegistry>");
                        
                        sessionFactory = configuration.buildSessionFactory(serviceRegistry);
                    } catch (Exception e) {
                        logger.error("Failed to initialize Hibernate SessionFactory: ", e);
                        
                        // Destroy the registry on failure to prevent resource leaks
                        if (serviceRegistry != null) {
                            StandardServiceRegistryBuilder.destroy(serviceRegistry);
                        }
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
        }
        return sessionFactory;
    }
}
