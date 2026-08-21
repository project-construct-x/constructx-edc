/*
 * Copyright (c) 2026 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package de.fraunhofer.isst.edc.extension.basic_abac.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BasicAbacUtils {

    public static final String BASIC_ABAC_REGEX =
            "^(?=.*/credentials)https://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?::[0-9]+)?/.*/[A-Z][^\\s/.]*(?:\\.[^\\s/.]+)+$";
    public static final Pattern BASIC_ABAC_PATTERN = Pattern.compile(BASIC_ABAC_REGEX);

    public static final String CONX_MEMBERSHIP_SCOPE = "org.eclipse.dspace.dcp.vc.type:https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential:read";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * This method takes as input
     * <p>
     * - the leftValue Parameter of the evaluate method from the DynamicAtomicConstraintRuleFunction interface.
     * <p>
     * - the credential subject of a verifiable credential (as processed by the edc framework, i.e. as a java.util.Map)
     * <p>
     * The leftValue is expected to have matched the BASIC_ABAC_REGEX, containing a pointer suffix at the end.
     * That suffix will be interpreted by this method to navigate through the credential subject (also see the readme).
     * If the found value is numeric, then -for the sake of normalization- it will be converted to a Double value.
     *
     * @param leftValue
     * @param map
     * @return the object (which might be a string, a list, a map or a Double) or null if nothing could be found
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
            if (!fieldName.isBlank() && current instanceof Map<?, ?> nestedMap) {
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
        if (current instanceof List<?> foundList) {
            return normalizeNumericToDoubleOrBoolean(foundList);
        }

        try {
            return Double.parseDouble(current.toString());
        } catch (Exception e) {
            return current;
        }

    }

    /**
     * This method expects the leftValue Parameter of the evaluate method from the DynamicAtomicConstraintRuleFunction
     * interface as input. It is also expected that the left expression was matched by the BASIC_ABAC_REGEX.
     * <p>
     * It will remove the pointer suffix, effectively returning the fully qualified name of the credential.
     *
     * @param leftExpression
     * @return the fully qualified name of the credential
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

    /**
     * Convert the string representation of a JSON list into a Java list.
     *
     * @param jsonString the string representation of a JSON list
     * @return a (Java) list of objects (of unspecified types)
     */
    public static @Nullable List<?> convertJsonToList(String jsonString) {
        try {
            var parsedJson = MAPPER.readTree(jsonString);
            if (parsedJson.isArray()) {
                return (List<?>) convert(parsedJson);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object convert(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return convertObject((ObjectNode) node);
        }
        if (node.isArray()) {
            return convertArray((ArrayNode) node);
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        return node.asText();
    }

    private static Map<String, Object> convertObject(ObjectNode objNode) {
        Map<String, Object> map = new LinkedHashMap<>();
        objNode.propertyStream().forEach(entry -> {
            map.put(entry.getKey(), convert(entry.getValue()));
        });
        return map;
    }

    private static List<Object> convertArray(ArrayNode arrNode) {
        List<Object> list = new ArrayList<>();
        for (JsonNode child : arrNode) {
            list.add(convert(child));
        }
        return list;
    }

    /**
     * Returns a copy of the input list, where contained objects are parsed into
     * Double values, if possible. I.e. Integer, Long or String values
     * (with numeric content) will be converted into Doubles.
     *
     * @param list
     * @return a new list
     */
    public static List<?> normalizeNumericToDoubleOrBoolean(List<?> list) {
        return list.stream()
                .map(it -> {
                    if (it instanceof List<?> nestedList) {
                        return normalizeNumericToDoubleOrBoolean(nestedList);
                    }
                    // Note: Nested maps unsupported, doubtful if it's needed
                    try {
                        return Double.parseDouble(it.toString());
                    } catch (NumberFormatException e) {
                        return normalizeToBoolean(it);
                    }
                }).toList();
    }

    public static Object normalizeToBoolean(Object value) {
        if ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString())) {
            return Boolean.valueOf(value.toString());
        }
        return value;

    }
}
