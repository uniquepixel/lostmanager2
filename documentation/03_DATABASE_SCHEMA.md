# Database Schema - Complete Reference

This document provides comprehensive documentation of the PostgreSQL database schema used by LostManager2.

---

## Table of Contents
1. [Overview](#overview)
2. [Core Tables](#core-tables)
3. [Auxiliary Tables](#auxiliary-tables)
4. [Schema Files](#schema-files)
5. [Indexes and Performance](#indexes-and-performance)
6. [Data Relationships](#data-relationships)
7. [Maintenance and Cleanup](#maintenance-and-cleanup)

---

## Overview

### Database Technology
- **DBMS**: PostgreSQL
- **Connection**: JDBC driver (org.postgresql 42.7.7)
- **Connection Pool**: Single connection managed by `dbutil.Connection`

### Connection Configuration
Environment variables:
```
LOST_MANAGER_DB_URL=jdbc:postgresql://host:port/database
LOST_MANAGER_DB_USER=username
LOST_MANAGER_DB_PASSWORD=password
```

### Key Features
- **JSONB support**: Used for flexible schema (achievement_data, actionvalues)
- **Prepared statements**: All queries use parameterized statements (SQL injection protection)
- **Foreign keys**: Referential integrity enforced
- **Indexes**: Strategic indexing for performance
- **Auto-generated keys**: BIGSERIAL for primary keys

---

## Core Tables

### players

**Purpose**: Stores all Clash of Clans player accounts with Discord linking and clan membership.

**Schema**:
```sql
CREATE TABLE players (
    coc_tag TEXT PRIMARY KEY,              -- Player tag (e.g., "#ABC123")
    discord_id TEXT,                       -- Discord user ID (nullable for unlinked)
    clan_tag TEXT,                         -- Current clan (nullable for clanless)
    clan_role TEXT,                        -- In-game role: "leader", "coLeader", "admin", "member", "hiddencoleader"
    name TEXT,                             -- Player name (updated periodically from API)
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag) ON DELETE SET NULL
);
```

**Columns**:
- `coc_tag`: Primary key. Includes # symbol. Example: "#ABC123"
- `discord_id`: Discord snowflake ID. Example: "123456789012345678". NULL if not linked.
- `clan_tag`: References clans table. NULL if player not in a clan.
- `clan_role`: 
  - "leader" = Clan leader
  - "coLeader" = Co-leader
  - "admin" = Elder (yes, confusing - this is the in-game elder rank)
  - "member" = Regular member
  - "hiddencoleader" = Hidden co-leader (some clans hide co-leader status)
- `name`: Player name from CoC. Updated every 2 hours by bot scheduler.

**Indexes**:
```sql
CREATE INDEX idx_players_discord_id ON players(discord_id);
CREATE INDEX idx_players_clan_tag ON players(clan_tag);
```

**Common Queries**:
```sql
-- Get all accounts linked to a Discord user
SELECT * FROM players WHERE discord_id = ?;

-- Get all members of a clan
SELECT * FROM players WHERE clan_tag = ?;

-- Get a specific player
SELECT * FROM players WHERE coc_tag = ?;

-- Find unlinked players in a clan
SELECT * FROM players WHERE clan_tag = ? AND discord_id IS NULL;
```

**Important Notes**:
- Multiple players can have same discord_id (multi-account support)
- clan_tag can be NULL (player not in any clan)
- discord_id can be NULL (player exists but not linked)
- "admin" in clan_role means Elder, not bot admin

---

### clans

**Purpose**: Stores clan configurations including kickpoint settings and Discord role mappings.

**Schema**:
```sql
CREATE TABLE clans (
    clan_tag TEXT PRIMARY KEY,               -- Clan tag (e.g., "#CLAN123")
    name TEXT NOT NULL,                      -- Clan name
    max_kickpoints BIGINT,                   -- Maximum kickpoints before action
    min_season_wins BIGINT,                  -- Minimum wins required per season
    kickpoints_expire_after_days INTEGER,    -- Days until kickpoints expire
    leader_role_id TEXT,                     -- Discord role ID for leaders
    co_leader_role_id TEXT,                  -- Discord role ID for co-leaders
    elder_role_id TEXT,                      -- Discord role ID for elders
    member_role_id TEXT                      -- Discord role ID for members
);
```

**Columns**:
- `clan_tag`: Primary key. Includes # symbol. Example: "#CLAN123"
- `name`: Clan name. Example: "LOST F2P"
- `max_kickpoints`: Kick threshold. Example: 10. NULL if not configured.
- `min_season_wins`: Minimum wins per season. Example: 50. NULL if not required.
- `kickpoints_expire_after_days`: Expiration period. Example: 30. NULL if not configured.
- `leader_role_id` through `member_role_id`: Discord role snowflake IDs. NULL if not configured.

**Configuration via**: `/clanconfig` command

**Common Queries**:
```sql
-- Get clan configuration
SELECT * FROM clans WHERE clan_tag = ?;

-- Get all clans
SELECT clan_tag, name FROM clans ORDER BY name;

-- Get clans with kickpoints configured
SELECT * FROM clans WHERE max_kickpoints IS NOT NULL;
```

**Important Notes**:
- Settings can be NULL (not all clans need all features)
- Role IDs are Discord snowflakes (18-19 digit numbers as strings)
- Kickpoint settings must be configured before using kickpoint features

---

### kickpoints

**Purpose**: Stores individual kickpoint records (penalties) for players.

**Schema**:
```sql
CREATE TABLE kickpoints (
    id BIGSERIAL PRIMARY KEY,                -- Unique kickpoint ID
    player_tag TEXT NOT NULL,                -- Player who received it
    reason TEXT NOT NULL,                    -- Why it was given
    amount INTEGER NOT NULL,                 -- Kickpoint value
    date TIMESTAMP NOT NULL,                 -- When it was given
    expires TIMESTAMP NOT NULL,              -- When it expires
    FOREIGN KEY (player_tag) REFERENCES players(coc_tag) ON DELETE CASCADE
);
```

**Columns**:
- `id`: Auto-generated unique ID. Used for editing/removing.
- `player_tag`: Player who received the kickpoint.
- `reason`: Text description. Can be from template or custom.
- `amount`: Kickpoint value. Usually 1-5.
- `date`: When kickpoint was given (can be backdated).
- `expires`: Calculated as `date + clan.kickpoints_expire_after_days`.

**Indexes**:
```sql
CREATE INDEX idx_kickpoints_player ON kickpoints(player_tag);
CREATE INDEX idx_kickpoints_expires ON kickpoints(expires);
```

**Common Queries**:
```sql
-- Get active kickpoints for a player
SELECT * FROM kickpoints 
WHERE player_tag = ? AND expires > NOW()
ORDER BY date DESC;

-- Get total active kickpoints for a player
SELECT SUM(amount) FROM kickpoints 
WHERE player_tag = ? AND expires > NOW();

-- Get all players with kickpoints in a clan
SELECT p.coc_tag, p.name, SUM(k.amount) as total_kp
FROM players p
JOIN kickpoints k ON p.coc_tag = k.player_tag
WHERE p.clan_tag = ? AND k.expires > NOW()
GROUP BY p.coc_tag, p.name
ORDER BY total_kp DESC;
```

**Important Notes**:
- Expired kickpoints remain in database (not auto-deleted)
- Queries must filter by `expires > NOW()` for active kickpoints
- ON DELETE CASCADE: If player deleted, kickpoints deleted too
- Amount can be any positive integer

---

### kickpoint_reasons

**Purpose**: Stores kickpoint reason templates with default amounts for clans.

**Schema**:
```sql
CREATE TABLE kickpoint_reasons (
    name TEXT NOT NULL,                      -- Reason name
    clan_tag TEXT NOT NULL,                  -- Clan this belongs to
    amount INTEGER NOT NULL,                 -- Default kickpoint amount
    PRIMARY KEY (name, clan_tag),
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag) ON DELETE CASCADE
);
```

**Columns**:
- `name`: Reason name/description. Example: "CW_Attack_vergessen"
- `clan_tag`: Clan this reason belongs to.
- `amount`: Default kickpoint value. Example: 2

**Composite Primary Key**: (name, clan_tag)
- Same reason name can exist in multiple clans
- Each clan can have multiple reasons

**Common Queries**:
```sql
-- Get all reasons for a clan
SELECT * FROM kickpoint_reasons 
WHERE clan_tag = ? 
ORDER BY name;

-- Get a specific reason
SELECT * FROM kickpoint_reasons 
WHERE name = ? AND clan_tag = ?;

-- Count reasons per clan
SELECT clan_tag, COUNT(*) as reason_count
FROM kickpoint_reasons
GROUP BY clan_tag;
```

**Important Notes**:
- Reasons are templates only - actual amount can differ
- Deleting a reason doesn't affect existing kickpoints
- ON DELETE CASCADE: If clan deleted, reasons deleted too

---

### listening_events

**Purpose**: Stores automated event monitoring configurations.

**Schema**:
```sql
CREATE TABLE listening_events (
    id BIGSERIAL PRIMARY KEY,                -- Event ID
    clan_tag TEXT NOT NULL,                  -- Clan to monitor
    listeningtype TEXT NOT NULL,             -- Event type: "cs", "cw", "cwlday", "raid", "fixtimeinterval", "cwlend"
    listeningvalue BIGINT NOT NULL,          -- Duration in milliseconds (-1 for start triggers)
    actiontype TEXT NOT NULL,                -- Action: "infomessage", "kickpoint", "cwdonator", "filler", "raidfails"
    channel_id TEXT NOT NULL,                -- Discord channel ID for messages
    actionvalues JSONB,                      -- Action configuration (JSON)
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag) ON DELETE CASCADE
);
```

**Columns**:
- `id`: Auto-generated event ID.
- `clan_tag`: Clan to monitor.
- `listeningtype`: 
  - "cs" = Clan Games
  - "cw" = Clan War
  - "cwlday" = CWL Day
  - "raid" = Raid Weekend
  - "fixtimeinterval" = Fixed time interval (unused)
  - "cwlend" = CWL End (entire CWL)
- `listeningvalue`: 
  - -1 = Fire on event START (war start detection)
  - 0 = Fire at event END
  - Positive = Milliseconds before end
- `actiontype`:
  - "infomessage" = Info only
  - "kickpoint" = Add kickpoints
  - "cwdonator" = Select donors
  - "filler" = Check fillers
  - "raidfails" = Raid analysis
- `channel_id`: Discord channel snowflake ID.
- `actionvalues`: JSONB array of ActionValue objects.

**Action Values Format**:

Kickpoint:
```json
[{"reason": {"name": "CW_Attack_Missed", "clan_tag": "#CLAN123"}}]
```

Filler:
```json
[{"type": "FILLER"}]
```

Raidfails:
```json
[{"value": 5, "value2": 3, "penalizeTies": true}]
```

**Indexes**:
```sql
CREATE INDEX idx_listening_events_clan ON listening_events(clan_tag);
CREATE INDEX idx_listening_events_type ON listening_events(listeningtype);
```

**Common Queries**:
```sql
-- Get all events
SELECT * FROM listening_events ORDER BY id;

-- Get events for a clan
SELECT * FROM listening_events WHERE clan_tag = ?;

-- Get events by type
SELECT * FROM listening_events WHERE listeningtype = ?;

-- Delete an event
DELETE FROM listening_events WHERE id = ?;
```

**Important Notes**:
- JSONB enables flexible configuration per action type
- actionvalues structure varies by actiontype
- Event polling checks all events every 2 minutes

---

### achievement_data

**Purpose**: Historical achievement snapshots for tracking progress over time.

**Schema**:
```sql
CREATE TABLE achievement_data (
    id BIGSERIAL PRIMARY KEY,                -- Record ID
    player_tag TEXT NOT NULL,                -- Player
    type TEXT NOT NULL,                      -- Achievement type: "CLANGAMES_POINTS", "WINS", etc.
    time TIMESTAMP NOT NULL,                 -- When snapshot was taken
    data JSONB NOT NULL,                     -- Achievement data
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(player_tag, type, time)           -- One snapshot per player/type/time
);
```

**Columns**:
- `id`: Auto-generated record ID.
- `player_tag`: Player tag.
- `type`: Achievement category. Common: "CLANGAMES_POINTS", "WINS"
- `time`: Snapshot timestamp (often season start/end).
- `data`: JSONB format. Structure varies by type.
- `created_at`: Record creation timestamp.

**Data Formats**:

Clan Games Points:
```json
{"value": 5000}
```

Wins:
```json
{"value": 1234}
```

**Indexes**:
```sql
CREATE INDEX idx_achievement_data_lookup ON achievement_data(player_tag, type, time);
CREATE INDEX idx_achievement_data_time ON achievement_data(time);
CREATE INDEX idx_achievement_data_type ON achievement_data(type);
```

**Unique Constraint**:
- (player_tag, type, time) = unique
- Prevents duplicate snapshots
- INSERT will fail if duplicate

**Common Queries**:
```sql
-- Get all snapshots for a player and type
SELECT * FROM achievement_data 
WHERE player_tag = ? AND type = ?
ORDER BY time;

-- Get snapshots for a time range
SELECT * FROM achievement_data 
WHERE player_tag = ? 
  AND type = ? 
  AND time BETWEEN ? AND ?;

-- Get latest snapshot
SELECT * FROM achievement_data 
WHERE player_tag = ? AND type = ?
ORDER BY time DESC LIMIT 1;
```

**Cleanup Function**:
```sql
-- Removes data older than 6 months
SELECT cleanup_old_achievement_data();
```

**Important Notes**:
- Used for Clan Games tracking (22nd start, 28th end)
- Used for season wins tracking
- JSONB allows flexible data structure
- See `achievement_data_schema.sql` for full schema with cleanup

---

## Auxiliary Tables

### cw_fillers

**Purpose**: Tracks players opted out of wars to exclude from missed attack tracking.

**Schema**:
```sql
CREATE TABLE cw_fillers (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,                  -- Clan
    player_tag TEXT NOT NULL,                -- Player opted out
    war_end_time TIMESTAMP NOT NULL,         -- War end timestamp (unique identifier)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(clan_tag, player_tag, war_end_time)
);
```

**Purpose**: When a "filler check" event runs, it identifies players opted out of war and records them here. Later, when checking for missed attacks, these players are excluded.

**Index**:
```sql
CREATE INDEX idx_cw_fillers_lookup ON cw_fillers(clan_tag, war_end_time);
```

**Cleanup**:
```sql
-- Remove data older than 14 days
DELETE FROM cw_fillers WHERE war_end_time < NOW() - INTERVAL '14 days';
```

**Common Queries**:
```sql
-- Get fillers for a specific war
SELECT player_tag FROM cw_fillers 
WHERE clan_tag = ? AND war_end_time = ?;

-- Check if player was filler
SELECT EXISTS(
    SELECT 1 FROM cw_fillers 
    WHERE clan_tag = ? 
      AND player_tag = ? 
      AND war_end_time = ?
);
```

**See**: `cw_fillers_table.sql`

---

### cwdonator_lists

**Purpose**: Tracks war donor selections to ensure fair rotation.

**Schema**:
```sql
CREATE TABLE cwdonator_lists (
    clan_tag TEXT NOT NULL,
    player_tags TEXT[] NOT NULL,             -- Array of selected player tags
    created_at TIMESTAMP DEFAULT CURRENT_timestamp,
    war_end_time TIMESTAMP,                  -- War identifier
    PRIMARY KEY (clan_tag, war_end_time)
);
```

**Columns**:
- `clan_tag`: Clan
- `player_tags`: PostgreSQL TEXT[] array of player tags selected
- `war_end_time`: War end timestamp (unique identifier)
- `created_at`: Record creation time

**Usage**: 
When `/cwdonator use_lists:true` is run:
1. Query previous selections
2. Exclude recently selected players
3. Select new donors from remaining pool
4. Store new selections

**Common Queries**:
```sql
-- Get last N donor lists for a clan
SELECT player_tags, war_end_time 
FROM cwdonator_lists 
WHERE clan_tag = ? 
ORDER BY war_end_time DESC 
LIMIT ?;

-- Insert new donor list
INSERT INTO cwdonator_lists (clan_tag, player_tags, war_end_time)
VALUES (?, ?::TEXT[], ?);
```

**See**: `cwdonator_lists_table.sql`

---

### sideclans

**Purpose**: Stores side clans used for CWL roster checking.

**Schema**:
```sql
CREATE TABLE sideclans (
    clan_tag TEXT PRIMARY KEY,               -- Side clan tag
    name TEXT NOT NULL,                      -- Side clan name
    belongs_to TEXT                          -- Main clan it belongs to
);
```

**Columns**:
- `clan_tag`: Side clan tag. Example: "#SIDECLAN1"
- `name`: Side clan name. Example: "LOST CWL 1"
- `belongs_to`: Main clan tag. Example: "#CLAN123"

**Index**:
```sql
CREATE INDEX idx_sideclans_name ON sideclans(name);
```

**Usage**: 
- Side clans are additional clans used for CWL when main clan doesn't have 30 spots
- Manually added to database
- Appear in autocomplete for `/cwlmemberstatus`

**Common Queries**:
```sql
-- Get all side clans
SELECT * FROM sideclans ORDER BY name;

-- Get side clans for a main clan
SELECT * FROM sideclans WHERE belongs_to = ?;

-- Add a side clan
INSERT INTO sideclans (clan_tag, name, belongs_to)
VALUES (?, ?, ?);
```

**Manual Management**: Currently requires direct database access or SQL execution.

**See**: `sideclans_table.sql`

---

## Schema Files

### achievement_data_schema.sql

**Location**: `/achievement_data_schema.sql` (repository root)

**Contents**:
- CREATE TABLE achievement_data
- Indexes for performance
- Cleanup function `cleanup_old_achievement_data()`
- Trigger for updated_at (if achievements table exists)
- Comments and documentation

**Key Features**:
- JSONB type for flexible data
- UNIQUE constraint on (player_tag, type, time)
- Auto-cleanup function (removes data > 6 months old)
- Indexes for fast lookups

**Running**:
```bash
psql -U username -d database -f achievement_data_schema.sql
```

---

### cw_fillers_table.sql

**Location**: `/cw_fillers_table.sql` (repository root)

**Contents**:
- CREATE TABLE cw_fillers
- Index for lookups
- Cleanup comment (manual)

**Purpose**: Supports war filler tracking for listening events.

---

### cwdonator_lists_table.sql

**Location**: `/cwdonator_lists_table.sql` (repository root)

**Contents**:
- CREATE TABLE cwdonator_lists
- Comments and usage notes

**Purpose**: Supports list-based donor rotation.

---

### sideclans_table.sql

**Location**: `/sideclans_table.sql` (repository root)

**Contents**:
- CREATE TABLE sideclans
- Index on name
- Comments

**Purpose**: Supports CWL roster checking for side clans.

---

## Indexes and Performance

### Indexing Strategy

**players table**:
```sql
PRIMARY KEY (coc_tag)                        -- Clustered index
INDEX idx_players_discord_id (discord_id)    -- Multi-account queries
INDEX idx_players_clan_tag (clan_tag)        -- Clan member queries
```

**kickpoints table**:
```sql
PRIMARY KEY (id)                             -- Clustered index
INDEX idx_kickpoints_player (player_tag)     -- Player kickpoint queries
INDEX idx_kickpoints_expires (expires)       -- Expiration filtering
```

**achievement_data table**:
```sql
PRIMARY KEY (id)                             -- Clustered index
INDEX idx_achievement_data_lookup (player_tag, type, time)  -- Composite for queries
INDEX idx_achievement_data_time (time)       -- Time-based queries
INDEX idx_achievement_data_type (type)       -- Type-based queries
```

**listening_events table**:
```sql
PRIMARY KEY (id)                             -- Clustered index
INDEX idx_listening_events_clan (clan_tag)   -- Clan filtering
INDEX idx_listening_events_type (listeningtype)  -- Type filtering
```

### Query Patterns

**Most Common Queries**:
1. Get player by tag: O(1) via primary key
2. Get clan members: O(log n) via clan_tag index
3. Get user's accounts: O(log n) via discord_id index
4. Get active kickpoints: O(log n) via player_tag index + filter
5. Get achievement history: O(log n) via composite index

**Optimization Tips**:
- Always use prepared statements (parameterized queries)
- Filter expired kickpoints in query: `WHERE expires > NOW()`
- Use LIMIT for pagination
- Avoid SELECT * when only specific columns needed

### Database Size Estimates

For a clan family with 200 players:
- players: ~50 KB
- clans: ~5 KB
- kickpoints: ~50-200 KB (depends on history)
- kickpoint_reasons: ~10 KB
- listening_events: ~5-20 KB
- achievement_data: ~1-5 MB (grows over time, cleanup recommended)
- cw_fillers: ~10-50 KB
- cwdonator_lists: ~10-50 KB
- sideclans: ~5 KB

**Total**: ~1-6 MB for typical setup, growing with achievement_data over time.

---

## Data Relationships

### Entity Relationship Diagram (Textual)

```
clans
  |
  |-- (one to many) --> players
  |     |
  |     |-- (one to many) --> kickpoints
  |     |
  |     |-- (one to many) --> achievement_data
  |     |
  |     |-- (many to one) --> users (via discord_id)
  |
  |-- (one to many) --> kickpoint_reasons
  |
  |-- (one to many) --> listening_events
  |
  |-- (one to many) --> cw_fillers
  |
  |-- (one to many) --> cwdonator_lists
```

### Foreign Key Constraints

**players.clan_tag** → **clans.clan_tag**
- ON DELETE SET NULL (player becomes clanless if clan deleted)

**kickpoints.player_tag** → **players.coc_tag**
- ON DELETE CASCADE (kickpoints deleted if player deleted)

**kickpoint_reasons.clan_tag** → **clans.clan_tag**
- ON DELETE CASCADE (reasons deleted if clan deleted)

**listening_events.clan_tag** → **clans.clan_tag**
- ON DELETE CASCADE (events deleted if clan deleted)

### Data Integrity Rules

1. **Player Linking**:
   - A player can only be in one clan at a time (clan_tag is singular)
   - A Discord user can have multiple linked accounts (multiple rows with same discord_id)
   - A player can be unlinked (discord_id = NULL)

2. **Kickpoints**:
   - Kickpoints must have a player (NOT NULL)
   - Expired kickpoints remain but don't count toward total
   - Amount must be positive

3. **Clans**:
   - Settings can be NULL (not required)
   - Deleting clan cascades to related data

4. **Achievement Data**:
   - UNIQUE(player_tag, type, time) prevents duplicates
   - Data is JSONB for flexibility

---

## Maintenance and Cleanup

### Regular Maintenance Tasks

#### 1. Achievement Data Cleanup
**Frequency**: Monthly or quarterly

**Purpose**: Remove old achievement snapshots (> 6 months)

**Method**:
```sql
-- Check how much data will be deleted
SELECT COUNT(*) FROM achievement_data 
WHERE time < NOW() - INTERVAL '6 months';

-- Execute cleanup
SELECT cleanup_old_achievement_data();
```

**Automated**: Consider using pg_cron extension:
```sql
CREATE EXTENSION pg_cron;
SELECT cron.schedule(
    'cleanup-old-achievements',
    '0 2 * * *',  -- Daily at 2 AM
    'SELECT cleanup_old_achievement_data()'
);
```

#### 2. CW Fillers Cleanup
**Frequency**: Weekly

**Purpose**: Remove filler records older than 14 days

**Method**:
```sql
DELETE FROM cw_fillers 
WHERE war_end_time < NOW() - INTERVAL '14 days';
```

#### 3. Expired Kickpoints (Optional)
**Frequency**: Monthly

**Purpose**: Actually delete expired kickpoints (usually kept for history)

**Method**:
```sql
-- Check what will be deleted
SELECT COUNT(*) FROM kickpoints WHERE expires < NOW();

-- Delete if desired
DELETE FROM kickpoints WHERE expires < NOW();
```

**Note**: Usually NOT recommended - keep for audit trail.

#### 4. Database Vacuum
**Frequency**: Weekly

**Purpose**: Reclaim storage and update statistics

**Method**:
```sql
VACUUM ANALYZE;
```

**Automated**: PostgreSQL has autovacuum enabled by default.

### Backup Strategy

**Recommended Schedule**:
- **Full backup**: Daily
- **Transaction log backup**: Continuous or hourly
- **Retention**: 30 days minimum

**Critical Tables** (priority for backup):
1. players (contains all linking data)
2. clans (contains all configuration)
3. kickpoints (historical penalty data)
4. kickpoint_reasons (clan rules)

**Less Critical**:
- achievement_data (can be recreated from API over time)
- cw_fillers (temporary data)
- cwdonator_lists (temporary data)

**Backup Methods**:

pg_dump (full database):
```bash
pg_dump -U username database > backup.sql
```

pg_dump (specific tables):
```bash
pg_dump -U username -t players -t clans -t kickpoints database > critical_backup.sql
```

pg_basebackup (physical backup):
```bash
pg_basebackup -D /backup/path -F tar -z -P
```

### Disaster Recovery

**Recovery Priority**:
1. Restore players table (links and memberships)
2. Restore clans table (configuration)
3. Restore kickpoints (historical data)
4. Restore other tables as needed

**Testing**: Regularly test restore procedures on a separate test database.

### Performance Monitoring

**Key Metrics**:
- Query execution time
- Index usage
- Table sizes
- Slow queries

**PostgreSQL Monitoring Queries**:

Table sizes:
```sql
SELECT 
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

Index usage:
```sql
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
```

Slow queries (requires pg_stat_statements):
```sql
SELECT 
    query,
    calls,
    total_time,
    mean_time,
    max_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;
```

---

## Database Initialization

### First-Time Setup

**1. Create Database**:
```sql
CREATE DATABASE lostmanager2;
```

**2. Create Core Tables**:
The bot automatically creates missing tables on startup via `dbutil.Connection.tablesExists()`. However, you can manually create them:

```sql
-- players table
CREATE TABLE players (
    coc_tag TEXT PRIMARY KEY,
    discord_id TEXT,
    clan_tag TEXT,
    clan_role TEXT,
    name TEXT
);

-- clans table
CREATE TABLE clans (
    clan_tag TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    max_kickpoints BIGINT,
    min_season_wins BIGINT,
    kickpoints_expire_after_days INTEGER,
    leader_role_id TEXT,
    co_leader_role_id TEXT,
    elder_role_id TEXT,
    member_role_id TEXT
);

-- kickpoints table
CREATE TABLE kickpoints (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    reason TEXT NOT NULL,
    amount INTEGER NOT NULL,
    date TIMESTAMP NOT NULL,
    expires TIMESTAMP NOT NULL,
    FOREIGN KEY (player_tag) REFERENCES players(coc_tag) ON DELETE CASCADE
);

-- kickpoint_reasons table
CREATE TABLE kickpoint_reasons (
    name TEXT NOT NULL,
    clan_tag TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (name, clan_tag),
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag) ON DELETE CASCADE
);

-- listening_events table
CREATE TABLE listening_events (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    listeningtype TEXT NOT NULL,
    listeningvalue BIGINT NOT NULL,
    actiontype TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    actionvalues JSONB,
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag) ON DELETE CASCADE
);
```

**3. Run Schema Files**:
```bash
psql -U username -d lostmanager2 -f achievement_data_schema.sql
psql -U username -d lostmanager2 -f cw_fillers_table.sql
psql -U username -d lostmanager2 -f cwdonator_lists_table.sql
psql -U username -d lostmanager2 -f sideclans_table.sql
```

**4. Create Indexes**:
```sql
CREATE INDEX idx_players_discord_id ON players(discord_id);
CREATE INDEX idx_players_clan_tag ON players(clan_tag);
CREATE INDEX idx_kickpoints_player ON kickpoints(player_tag);
CREATE INDEX idx_kickpoints_expires ON kickpoints(expires);
CREATE INDEX idx_listening_events_clan ON listening_events(clan_tag);
CREATE INDEX idx_listening_events_type ON listening_events(listeningtype);
```

### Initial Data Population

**1. Add Clans**:
```sql
INSERT INTO clans (clan_tag, name) VALUES
    ('#CLAN123', 'LOST F2P'),
    ('#CLAN456', 'LOST Elite'),
    ('#CLAN789', 'LOST Training');
```

**2. Configure Clan Settings** (via `/clanconfig` command or SQL):
```sql
UPDATE clans SET
    max_kickpoints = 10,
    min_season_wins = 50,
    kickpoints_expire_after_days = 30
WHERE clan_tag = '#CLAN123';
```

**3. Add Side Clans** (if using CWL):
```sql
INSERT INTO sideclans (clan_tag, name, belongs_to) VALUES
    ('#SIDE1', 'LOST CWL 1', '#CLAN123'),
    ('#SIDE2', 'LOST CWL 2', '#CLAN123');
```

**4. Link Players** (via `/verify` or `/link` commands - these will populate players table)

---

## Troubleshooting

### Common Issues

#### 1. Connection Refused
**Symptom**: "Verbindung zur Datenbank fehlgeschlagen"

**Solutions**:
- Check PostgreSQL is running: `systemctl status postgresql`
- Verify connection URL: `jdbc:postgresql://localhost:5432/lostmanager2`
- Check firewall rules
- Verify credentials

#### 2. Foreign Key Violations
**Symptom**: "violates foreign key constraint"

**Solutions**:
- Ensure parent record exists (e.g., clan before player)
- Check referential integrity
- Use ON DELETE CASCADE appropriately

#### 3. Unique Constraint Violations
**Symptom**: "duplicate key value violates unique constraint"

**Solutions**:
- For achievement_data: This is normal - duplicate inserts are ignored
- For players: Player already exists - use UPDATE instead
- For kickpoint_reasons: Reason already exists for clan

#### 4. Slow Queries
**Symptom**: Commands take long time to respond

**Solutions**:
- Check index usage (see Performance Monitoring)
- Run VACUUM ANALYZE
- Optimize queries to use indexes
- Add missing indexes

#### 5. JSONB Errors
**Symptom**: "invalid input syntax for type json"

**Solutions**:
- Validate JSON before inserting
- Use ::jsonb cast for proper type conversion
- Check for proper escaping

---

## Security Considerations

### SQL Injection Prevention
✅ **All queries use prepared statements**
```java
// Good (used throughout bot)
PreparedStatement pstmt = conn.prepareStatement(
    "SELECT * FROM players WHERE coc_tag = ?"
);
pstmt.setString(1, playerTag);

// Bad (never used)
String query = "SELECT * FROM players WHERE coc_tag = '" + playerTag + "'";
```

### Access Control
- Database credentials in environment variables (not in code)
- Separate database user for bot (limited permissions)
- No direct database access for Discord users

### Data Privacy
- Discord IDs stored (necessary for linking)
- No personal information beyond Discord ID and player tag
- Kickpoint reasons may contain sensitive info (keep secure)

### Backup Encryption
- Encrypt backups at rest
- Secure transmission of backups
- Limit access to backup files

---

## Migration Guide

### Adding New Columns

**Safe Migration** (backward compatible):
```sql
-- Add nullable column
ALTER TABLE clans ADD COLUMN new_setting TEXT;

-- Update bot code to use column
-- Deploy new bot version

-- If needed, set default values
UPDATE clans SET new_setting = 'default_value' WHERE new_setting IS NULL;

-- Optionally make NOT NULL later
ALTER TABLE clans ALTER COLUMN new_setting SET NOT NULL;
```

### Adding New Tables

**Process**:
1. Create table in new .sql file
2. Add to bot's `tablesExists()` method if needed
3. Create data wrapper class if needed
4. Deploy bot with new schema support

### Changing Column Types

**Example**: Changing kickpoints amount from INTEGER to BIGINT:
```sql
ALTER TABLE kickpoints ALTER COLUMN amount TYPE BIGINT;
```

**Note**: Compatible types can be changed directly. Incompatible types require data migration.

### Removing Deprecated Data

**Process**:
1. Mark as deprecated in code (stop writing)
2. Wait for grace period (ensure no old bot versions running)
3. Remove from code
4. Drop column/table
5. Update documentation

---

**End of Database Schema Documentation**

For data structure mappings, see `01_DATA_STRUCTURES.md`.

For command usage, see `02_COMMANDS_REFERENCE.md`.

For architecture overview, see `00_MASTER_OVERVIEW.md`.
