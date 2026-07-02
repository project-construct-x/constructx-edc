/*
 * Copyright (c) 2026. Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;


@Extension("Sql Vault Extension")
public class SqlVaultExtension implements ServiceExtension {

    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = "edc.sql.store.vault.datasource")
    private String dataSourceName;
    @Inject
    private DataSourceRegistry dataSourceRegistry;
    @Inject
    private TransactionContext transactionContext;
    @Inject
    private TypeManager typemanager;
    @Inject
    private QueryExecutor queryExecutor;
    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    private SqlVault sqlVault;
    private Monitor monitor;

    @Setting(description = "initial k-v pairs to be used", key = "edc.sql.store.vault.initdata")
    private String initData;

    @Override
    public String name() {
        return "Sql Vault Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        this.monitor = context.getMonitor().withPrefix(this.getClass().getSimpleName());
        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, "sql-vault.sql");
    }

    @Provider
    public Vault provideSQLVault(ServiceExtensionContext context) {
        sqlVault = new SqlVault(dataSourceRegistry, dataSourceName, transactionContext, typemanager.getMapper(),
                queryExecutor, context.getMonitor());
        return sqlVault;
    }

    @Override
    public void start(){
        if (initData != null && !initData.isEmpty()) {
            String[] kvPairs = initData.split(";");
            for (String kvPair : kvPairs) {
                try {
                    String[] kv = kvPair.split("::");
                    sqlVault.storeSecret(kv[0], kv[1]);
                } catch (Exception e) {
                    monitor.warning("Error storing sql vault data: " + kvPair);
                }
            }
        }

    }
}
