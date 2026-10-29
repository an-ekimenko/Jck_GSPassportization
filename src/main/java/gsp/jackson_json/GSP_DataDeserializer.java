package gsp.jackson_json;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import gsp.model.GSP_Data;

/**
 * Jackson JSON deserializer for {@link GSP_Data} objects.
 * Refactored for better performance and clean logging.
 */
public class GSP_DataDeserializer extends StdDeserializer<GSP_Data> {
	
	private static final Logger logger = LoggerFactory.getLogger(GSP_DataDeserializer.class);
	
	// Matches integers and floating-point numbers with dot or comma separators
	private static final Pattern DOUBLE_PATTERN = Pattern.compile("-?\\d+([.,]\\d+)?");
	
	// Matches consecutive sequences of numeric digits
	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
	
	public GSP_DataDeserializer() {
		super(GSP_Data.class);
	}
	
	/**
	 * Required constructor for base class compatibility and Jackson internal mechanics.
	 * 
	 * @param vc the target class type
	 */
	public GSP_DataDeserializer(Class<?> vc) {
		super(vc);
	}

	@Override
	public GSP_Data deserialize(JsonParser jp, DeserializationContext ctxt)
			throws IOException, JsonProcessingException 
	{
		JsonNode tree = jp.getCodec().readTree(jp);
		GSP_Data rrData = new GSP_Data();
		
		// 1. Parse Complex ID
		JsonNode complexNode = tree.path("complex");
		if (complexNode.isNumber()) {
			rrData.setId(Math.abs(complexNode.asInt()));
		} else if (!complexNode.isMissingNode() && !complexNode.isNull()) {
			rrData.setId(parseIntSafe(complexNode.asText()));
		}
		
		// 2. Parse Latitude
		JsonNode latNode = tree.path("latitude");
		if (latNode.isNumber()) {
			rrData.setLatitude(latNode.asDouble());
		} else if (!latNode.isMissingNode() && !latNode.isNull()) {
			rrData.setLatitude(parseDoubleSafe(latNode.asText()));
		}
		
		// 3. Parse Longitude
		JsonNode lonNode = tree.path("longitude");
		if (lonNode.isNumber()) {
			rrData.setLongitude(lonNode.asDouble());
		} else if (!lonNode.isMissingNode() && !lonNode.isNull()) {
			rrData.setLongitude(parseDoubleSafe(lonNode.asText()));
		}		
		
		// 4. Parse Text Fields (Fallback to empty strings if missing or null)
		JsonNode addressNode = tree.path("mailaddress");
		rrData.setAddress(addressNode.isTextual() ? addressNode.asText() : "");
		
		JsonNode districtNode = tree.path("district");
		rrData.setDistrict(districtNode.isTextual() ? districtNode.asText() : "");
		
		// 5. Parse Fuels Array
		JsonNode fuelsNode = tree.path("fuels");
		if (fuelsNode.isArray()) {
			String fuelsString = StreamSupport
					.stream(fuelsNode.spliterator(), false)
					.map(JsonNode::asText)
					.filter(text -> text != null && !text.trim().isEmpty()) 
					.collect(Collectors.joining(", "));
			rrData.setFuels(fuelsString);
		}
		
		return rrData;
	}
	
	/**
	 * Safely parses an Integer from a raw string.
	 * 
	 * @param value the string to parse
	 * @return the parsed absolute Integer, or null if the string is empty or invalid
	 */
	private Integer parseIntSafe(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		
		String trimmed = value.trim();
		try {
			int parsed = Integer.parseInt(trimmed);
			return Math.abs(parsed);
		} catch (NumberFormatException e) {
			Matcher matcher = DIGIT_PATTERN.matcher(trimmed);
			if (matcher.find()) {
				try {
					return Integer.parseInt(matcher.group());
				} catch (NumberFormatException ex) {
					logger.warn("Extracted unsigned integer exceeds Integer range: '{}' in original value: '{}'", 
							matcher.group(), value);
				}
			} else {
				logger.warn("Unable to extract a valid positive integer identifier from string: '{}'", value);
			}
			return null;
		}
	}
	
	/**
	 * Safely parses a Double, replacing commas with dots before final parsing.
	 * 
	 * @param value the string to parse
	 * @return the parsed Double, or null if no valid floating-point number is found
	 */
	private Double parseDoubleSafe(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		
		Matcher matcher = DOUBLE_PATTERN.matcher(value.trim());
		if (!matcher.find()) {
			logger.warn("Unable to extract a double (coordinate) from string: '{}'", value);
			return null;
		}
		
		String normalizedNumber = matcher.group().replace(',', '.');
		try {
			return Double.parseDouble(normalizedNumber);
		} catch (NumberFormatException e) {
			logger.warn("Extracted number exceeds Double range: '{}' in original value: '{}'", normalizedNumber, value);
			return null;
		}
	}
}
