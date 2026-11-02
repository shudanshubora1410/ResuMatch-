-- ResuMatch - Phase 0 baseline migration.
--
-- Purpose: prove that Flyway runs from the ServletContextListener and give the health check
-- something real to depend on. Feature tables arrive in their own phases; this file is never
-- edited once applied - new changes always go into a new V2__... migration.

CREATE TABLE IF NOT EXISTS app_metadata (
    meta_key   VARCHAR(64)  NOT NULL,
    meta_value VARCHAR(255) NOT NULL,
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (meta_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO app_metadata (meta_key, meta_value) VALUES ('app_name', 'ResuMatch');
INSERT IGNORE INTO app_metadata (meta_key, meta_value) VALUES ('schema_phase', '0');
