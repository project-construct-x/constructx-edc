/*
 * Copyright (c) 2025 Think-it GmbH
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

package de.fraunhofer.isst.edc.extension.conx_migration.dev;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.sql.DriverManagerConnectionFactory;
import org.eclipse.edc.sql.datasource.ConnectionFactoryDataSource;
import org.flywaydb.core.Flyway;

import java.util.Properties;

import static de.fraunhofer.isst.edc.extension.conx_migration.dev.ConstructXMigrationExtension.NAME;
import static org.flywaydb.core.api.MigrationVersion.LATEST;

@Extension(NAME)
public class ConstructXMigrationExtension implements ServiceExtension {

    public static final String NAME = "ConstructX Postgresql Schema Migration";

    @Setting(key = "edc.datasource.default.url", description = "DataSource JDBC url")
    private String dbUrl;

    @Setting(key = "edc.datasource.default.user", description = "DataSource JDBC user")
    private String dbUser;

    @Setting(key = "edc.datasource.default.password", description = "DataSource JDBC password")
    private String dbPassword;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void prepare() {
        var jdbcProperties = new Properties();
        jdbcProperties.put("user", dbUser);
        jdbcProperties.put("password", dbPassword);
        var dataSource = new ConnectionFactoryDataSource(new DriverManagerConnectionFactory(), dbUrl, jdbcProperties);
        var flyway = Flyway.configure()
                .baselineVersion("1.0.0")
                .baselineOnMigrate(true)
                .failOnMissingLocations(true)
                .dataSource(dataSource)
                .table("flyway_schema_history")
                .locations("classpath:migrations/constructx")
                .target(LATEST)
                .load();

        var migrateResult = flyway.migrate();
        if (!migrateResult.success) {
            throw new EdcPersistenceException("Migrating connector failed: %s".formatted(
                    String.join(", ", migrateResult.warnings)));
        }
    }
}
