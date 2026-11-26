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

package de.fraunhofer.isst.edc.extension.sqlvault.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class SqlVaultExtensionTest {

    private static final String UPSERT = "ON CONFLICT (id) DO UPDATE";

    private final DataSourceRegistry dataSourceRegistry = mock();
    private final DataSource dataSource = mock();
    private final Connection connection = mock();
    private final QueryExecutor queryExecutor = mock();
    private final SqlSchemaBootstrapper schemaBootstrapper = mock();
    private final TypeManager typeManager = mock();
    private final Monitor monitor = mock(Monitor.class, RETURNS_SELF);

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp(ServiceExtensionContext context) throws SQLException {
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());
        when(queryExecutor.execute(any(), anyString(), any(Object[].class))).thenReturn(1);

        context.registerService(DataSourceRegistry.class, dataSourceRegistry);
        context.registerService(TransactionContext.class, new NoopTransactionContext());
        context.registerService(TypeManager.class, typeManager);
        context.registerService(QueryExecutor.class, queryExecutor);
        context.registerService(SqlSchemaBootstrapper.class, schemaBootstrapper);
        when(context.getMonitor()).thenReturn(monitor);
    }

    @Test
    void initialize_registersSchemaForConfiguredDatasource(ServiceExtensionContext context, ObjectFactory factory) {
        withConfig(context, Map.of("edc.sql.store.vault.datasource", "vault-ds"));

        start(context, factory);

        verify(schemaBootstrapper).addStatementFromResource("vault-ds", "sql-vault.sql");
    }

    @Test
    void start_storesInitDataPairs(ServiceExtensionContext context, ObjectFactory factory) {
        withConfig(context, Map.of("edc.sql.store.vault.initdata", "foo:::123;;;bar:::456"));

        start(context, factory);

        verify(queryExecutor).execute(eq(connection), contains(UPSERT), eq("foo"), eq("123"));
        verify(queryExecutor).execute(eq(connection), contains(UPSERT), eq("bar"), eq("456"));
    }

    @Test
    void start_keepsSeparatorInsideValue(ServiceExtensionContext context, ObjectFactory factory) {
        withConfig(context, Map.of("edc.sql.store.vault.initdata", "foo:::a:::b"));

        start(context, factory);

        verify(queryExecutor).execute(eq(connection), contains(UPSERT), eq("foo"), eq("a:::b"));
    }

    @Test
    void start_skipsMalformedPairButStoresTheRest(ServiceExtensionContext context, ObjectFactory factory) {
        withConfig(context, Map.of("edc.sql.store.vault.initdata", "broken;;;foo:::123"));

        start(context, factory);

        verify(queryExecutor).execute(eq(connection), contains(UPSERT), eq("foo"), eq("123"));
        verify(queryExecutor, never()).execute(any(), anyString(), eq("broken"), any());
        verify(monitor).warning(contains("broken"));
    }

    @Test
    void start_logsWarningWhenStoringFails(ServiceExtensionContext context, ObjectFactory factory) {
        when(queryExecutor.execute(any(), anyString(), any(Object[].class))).thenReturn(0);
        withConfig(context, Map.of("edc.sql.store.vault.initdata", "foo:::123"));

        start(context, factory);

        verify(monitor).warning(contains("key foo"));
    }

    @Test
    void start_storesSecretsFromDirectory(ServiceExtensionContext context, ObjectFactory factory) throws Exception {
        Files.writeString(tempDir.resolve("file-secret-one"), "value-one\n");
        Files.writeString(tempDir.resolve("file-secret-two"), "value-two");
        Files.createDirectory(tempDir.resolve("subdir"));
        withConfig(context, Map.of("edc.sql.store.vault.directory", tempDir.toString()));

        start(context, factory);

        verify(queryExecutor).execute(eq(connection), contains(UPSERT), eq("file-secret-one"), eq("value-one"));
        verify(queryExecutor).execute(eq(connection), contains(UPSERT), eq("file-secret-two"), eq("value-two"));
        verify(queryExecutor, never()).execute(any(), anyString(), eq("subdir"), any());
    }

    @Test
    void start_warnsWhenDirectoryIsMissing(ServiceExtensionContext context, ObjectFactory factory) {
        var missing = tempDir.resolve("does-not-exist");
        withConfig(context, Map.of("edc.sql.store.vault.directory", missing.toString()));

        start(context, factory);

        verify(monitor).warning(contains("No init data directory found"));
        verify(queryExecutor, never()).execute(any(), anyString(), any(Object[].class));
    }

    private void withConfig(ServiceExtensionContext context, Map<String, String> settings) {
        var config = new HashMap<>(settings);
        // never fall back to the real default directory of the machine running the tests
        config.putIfAbsent("edc.sql.store.vault.directory", tempDir.resolve("unused").toString());
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));
    }

    private void start(ServiceExtensionContext context, ObjectFactory factory) {
        var extension = factory.constructInstance(SqlVaultExtension.class);
        extension.initialize(context);
        extension.provideSqlVault(context);
        extension.start();
    }
}
