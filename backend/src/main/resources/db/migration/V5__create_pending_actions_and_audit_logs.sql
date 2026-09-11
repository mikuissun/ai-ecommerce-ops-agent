CREATE TABLE pending_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    arguments_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP NULL,
    CONSTRAINT fk_pending_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_pending_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT chk_pending_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXECUTED','FAILED')),
    INDEX idx_pending_user_status (user_id, status)
);

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pending_action_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    target VARCHAR(100) NOT NULL,
    before_value VARCHAR(100) NULL,
    after_value VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_audit_pending FOREIGN KEY (pending_action_id) REFERENCES pending_actions(id),
    CONSTRAINT uk_audit_pending UNIQUE (pending_action_id)
);
