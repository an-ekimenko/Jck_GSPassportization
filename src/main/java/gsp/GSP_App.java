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

/**
 * Main application orchestrator coordinating the data pipeline.
 */
@Singleton
public class GSP_App {

	private static final Logger logger = LogManager.getLogger(GSP_App.class);

	private final GSP_HttpClient httpClient;
	private final GSP_DAO gspDao;
	private final ObjectMapper objectMapper;

	@Inject
	public GSP_App(GSP_HttpClient httpClient, GSP_DAO gspDao, ObjectMapper objectMapper) {
		this.httpClient = httpClient;
		this.gspDao = gspDao;
		this.objectMapper = objectMapper;
	}
    
	/**
	 * Executes the data synchronization job: fetch, deserialize, deduplicate, and persist.
	 */
	public void run() {
		logger.info("Starting Gas Station Pasportization synchronization job...");
        
		try {
			// Fetch raw JSON payload from the API endpoint
			String jsonPayload = httpClient.fetchRawJson();
			if (jsonPayload == null || jsonPayload.isBlank()) {
				throw new IllegalStateException("Received empty payload from API endpoint.");
			}

			// Deserialize JSON array into DTO objects
			logger.debug("Parsing JSON array payload into DTO structure...");
			List<GSP_Data> dtoData = objectMapper.readValue(jsonPayload, new TypeReference<List<GSP_Data>>() {});
            
			// Exit early if the response payload contains no records
			if (dtoData == null || dtoData.isEmpty()) {
				logger.warn("No gas station records discovered in the current API response payload.");
				return;
			}

			// Transform DTOs into persistent entities and deduplicate by ID via stream processing
			logger.debug("Executing data filtering and mapping [Source count: {}]...", dtoData.size());
            
			Set<Integer> seenIds = new HashSet<>();
            
			List<GSP_Entity> gsEntities = dtoData.stream()
				.filter(station -> seenIds.add(station.getId())) 
				.map(station -> new GSP_Entity(
						station.getId(),
						"", // Gas station description field defaults to an empty string
						station.getAddress(),
						station.getFuels(),
						station.getLatitude(), 
						station.getLongitude(),
						station.getDistrict()
				))
				.collect(Collectors.toList());

			// Skip database operations if no unique entities remain after filtering
			if (gsEntities.isEmpty()) {
				logger.warn("All fetched gas station records were filtered out as duplicates. Database write skipped.");
				return;
			}

			// Persist unique records to the MS SQL staging database using batch operations
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
