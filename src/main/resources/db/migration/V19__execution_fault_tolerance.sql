-- Track consecutive AgentCore failures per task so transient outages are
-- tolerated instead of failing the whole message on the first hiccup.
ALTER TABLE coordinator_task ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0;
