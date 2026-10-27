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
 * Helper DAO class for interaction with the database.
 */
@Singleton 
public class GSP_DAO {

    private static final Logger logger = LoggerFactory.getLogger(GSP_DAO.class);
    private final SessionFactory sessionFactory;

    @Inject
    public GSP_DAO(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;}
	
    /**
     * Save or update RepairRequests_Entity entities in the database using batch processing.
     *
     * @param listOfObj the list of entities to be synchronized with the database
     */
    public void saveOrUpdateBatch(List<GSP_Entity> listOfObj) {
        if (listOfObj == null || listOfObj.isEmpty()) {
            return;
        }

        Transaction tr = null;
        
        try (Session session = sessionFactory.openSession()) {
            tr = session.beginTransaction();

            int counter = 0;
            for (GSP_Entity entity : listOfObj) {
                session.merge(entity);

                /**
                 * Periodically flush and clear the first-level session cache to prevent 
                 * memory overflow during large batch operations.
                 */
                if (++counter % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            tr.commit();
            
            System.out.printf("The batch of %d RepairRequests entities has been successfully saved/updated in MS SQL.\n", 
            		          listOfObj.size());
            logger.info("The batch of {} RepairRequests entities has been successfully saved/updated in MS SQL.", listOfObj.size());
        } catch (Exception ex) {
            if (tr != null && tr.isActive()) {
                tr.rollback();
            }
            logger.error("Error during batch saving of RepairRequests to the database: ", ex);
            throw ex;
        }
    }
}

