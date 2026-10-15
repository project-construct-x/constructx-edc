--
--  Copyright (c) 2025 Contributors of the Eclipse Foundation
--
--  This program and the accompanying materials are made available under the
--  terms of the Apache License, Version 2.0 which is available at
--  https://www.apache.org/licenses/LICENSE-2.0
--
--  SPDX-License-Identifier: Apache-2.0
--

CREATE TABLE IF NOT EXISTS edc_asset
(
    asset_id                VARCHAR PRIMARY KEY,
    created_at              BIGINT  NOT NULL,
    properties              JSON    DEFAULT '{}',
    private_properties      JSON    DEFAULT '{}',
    data_address            JSON    DEFAULT '{}',
    participant_context_id  VARCHAR NOT NULL,
    dataplane_metadata      JSON
);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_federated_catalog
(
    id                    VARCHAR PRIMARY KEY NOT NULL,
    catalog               JSON,
    marked                BOOLEAN DEFAULT FALSE
);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_contract_definitions
(
    created_at             BIGINT  NOT NULL,
    contract_definition_id VARCHAR NOT NULL,
    access_policy_id       VARCHAR NOT NULL,
    contract_policy_id     VARCHAR NOT NULL,
    assets_selector        JSON    NOT NULL,
    private_properties     JSON,
    participant_context_id VARCHAR NOT NULL,
    PRIMARY KEY (contract_definition_id)
);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_lease
(
    leased_by      VARCHAR               NOT NULL,
    leased_at      BIGINT,
    lease_duration INTEGER DEFAULT 60000 NOT NULL,
    resource_id       VARCHAR NOT NULL,
    resource_kind  VARCHAR NOT NULL,
    PRIMARY KEY(resource_id, resource_kind)
);

COMMENT ON COLUMN edc_lease.leased_at IS 'posix timestamp of lease';
COMMENT ON COLUMN edc_lease.lease_duration IS 'duration of lease in milliseconds';

CREATE TABLE IF NOT EXISTS edc_contract_agreement
(
    agr_id            VARCHAR NOT NULL
    CONSTRAINT contract_agreement_pk
    PRIMARY KEY,
    provider_agent_id VARCHAR,
    consumer_agent_id VARCHAR,
    signing_date      BIGINT,
    start_date        BIGINT,
    end_date          INTEGER,
    asset_id          VARCHAR NOT NULL,
    policy            JSON,
    agr_participant_context_id VARCHAR NOT NULL,
    agr_agreement_id VARCHAR NOT NULL,
    claims            JSON,
    UNIQUE (agr_agreement_id, agr_participant_context_id)
    );

CREATE TABLE IF NOT EXISTS edc_contract_negotiation
(
    id                   VARCHAR           NOT NULL
    CONSTRAINT contract_negotiation_pk
    PRIMARY KEY,
    created_at           BIGINT            NOT NULL,
    updated_at           BIGINT            NOT NULL,
    correlation_id       VARCHAR,
    counterparty_id      VARCHAR           NOT NULL,
    counterparty_address VARCHAR           NOT NULL,
    protocol             VARCHAR           NOT NULL,
    type                 VARCHAR           NOT NULL,
    state                INTEGER DEFAULT 0 NOT NULL,
    state_count          INTEGER DEFAULT 0,
    state_timestamp      BIGINT,
    error_detail         VARCHAR,
    agreement_id         VARCHAR
    CONSTRAINT contract_negotiation_contract_agreement_id_fk
    REFERENCES edc_contract_agreement,
    contract_offers      JSON,
    callback_addresses   JSON,
    trace_context        JSON,
    pending              BOOLEAN DEFAULT FALSE,
    protocol_messages    JSON,
    participant_context_id VARCHAR NOT NULL
);

COMMENT ON COLUMN edc_contract_negotiation.agreement_id IS 'ContractAgreement serialized as JSON';
COMMENT ON COLUMN edc_contract_negotiation.contract_offers IS 'List<ContractOffer> serialized as JSON';
COMMENT ON COLUMN edc_contract_negotiation.trace_context IS 'Map<String,String> serialized as JSON';

CREATE INDEX IF NOT EXISTS contract_negotiation_correlationid_index
    ON edc_contract_negotiation (correlation_id);

CREATE UNIQUE INDEX IF NOT EXISTS contract_negotiation_id_uindex
    ON edc_contract_negotiation (id);

CREATE UNIQUE INDEX IF NOT EXISTS contract_agreement_id_uindex
    ON edc_contract_agreement (agr_id);

CREATE INDEX IF NOT EXISTS contract_negotiation_agreement_id_index
    ON edc_contract_negotiation (agreement_id);

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS contract_negotiation_state ON edc_contract_negotiation (state,state_timestamp);

