-- Table for cwdonator list distribution system
CREATE TABLE IF NOT EXISTS cwdonator_lists (
    clan_tag TEXT PRIMARY KEY,
    list_a TEXT[] DEFAULT '{}',
    list_b TEXT[] DEFAULT '{}'
);
