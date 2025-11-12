-- Table for storing side clans used for CWL
-- These clans are manually added and used for autocomplete in cwlmemberstatus command
CREATE TABLE IF NOT EXISTS sideclans (
    clan_tag TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    belongs_to TEXT
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_sideclans_name ON sideclans(name);

COMMENT ON TABLE sideclans IS 'Side clans used for CWL member status tracking. Manually maintained.';
COMMENT ON COLUMN sideclans.clan_tag IS 'Clan tag (with # prefix), primary key';
COMMENT ON COLUMN sideclans.name IS 'Clan name for display in autocomplete';
COMMENT ON COLUMN sideclans.belongs_to IS 'Main clan this side clan belongs to';
