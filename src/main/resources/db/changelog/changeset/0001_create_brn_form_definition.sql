CREATE TABLE brn_form_definition
(
    id                UUID NOT NULL PRIMARY KEY,
    category          VARCHAR(255),
    deployed_at       TIMESTAMP WITH TIME ZONE NOT NULL default now(),
    deployment_id     VARCHAR(64) NOT NULL,
    description       VARCHAR(4000),
    key               VARCHAR(255) NOT NULL,
    name              VARCHAR(255),
    resource_bytes    BYTEA,
    resource_name     VARCHAR(4000) NOT NULL,
    schema_json       JSONB NOT NULL,
    tenant_id         VARCHAR(255) NOT NULL default '',
    variable_bindings TEXT,
    version           INTEGER NOT NULL,
    CONSTRAINT brn_uniq_form_def UNIQUE (key, version, tenant_id)
);

CREATE INDEX idx_brn_form_key_tenant_id ON brn_form_definition (key, tenant_id);
CREATE INDEX idx_brn_form_deployment_id ON brn_form_definition (deployment_id);
