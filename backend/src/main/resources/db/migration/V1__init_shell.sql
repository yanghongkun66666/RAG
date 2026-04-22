CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS learn_shell_marker (
    id BIGSERIAL PRIMARY KEY,
    module_name VARCHAR(100) NOT NULL,
    stage VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO learn_shell_marker (module_name, stage)
SELECT 'shell', 'learn/00-shell'
WHERE NOT EXISTS (
    SELECT 1
    FROM learn_shell_marker
    WHERE module_name = 'shell' AND stage = 'learn/00-shell'
);
