CREATE TABLE brn_modeler_app
(
    id                    UUID NOT NULL PRIMARY KEY,
    key                   VARCHAR(255) NOT NULL,
    name                  VARCHAR(255),
    description           VARCHAR(4000),
    readme                TEXT,
    state                 VARCHAR(16) NOT NULL DEFAULT 'draft',
    app_definition_id     VARCHAR(64),
    app_definition_key    VARCHAR(255),
    deployment_id         VARCHAR(64),
    deployed_version      INTEGER,
    deployed_content_hash VARCHAR(64),
    deployed_at           TIMESTAMP WITH TIME ZONE,
    tenant_id             VARCHAR(255) NOT NULL DEFAULT '',
    lock_version          BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT brn_uniq_modeler_app UNIQUE (key, tenant_id)
);

CREATE INDEX idx_brn_modeler_app_state ON brn_modeler_app (state);

CREATE TABLE brn_modeler_app_file
(
    id                 UUID NOT NULL PRIMARY KEY,
    app_id             UUID NOT NULL,
    file_key           VARCHAR(255) NOT NULL,
    name               VARCHAR(255),
    description        VARCHAR(4000),
    type               VARCHAR(16) NOT NULL,
    resource_name      VARCHAR(4000) NOT NULL,
    content            BYTEA NOT NULL,
    content_hash       VARCHAR(64) NOT NULL,
    deployed_hash      VARCHAR(64),
    state              VARCHAR(16) NOT NULL DEFAULT 'draft',
    engine_resource_id VARCHAR(255),
    error_log          JSONB,
    lock_version       BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT brn_uniq_modeler_file UNIQUE (app_id, file_key),
    CONSTRAINT brn_fk_modeler_file_app FOREIGN KEY (app_id)
        REFERENCES brn_modeler_app (id) ON DELETE CASCADE
);

CREATE INDEX idx_brn_modeler_file_app_id ON brn_modeler_app_file (app_id);
