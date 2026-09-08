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

package de.fraunhofer.isst.edc.extension.dynamic_issuers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DynamicTrustedIssuerRegistry implements TrustedIssuerRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Set<String>> STRING_SET_TYPEREFERENCE = new TypeReference<>() {
    };
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dynamic-trusted-issuer-worker");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, Set<String>> store = new HashMap<>();
    private final Set<String> externalIssuers = new HashSet<>();
    private final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private final Monitor monitor;
    private final URI trustedIssuerServer;
    private final long defaultInterval;
    private boolean completedInitialCall = false;

    public DynamicTrustedIssuerRegistry(URI trustedIssuerServer, Monitor monitor, long defaultInterval) {
        this.defaultInterval = defaultInterval;
        this.monitor = monitor.withPrefix(this.getClass().getSimpleName());
        this.trustedIssuerServer = trustedIssuerServer;
        if (trustedIssuerServer != null) {
            SCHEDULER.schedule(this::fetchUpdateFromServer, 0, TimeUnit.SECONDS);
        } else {
            monitor.warning("Trust Server URL is null, no updates will be fetched.");
        }

    }

    @Override
    public void register(Issuer issuer, String credentialType) {
        try {
            LOCK.writeLock().lock();
            monitor.debug("Registering " + issuer.id());
            store.computeIfAbsent(issuer.id(), k -> new HashSet<>()).add(credentialType);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    @Override
    public Set<String> getSupportedTypes(Issuer issuer) {
        try {
            LOCK.readLock().lock();
            monitor.debug("Providing supported types request for " + issuer.id());
            return store.getOrDefault(issuer.id(), Set.of());
        } finally {
            LOCK.readLock().unlock();
        }
    }

    private void fetchUpdateFromServer() {
        long newInterval = completedInitialCall ? defaultInterval : 30;
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(trustedIssuerServer).GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode responseJson = MAPPER.readTree(response.body());
                monitor.debug("Got response from trusted issuer server: \n" + responseJson.toPrettyString());
                try {
                    newInterval = responseJson.get("interval").asLong();
                } catch (Exception e) {
                }
                JsonNode issuerData = responseJson.get("issuerdata");
                handleUpdate(issuerData);
                completedInitialCall = true;
            } else {
                monitor.warning("Unexpected response status from trusted issuer server " + response.statusCode());
            }

        } catch (java.net.ConnectException ce) {
            monitor.warning("Failed to reach trusted issuer server " + trustedIssuerServer);
        } catch (Exception e) {
            monitor.warning("Unexpected error while trying to reach trusted issuer server", e);
        } finally {
            SCHEDULER.schedule(this::fetchUpdateFromServer, newInterval, TimeUnit.SECONDS);
            monitor.debug("Next update scheduled in " + newInterval + " seconds");
        }
    }

    private void handleUpdate(JsonNode update) {
        try {
            LOCK.writeLock().lock();
            Set<String> foundIssuers = new HashSet<>();
            if (update.isArray()) {
                for (var item : update) {
                    try {
                        String id = item.get("id").asText();
                        Set<String> types = MAPPER.convertValue(item.get("supportedTypes"), STRING_SET_TYPEREFERENCE);
                        foundIssuers.add(id);
                        externalIssuers.add(id);
                        store.put(id, types);

                    } catch (Exception e) {
                        monitor.warning("Failure while handling array item: " + item.toPrettyString(), e);
                    }
                }
                Set<String> delta = new HashSet<>(externalIssuers);
                delta.removeAll(foundIssuers);
                delta.forEach(id -> {
                    monitor.warning("Removing trusted issuer " + id);
                    store.remove(id);
                });
                externalIssuers.removeAll(delta);
            } else {
                monitor.warning("Payload from trusted issuer server is not an array!");
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }


}
