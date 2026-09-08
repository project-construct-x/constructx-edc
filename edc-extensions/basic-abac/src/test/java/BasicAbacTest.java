/*
 * Copyright (c) 2026 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
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

import de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BasicAbacTest {

    // Tests for BasicAbacUtils.BASIC_ABAC_PATTERN

    @ParameterizedTest
    @ValueSource(strings = {
            "https://w3id.org/constructx/credentials/v1.0/Foo.credentialSubject.fooLevel",
            "https://w3id.org/constructx/notcredentials/v1.0/Bar.credentialSubject.nestedObject.barLevel",
            "https://my-domain.com:8080/some/fancy/path/segments/Foo.credentialSubject.fooLevel",

            "https://w3id.org/constructx/policies/v1.0/Foo.credentialSubject.fooLevel",
            "https://example.org/Foo.credentialSubject.fooLevel",

            // Multiple JSONPath segments
            "https://example.org/types/EmployeeCredential.credentialSubject.address.city",
            "https://example.org/types/Employee123Credential.credentialSubject.department.name"
    })
    void shouldMatchValidBasicAbacOperands(String leftOperand) {
        assertTrue(
                BasicAbacUtils.BASIC_ABAC_PATTERN.matcher(leftOperand).matches(),
                () -> "Expected leftOperand to match regex: " + leftOperand
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Only HTTPS is allowed
            "http://example.org/credentials/Foo.credentialSubject.fooLevel",

            // The credential type must start with an uppercase letter
            "https://example.org/credentials/foo.credentialSubject.fooLevel",

            // .credentialSubject is missing
            "https://example.org/credentials/Foo.fooLevel",

            // JSONPath segment after .credentialSubject is missing
            "https://example.org/credentials/Foo.credentialSubject",

            // Empty JSONPath segment
            "https://example.org/credentials/Foo.credentialSubject..fooLevel",

            // Incorrect spelling or capitalization of credentialSubject
            "https://example.org/credentials/Foo.credentialsSubject.fooLevel",
            "https://example.org/credentials/Foo.CredentialSubject.fooLevel",

            // .credentialSubject is not directly after the credential type
            "https://example.org/credentials/Foo.additional.credentialSubject.fooLevel",

            // Trailing dot or slash
            "https://example.org/credentials/Foo.credentialSubject.",
            "https://example.org/credentials/Foo.credentialSubject.fooLevel/",

            // Missing host or top-level domain
            "https://localhost/Foo.credentialSubject.fooLevel",
            "https:///Foo.credentialSubject.fooLevel",

            // Not a complete HTTPS URL
            "Foo.credentialSubject.fooLevel",
            ""
    })
    void shouldNotMatchInvalidBasicAbacOperands(String leftOperand) {
        assertFalse(
                BasicAbacUtils.BASIC_ABAC_PATTERN.matcher(leftOperand).matches(),
                () -> "Expected leftOperand not to match regex: " + leftOperand
        );
    }


    // Tests for BasicAbacUtils.truncateLastPathSegment

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                    "https://w3id.org/constructx/credentials/v1.0/Foo.credentialSubject.fooLevel"
                            + "|https://w3id.org/constructx/credentials/v1.0/Foo",
                    "https://w3id.org/constructx/credentials/v1.0/Bar.credentialSubject.nestedObject.barLevel"
                            + "|https://w3id.org/constructx/credentials/v1.0/Bar",
                    "https://my-domain.com:8080/credentials/Foo.credentialSubject.fooLevel"
                            + "|https://my-domain.com:8080/credentials/Foo",
                    "https://w3id.org/constructx/policies/v1.0/Employee123Credential.credentialSubject.department.name"
                            + "|https://w3id.org/constructx/policies/v1.0/Employee123Credential",
                    "https://example.org/Foo.credentialSubject.fooLevel"
                            + "|https://example.org/Foo"
            }
    )
    void shouldRemoveJsonPathFromLeftExpression(String leftExpression, String expectedCredentialType) {
        String result = BasicAbacUtils.truncateLastPathSegment(leftExpression);

        assertEquals(expectedCredentialType, result);
    }

    @Test
    void shouldNotRemoveDotsFromPreviousPathSegments() {
        String leftExpression =
                "https://example.org/path.with.dots/v1.0/Foo.credentialSubject.value";

        String result = BasicAbacUtils.truncateLastPathSegment(leftExpression);

        assertEquals(
                "https://example.org/path.with.dots/v1.0/Foo",
                result
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // The last path segment does not contain a dot
            "https://example.org/credentials/Foo",

            // The expression does not contain a slash
            "Foo.credentialSubject.fooLevel",

            // The last path segment is empty
            "https://example.org/credentials/",

            // Empty expression
            ""
    })
    void shouldReturnNullForInvalidStringExpression(String leftExpression) {
        String result = BasicAbacUtils.truncateLastPathSegment(leftExpression);

        assertNull(result);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 42 })
    void shouldReturnNullNonStringExpression(Object leftExpression) {
        String result = BasicAbacUtils.truncateLastPathSegment(leftExpression);

        assertNull(result);
    }


    // Tests for BasicAbacUtils.getPathObject

    @Test
    void shouldReturnStringFromCredentialSubject() {
        Map<String, Object> credentialSubject = Map.of(
                "firstName", "Alice"
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.firstName",
                credentialSubject
        );

        assertEquals("Alice", result);
    }

    @Test
    void shouldNavigateThroughNestedObjects() {
        Map<String, Object> credentialSubject = Map.of(
                "address", Map.of(
                        "city", "Berlin",
                        "country", "Germany"
                )
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.address.city",
                credentialSubject
        );

        assertEquals("Berlin", result);
    }

    @Test
    void shouldNormalizeIntegerToDouble() {
        Map<String, Object> credentialSubject = Map.of(
                "accessLevel", 4
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.accessLevel",
                credentialSubject
        );

        assertEquals(4.0, result);
    }

    @Test
    void shouldReturnDoubleValue() {
        Map<String, Object> credentialSubject = Map.of(
                "score", 12.5
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.score",
                credentialSubject
        );

        assertEquals(12.5, result);
    }

    @Test
    void shouldNormalizeNumericStringToDouble() {
        Map<String, Object> credentialSubject = Map.of(
                "accessLevel", "4"
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.accessLevel",
                credentialSubject
        );

        assertEquals(4.0, result);
    }

    @Test
    void shouldReturnBooleanValue() {
        Map<String, Object> credentialSubject = Map.of(
                "active", true
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.active",
                credentialSubject
        );

        assertEquals(true, result);
    }

    @Test
    void shouldReturnNestedMap() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Berlin");
        address.put("postalCode", "10115");

        Map<String, Object> credentialSubject = Map.of(
                "address", address
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.address",
                credentialSubject
        );

        assertSame(address, result);
    }

    @Test
    void shouldReturnAndNormalizeList() {
        Map<String, Object> credentialSubject = Map.of(
                "values", List.of(1, 2.5, "3", true, "text")
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.values",
                credentialSubject
        );

        assertEquals(
                List.of(1.0, 2.5, 3.0, true, "text"),
                result
        );
    }

    @Test
    void shouldAccessListElementByIndex() {
        Map<String, Object> credentialSubject = Map.of(
                "roles", List.of("reader", "editor", "admin")
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.roles[1]",
                credentialSubject
        );

        assertEquals("editor", result);
    }

    @Test
    void shouldNavigateThroughObjectInsideList() {
        Map<String, Object> credentialSubject = Map.of(
                "addresses", List.of(
                        Map.of("city", "Berlin"),
                        Map.of("city", "Munich")
                )
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.addresses[1].city",
                credentialSubject
        );

        assertEquals("Munich", result);
    }

    @Test
    void shouldNavigateThroughMultipleNestedListsAndObjects() {
        Map<String, Object> credentialSubject = Map.of(
                "departments", List.of(
                        Map.of(
                                "name", "Engineering",
                                "employees", List.of(
                                        Map.of("name", "Alice", "accessLevel", 3),
                                        Map.of("name", "Bob", "accessLevel", 4)
                                )
                        )
                )
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential"
                        + ".credentialSubject.departments[0].employees[1].accessLevel",
                credentialSubject
        );

        assertEquals(4.0, result);
    }

    @Test
    void shouldReturnNullWhenFieldDoesNotExist() {
        Map<String, Object> credentialSubject = Map.of(
                "firstName", "Alice"
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.lastName",
                credentialSubject
        );

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenNestedFieldDoesNotExist() {
        Map<String, Object> credentialSubject = Map.of(
                "address", Map.of(
                        "city", "Berlin"
                )
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.address.country",
                credentialSubject
        );

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenListIndexIsOutOfBounds() {
        Map<String, Object> credentialSubject = Map.of(
                "roles", List.of("reader", "editor")
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.roles[5]",
                credentialSubject
        );

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenListIndexIsNegative() {
        Map<String, Object> credentialSubject = Map.of(
                "roles", List.of("reader", "editor")
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.roles[-1]",
                credentialSubject
        );

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenIndexedValueIsNotAList() {
        Map<String, Object> credentialSubject = Map.of(
                "role", "admin"
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.role[0]",
                credentialSubject
        );

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenIntermediateValueIsNotAMap() {
        Map<String, Object> credentialSubject = Map.of(
                "address", "Berlin"
        );

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.address.city",
                credentialSubject
        );

        assertNull(result);
    }

    @Test
    void shouldReturnNullForEmptyCredentialSubject() {
        Map<String, Object> credentialSubject = Map.of();

        Object result = BasicAbacUtils.getPathObject(
                "https://example.org/EmployeeCredential.credentialSubject.firstName",
                credentialSubject
        );

        assertNull(result);
    }




    // Tests for BasicAbacUtils.convertJsonToList

    @Test
    void shouldConvertEmptyJsonArrayToEmptyList() {
        List<?> result = BasicAbacUtils.convertJsonToList("[]");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldConvertJsonArrayContainingStrings() {
        List<?> result = BasicAbacUtils.convertJsonToList(
                "[\"reader\", \"editor\", \"admin\"]"
        );

        assertEquals(
                List.of("reader", "editor", "admin"),
                result
        );
    }

    @Test
    void shouldConvertJsonNumbersToDoubles() {
        List<?> result = BasicAbacUtils.convertJsonToList(
                "[1, 2.5, -3, 0]"
        );

        assertEquals(
                List.of(1.0, 2.5, -3.0, 0.0),
                result
        );
    }

    @Test
    void shouldConvertJsonBooleans() {
        List<?> result = BasicAbacUtils.convertJsonToList(
                "[true, false]"
        );

        assertEquals(
                List.of(true, false),
                result
        );
    }

    @Test
    void shouldPreserveJsonNullValues() {
        List<Object> expected = new ArrayList<>();
        expected.add("first");
        expected.add(null);
        expected.add("third");

        List<?> result = BasicAbacUtils.convertJsonToList(
                "[\"first\", null, \"third\"]"
        );

        assertEquals(expected, result);
    }

    @Test
    void shouldConvertArrayContainingDifferentJsonTypes() {
        List<Object> expected = new ArrayList<>();
        expected.add("text");
        expected.add(42.0);
        expected.add(true);
        expected.add(null);

        List<?> result = BasicAbacUtils.convertJsonToList(
                "[\"text\", 42, true, null]"
        );

        assertEquals(expected, result);
    }

    @Test
    void shouldConvertNestedJsonArrays() {
        List<?> result = BasicAbacUtils.convertJsonToList(
                "[[1, 2], [3, 4], []]"
        );

        assertEquals(
                List.of(
                        List.of(1.0, 2.0),
                        List.of(3.0, 4.0),
                        List.of()
                ),
                result
        );
    }

    @Test
    void shouldConvertJsonObjectsInsideArrayToMaps() {
        List<?> result = BasicAbacUtils.convertJsonToList(
                """
                [
                  {
                    "name": "Alice",
                    "accessLevel": 4,
                    "active": true
                  },
                  {
                    "name": "Bob",
                    "accessLevel": 3,
                    "active": false
                  }
                ]
                """
        );

        assertEquals(
                List.of(
                        Map.of(
                                "name", "Alice",
                                "accessLevel", 4.0,
                                "active", true
                        ),
                        Map.of(
                                "name", "Bob",
                                "accessLevel", 3.0,
                                "active", false
                        )
                ),
                result
        );
    }

    @Test
    void shouldConvertNestedObjectsAndArrays() {
        String json = """
        [
          {
            "department": {
              "name": "Engineering",
              "employees": [
                {
                  "name": "Alice",
                  "accessLevel": 3
                },
                {
                  "name": "Bob",
                  "accessLevel": 4
                }
              ]
            }
          }
        ]
        """;

        Map<String, Object> alice = new LinkedHashMap<>();
        alice.put("name", "Alice");
        alice.put("accessLevel", 3.0);

        Map<String, Object> bob = new LinkedHashMap<>();
        bob.put("name", "Bob");
        bob.put("accessLevel", 4.0);

        Map<String, Object> department = new LinkedHashMap<>();
        department.put("name", "Engineering");
        department.put("employees", List.of(alice, bob));

        Map<String, Object> rootObject = new LinkedHashMap<>();
        rootObject.put("department", department);

        List<?> result = BasicAbacUtils.convertJsonToList(json);

        assertEquals(List.of(rootObject), result);
    }

    @Test
    void shouldPreserveNullValuesInsideJsonObjects() {
        String json = """
        [
          {
            "name": "Alice",
            "optionalValue": null
          }
        ]
        """;

        Map<String, Object> expectedObject = new LinkedHashMap<>();
        expectedObject.put("name", "Alice");
        expectedObject.put("optionalValue", null);

        List<?> result = BasicAbacUtils.convertJsonToList(json);

        assertEquals(List.of(expectedObject), result);
    }

    @Test
    void shouldConvertEscapedAndUnicodeStrings() {
        List<?> result = BasicAbacUtils.convertJsonToList(
                "[\"Hello\\\\World\", \"Line 1\\nLine 2\", \"München\"]"
        );

        assertEquals(
                List.of(
                        "Hello\\World",
                        "Line 1\nLine 2",
                        "München"
                ),
                result
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // A JSON object is not a list
            "{}",
            "{\"name\":\"Alice\"}",

            // Primitive JSON values are not lists
            "\"text\"",
            "42",
            "true",
            "null",

            // Empty or malformed JSON
            "",
            "not-json",
            "[1, 2",
            "[1,,2]",
            "{invalid}"
    })
    void shouldReturnNullWhenInputIsNotAValidJsonArray(String jsonString) {
        List<?> result = BasicAbacUtils.convertJsonToList(jsonString);

        assertNull(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            " ",
            "\t",
            "\n"
    })
    void shouldReturnNullForNullOrBlankInput(String jsonString) {
        List<?> result = BasicAbacUtils.convertJsonToList(jsonString);

        assertNull(result);
    }
}
