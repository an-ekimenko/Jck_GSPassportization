package gsp.hibernate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import gsp.model.GSP_Entity;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import jakarta.inject.Singleton;

/**
 * Data Access Object (DAO) utility for handling database operations via Hibernate.
 */
@Singleton 
public class GSP_DAO {

    private static final Logger logger = LoggerFactory.getLogger(GSP_DAO.class);
    private final SessionFactory sessionFactory;

    @Inject
    public GSP_DAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
    /**
     * Saves or updates GSP_Entity entities in the database using batch processing.
     *
     * @param entities the list of entities to be synchronized with the database
     */
    public void saveOrUpdateBatch(List<GSP_Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        Transaction transaction = null;
        
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();

            int counter = 0;
            for (GSP_Entity entity : entities) {
                session.merge(entity);

                // Flush and clear the session cache periodically to prevent memory issues during batching
                if (++counter % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            transaction.commit();
            
            System.out.printf("The batch of %d entities has been successfully saved/updated in MS SQL.\n", 
            		          entities.size());
            logger.info("The batch of {} entities has been successfully saved/updated in MS SQL.", entities.size());
        } catch (Exception ex) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            logger.error("Error during batch saving of entities to the database: ", ex);
            throw ex;
        }
    }
}

