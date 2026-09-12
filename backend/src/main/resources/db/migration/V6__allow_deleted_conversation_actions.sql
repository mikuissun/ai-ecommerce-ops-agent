-- Retain action/audit history after a conversation is deleted.
-- The service rejects pending actions and detaches them before deleting the conversation.
ALTER TABLE pending_actions MODIFY COLUMN conversation_id BIGINT NULL;
