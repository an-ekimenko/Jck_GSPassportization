package gsp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import gsp.hibernate.GSP_DAO;
import gsp.model.GSP_Data;
import gsp.model.GSP_Entity;
import gsp.network.GSP_HttpClient;

@Singleton
public class GSP_App {

	private static final Logger logger = LogManager.getLogger();

    private final GSP_HttpClient httpClient;
    private final GSP_DAO gspDao;
    private final ObjectMapper objectMapper;

    // Guice will automatically collect and integrate all dependencies here.
    @Inject
    public GSP_App(GSP_HttpClient httpClient, GSP_DAO gspDao, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.gspDao = gspDao;
        this.objectMapper = objectMapper;
    }
    
    /**
     * The main linear data processing pipeline: 
     * Request -> Deserialization -> Stream transformation of DTO into Entity -> Saving in batches to MS SQL.
     */
    public void run() {
        logger.info("Starting Gas Station Pasportization synchronization job...");
        
        try {
            // Downloading raw JSON via the network transport layer
            String jsonPayload = httpClient.fetchRawJson();
            if (jsonPayload == null || jsonPayload.isBlank()) {
                throw new IllegalStateException("Received empty payload from API endpoint.");
            }

            // Custom deserialization of top-level JSON array using Jackson TypeReference
            logger.debug("Parsing JSON array payload into DTO structure...");
            List<GSP_Data> dtoData = objectMapper.readValue(jsonPayload, new TypeReference<List<GSP_Data>>() {});
            
            // Fast exit if payload contains no elements
            if (dtoData == null || dtoData.isEmpty()) {
                logger.warn("No gas station records discovered in the current API response payload.");
                return;
            }

            /**
             * Transform raw data set structures into persistent domain models via stream processing, 
             * leaving only objects with unique IDs in the list.
             */
            logger.debug("Executing data cleansing and stream mapping [Source count: {}]...", dtoData.size());
            
            Set<Integer> seenIds = new HashSet<>();
            
            List<GSP_Entity> gsEntities = dtoData.stream()
                // Filter the list, keeping only those objects whose ID has not been encountered before
                .filter(station -> seenIds.add(station.getId())) 
                .map(station -> new GSP_Entity(
                        station.getId(),
                        "", // Gas station description field defaults to empty string
                        station.getAddress(),
                        station.getFuels(),
                        station.getLatitude(), 
                        station.getLongitude(),
                        station.getDistrict()
                ))
                .collect(Collectors.toList());

            // Check if any unique entities remained after filtering step
            if (gsEntities.isEmpty()) {
                logger.warn("All fetched gas station records were filtered out as duplicates. Database write skipped.");
                return;
            }

            // Execute a transaction-aware batch save/update operation in MS SQL
            logger.info("Flushing {} unique gas station entities to MS SQL staging environment...", gsEntities.size());
            gspDao.saveOrUpdateBatch(gsEntities);
            
            logger.info("Gas Station Pasportization synchronization completed successfully - [{} record(s) processed]!",
            		gsEntities.size());
        } catch (Exception e) {
            logger.error("Critical failure encountered during synchronization execution path", e);
            throw new RuntimeException("Staging environment synchronization job failed", e);
        }
    }
}
