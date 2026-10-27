package gsp.jackson_json;

import java.io.IOException;
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

import java.util.regex.Matcher; 
import gsp.model.GSP_Data;

/**
• Jackson JSON deserializer for {@link GSP_Data} objects.
*/
public class GSP_DataDeserializer extends StdDeserializer<GSP_Data> {
	
	private static final Logger logger = LoggerFactory.getLogger(GSP_DataDeserializer.class);
	
	// The pattern correctly processes: integers, numbers with a decimal point, and numbers with a comma.
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("-?\\d+([.,]\\d+)?");
	
	public GSP_DataDeserializer() {
		super(GSP_Data.class);
	}
	
	/**
	• Required constructor for base class compatibility and Jackson internal mechanism.
	• @param vc the target class type
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
		
		// Extract values using Jackson’s typed methods, if possible.
        JsonNode complexNode = tree.path("complex");
        if (complexNode.isNumber()) {
            rrData.setId(complexNode.asInt());
        } else {
            rrData.setId(parseIntSafe(complexNode.asText()));
        }
		
        // Safe parsing of coordinates (with support for various separator formats)
        rrData.setLatitude(parseDoubleSafe(tree.path("latitude").asText()));
        rrData.setLongitude(parseDoubleSafe(tree.path("longitude").asText()));		
		
		// Fallback to an empty string if fields are missing or empty
		rrData.setAddress(tree.path("mailaddress").asText());
		rrData.setDistrict(tree.path("district").asText());
		
		// Handle "fuels" array format by joining elements into a comma-separated string
		if (tree.has("fuels") && tree.get("fuels").isArray()) {
			String fuelsString = StreamSupport
					.stream(tree.get("fuels").spliterator(), false)
					.map(JsonNode::asText)
					//Protection against null elements in an array
					.filter(text -> text != null && !text.isEmpty()) 
					.collect(Collectors.joining(", "));
			rrData.setFuels(fuelsString);
		}
		return rrData;
	}
	
	/**
	• Safely parses an Integer from a raw string.
	• @param value the string to parse
	• @return the parsed Integer, or null if the string is empty or invalid
	*/
	private Integer parseIntSafe(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		
		String trimmed = value.trim();
	    try {
	        // First, try to parse the string “as is.”
	        int parsed = Integer.parseInt(trimmed);
	        // If the number is parsed, but it’s negative, we remove the minus sign
	        return Math.abs(parsed);
	    } catch (NumberFormatException e) {
	        /* 
	         * If there are extra characters in the string, we are looking for the FIRST 
	         * sequence consisting only of NUMBERS (without a minus sign).
	         */
	        Matcher matcher = Pattern.compile("\\d+").matcher(trimmed);
	        if (matcher.find()) {
	            try {
	                return Integer.parseInt(matcher.group());
	            } catch (NumberFormatException ex) {
	                logger.warn("The extracted unsigned integer exceeds Integer range: '{}' in original value: '{}'", 
	                		matcher.group(), value);
	            }
	        } else {
	            logger.warn("Unable to extract a valid positive integer identifier from the string: '{}'", 
	            		value);
	        }
	        return null;
	    }
}
	
	/**
	• Safely parses a Double, replacing commas with dots before final parsing.
	• @param value the string to parse
	• @return the parsed Double, or null if no valid floating-point number is found
	*/
	private Double parseDoubleSafe(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		
		Matcher matcher = DOUBLE_PATTERN.matcher(value.trim());
        if (!matcher.find()) {
            logger.warn("Unable to extract a double (coordinate) from the string: '{}'", value);
            return null;
        }
		
        // Normalize the string (replace the comma with a period for Double.parseDouble).
        String normalizedNumber = matcher.group().replace(',', '.');
        try {
            return Double.parseDouble(normalizedNumber);
        } catch (NumberFormatException e) {
            logger.warn("The extracted number exceeds Double range: '{}' in original value: '{}'", normalizedNumber, value);
            return null;
        }
	}
}