----------------------------------------------------------


CREATE TABLE IF NOT EXISTS edc_data_plane_instance
(
    id                   VARCHAR NOT NULL PRIMARY KEY,
    data                 JSON
);

CREATE TABLE IF NOT EXISTS edc_edr_entry
(
    transfer_process_id           VARCHAR NOT NULL PRIMARY KEY,
    agreement_id                  VARCHAR NOT NULL,
    asset_id                      VARCHAR NOT NULL,
    provider_id                   VARCHAR NOT NULL,
    contract_negotiation_id       VARCHAR,
    created_at                    BIGINT  NOT NULL,
    participant_context_id VARCHAR NOT NULL
);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_jti_validation
(
    token_id   VARCHAR NOT NULL PRIMARY KEY,
    expires_at BIGINT -- expiry time in epoch millis
);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_policydefinitions
(
    policy_id             VARCHAR NOT NULL,
    created_at            BIGINT  NOT NULL,
    permissions           JSON,
    prohibitions          JSON,
    duties                JSON,
    profiles              JSON,
    extensible_properties JSON,
    inherits_from         VARCHAR,
    assigner              VARCHAR,
    assignee              VARCHAR,
    target                VARCHAR,
    policy_type           VARCHAR NOT NULL,
    private_properties    JSON,
    participant_context_id VARCHAR NOT NULL,
    PRIMARY KEY (policy_id)
    );

COMMENT ON COLUMN edc_policydefinitions.permissions IS 'Java List<Permission> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.prohibitions IS 'Java List<Prohibition> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.duties IS 'Java List<Duty> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.profiles IS 'Java List<String> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.extensible_properties IS 'Java Map<String, Object> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.policy_type IS 'Java PolicyType serialized as JSON';

CREATE UNIQUE INDEX IF NOT EXISTS edc_policydefinitions_id_uindex
    ON edc_policydefinitions (policy_id);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_policy_monitor
(
    entry_id             VARCHAR NOT NULL PRIMARY KEY,
    state                INTEGER NOT NULL            ,
    created_at           BIGINT  NOT NULL            ,
    updated_at           BIGINT  NOT NULL            ,
    state_count          INTEGER DEFAULT 0 NOT NULL,
    state_time_stamp     BIGINT,
    trace_context        JSON,
    error_detail         VARCHAR,
    properties           JSON,
    contract_id          VARCHAR
);

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS policy_monitor_state ON edc_policy_monitor (state,state_time_stamp);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_agreement_retirement
(
    contract_agreement_id     VARCHAR PRIMARY KEY,
    reason                    TEXT   NOT NULL,
    agreement_retirement_date BIGINT NOT NULL
);

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS sql_vault
(
    id     VARCHAR NOT NULL PRIMARY KEY,
    secret VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS edc_target_node_directory
(
    id                      VARCHAR PRIMARY KEY NOT NULL,
    name                    VARCHAR NOT NULL,
    target_url              VARCHAR NOT NULL,
    supported_protocols     JSON
);

COMMENT ON COLUMN edc_target_node_directory.supported_protocols IS 'List<String> serialized as JSON';

----------------------------------------------------------

CREATE TABLE IF NOT EXISTS edc_transfer_process
(
    transferprocess_id         VARCHAR           PRIMARY KEY,
    type                       VARCHAR           NOT NULL,
    state                      INTEGER           NOT NULL,
    state_count                INTEGER DEFAULT 0 NOT NULL,
    state_time_stamp           BIGINT,
    created_at                 BIGINT            NOT NULL,
    updated_at                 BIGINT            NOT NULL,
    trace_context              JSON,
    error_detail               VARCHAR,
    content_data_address       JSON,
    private_properties         JSON,
    callback_addresses         JSON,
    pending                    BOOLEAN  DEFAULT FALSE,
    transfer_type              VARCHAR,
    protocol_messages          JSON,
    data_plane_id              VARCHAR,
    correlation_id             VARCHAR,
    counter_party_address      VARCHAR,
    protocol                   VARCHAR,
    asset_id                   VARCHAR,
    contract_id                VARCHAR,
    data_destination           JSON,
    participant_context_id     VARCHAR NOT NULL,
    dataplane_metadata         JSON,
    data_address_alias         TEXT,
    claims                     JSON
);

COMMENT ON COLUMN edc_transfer_process.trace_context IS 'Java Map serialized as JSON';
COMMENT ON COLUMN edc_transfer_process.content_data_address IS 'DataAddress serialized as JSON';
COMMENT ON COLUMN edc_transfer_process.dataplane_metadata IS 'Dataplane Metadata serialized as JSON';

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS transfer_process_state ON edc_transfer_process (state,state_time_stamp);

----------------------------------------------------------

