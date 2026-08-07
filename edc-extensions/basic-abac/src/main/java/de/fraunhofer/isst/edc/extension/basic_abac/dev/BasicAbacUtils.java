package de.fraunhofer.isst.edc.extension.basic_abac.dev;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BasicAbacUtils {

    public static final String BASIC_ABAC_REGEX =
            "^(?=.*/credentials)https://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?::[0-9]+)?/.*/[A-Z][^\\s/.]*(?:\\.[^\\s/.]+)+$";
    public static final Pattern BASIC_ABAC_PATTERN = Pattern.compile(BASIC_ABAC_REGEX);

    public static final String CONX_MEMBERSHIP_SCOPE = "org.eclipse.dspace.dcp.vc.type:https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential:read";

    /**
     * This method takes as input
     * <p>
     * - the leftValue Parameter of the evaluate method from the DynamicAtomicConstraintRuleFunction interface.
     * <p>
     * - the credential subject of a verifiable credential (as processed by the edc framework, i.e. as a java.util.Map)
     * <p>
     * The leftValue is expected to have matched the BASIC_ABAC_REGEX, containing a pointer suffix at the end.
     * That suffix will be interpreted by this method to navigate through the credential subject (also see the readme).
     *
     * @param leftValue
     * @param map
     * @return the object (which might be a string, a list, a map) or null if nothing could be found
     */
    public static Object getPathObject(Object leftValue, Map<?, ?> map) {
        String jsonPath = leftValue.toString().replace(truncateLastPathSegment(leftValue) + ".", "");
        if (jsonPath.isBlank()) return null;
        Object current = map;
        String[] segments = jsonPath.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            String fieldName = segments[i].strip();
            Integer arrayIndex = null;
            int bracketStart = fieldName.indexOf("[");
            int bracketEnd = fieldName.indexOf("]");
            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                String indexString = fieldName.substring(bracketStart + 1, bracketEnd);
                fieldName = fieldName.substring(0, bracketStart);
                arrayIndex = Integer.parseInt(indexString);
            }
            if (!fieldName.isBlank() && current instanceof Map<?,?> nestedMap) {
                current = nestedMap.get(fieldName);
            }
            if (current == null) {
                return null;
            }
            if (arrayIndex != null) {
                if (current instanceof List<?> nestedList && arrayIndex >= 0 && arrayIndex < nestedList.size()) {
                    try {
                        current = nestedList.get(arrayIndex);
                    } catch (Exception e) {
                        return null;
                    }
                } else {
                    return null;
                }
            }

        }
        return current;
    }

    /**
     * This method expects the leftValue Parameter of the evaluate method from the DynamicAtomicConstraintRuleFunction
     * interface as input. It is also expected that the left expression was matched by the BASIC_ABAC_REGEX.
     *
     * It will remove the pointer suffix, effectively returning the fully qualified name of the credential.
     *
     * @param leftExpression
     * @return
     */
    public static String truncateLastPathSegment(Object leftExpression) {
        if (leftExpression instanceof String url) {
            int lastSlashIndex = url.lastIndexOf('/');
            if (lastSlashIndex == -1) {
                return url;
            }
            String beforeLastSegment = url.substring(0, lastSlashIndex + 1);
            String lastSegment = url.substring(lastSlashIndex + 1);
            int firstDotIndex = lastSegment.indexOf('.');
            if (firstDotIndex == -1) {
                return url;
            }
            return beforeLastSegment + lastSegment.substring(0, firstDotIndex);
        }
        return null;
    }
}
