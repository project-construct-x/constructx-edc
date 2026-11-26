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
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlVaultTest {

    private static final String DATASOURCE = "vault-ds";

    private final DataSourceRegistry dataSourceRegistry = mock();
    private final DataSource dataSource = mock();
    private final Connection connection = mock();
    private final QueryExecutor queryExecutor = mock();
    private final Monitor monitor = mock(Monitor.class, RETURNS_SELF);

    private SqlVault vault;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSourceRegistry.resolve(DATASOURCE)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        vault = new SqlVault(dataSourceRegistry, DATASOURCE, new NoopTransactionContext(), new ObjectMapper(),
                queryExecutor, monitor);
    }

    @Test
    void resolveSecret_returnsStoredValue() throws SQLException {
        when(queryExecutor.single(eq(connection), eq(false), any(), contains("SELECT secret FROM sql_vault"), eq("foo")))
                .thenReturn("bar");

        assertThat(vault.resolveSecret("foo")).isEqualTo("bar");
        verify(connection).close();
    }

    @Test
    void resolveSecret_returnsNullWhenNotFound() {
        when(queryExecutor.single(any(), eq(false), any(), anyString(), eq("unknown"))).thenReturn(null);

        assertThat(vault.resolveSecret("unknown")).isNull();
    }

    @Test
    void resolveSecret_returnsNullAndLogsOnError() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

        assertThat(vault.resolveSecret("foo")).isNull();
        verify(monitor).severe(contains("foo"), isA(SQLException.class));
    }

    @Test
    void storeSecret_upsertsKeyAndValue() throws SQLException {
        when(queryExecutor.execute(eq(connection), contains("ON CONFLICT (id) DO UPDATE"), eq("foo"), eq("bar")))
                .thenReturn(1);

        assertThat(vault.storeSecret("foo", "bar")).isSucceeded();
        verify(connection).close();
    }

    @Test
    void storeSecret_failsWhenNoRowWasChanged() {
        when(queryExecutor.execute(any(), anyString(), any(Object[].class))).thenReturn(0);

        assertThat(vault.storeSecret("foo", "bar")).isFailed();
    }

    @Test
    void storeSecret_failsOnDatabaseError() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

        assertThat(vault.storeSecret("foo", "bar")).isFailed().detail().contains("db down");
    }

    @Test
    void deleteSecret_deletesKey() {
        when(queryExecutor.execute(eq(connection), contains("DELETE FROM sql_vault"), eq("foo"))).thenReturn(1);

        assertThat(vault.deleteSecret("foo")).isSucceeded();
    }

    @Test
    void deleteSecret_failsWhenKeyDoesNotExist() {
        when(queryExecutor.execute(eq(connection), contains("DELETE FROM sql_vault"), eq("unknown"))).thenReturn(0);

        assertThat(vault.deleteSecret("unknown")).isFailed();
    }

    @Test
    void partitionedMethods_delegateToDefaultMethods() {
        when(queryExecutor.execute(eq(connection), contains("ON CONFLICT"), eq("foo"), eq("bar"))).thenReturn(1);
        when(queryExecutor.single(eq(connection), eq(false), any(), anyString(), eq("foo"))).thenReturn("bar");

        assertThat(vault.storeSecret("some-partition", "foo", "bar")).isSucceeded();
        assertThat(vault.resolveSecret("some-partition", "foo")).isEqualTo("bar");
    }
}
