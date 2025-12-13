# Data Structures - Complete Reference

This document provides comprehensive documentation of all data wrapper classes used in LostManager2.

---

## Table of Contents
1. [Player](#player)
2. [Clan](#clan)
3. [User](#user)
4. [Kickpoint](#kickpoint)
5. [KickpointReason](#kickpointreason)
6. [ListeningEvent](#listeningevent)
7. [AchievementData](#achievementdata)
8. [ActionValue](#actionvalue)

---

## Player

**File**: `src/main/java/datawrapper/Player.java`

### Purpose
Represents a Clash of Clans player with data from both the database (authoritative for Discord linking and clan membership) and the CoC API (real-time game data).

### Key Concept: Dual Data Sources
The Player class maintains separate fields for database and API data:
- **Database (DB)**: Clan membership, linked Discord account, manual overrides
- **API**: Real-time stats, current war status, achievements, name updates

### Constructor
```java
Player player = new Player("#ABC123");
```

### Fields

#### Identity
- `String tag`: Player tag (e.g., "#ABC123") - primary identifier

#### Names
- `String namedb`: Player name from database (last saved)
- `String nameapi`: Player name from CoC API (current)

#### Clan Information
- `Clan clandb`: Clan from database (authoritative)
- `Clan clanapi`: Clan from API (where player currently is)
- `RoleType roledb`: Clan role in database
- `RoleType roleapi`: Clan role from API

#### Discord Linking
- `User user`: Linked Discord user (if any)

#### War/Raid Data
- `Boolean warpreference`: Whether player opts into wars (from API)
- `Integer warmapposition`: Position on war map (from API)
- `Integer currentRaidAttacks`: Raid attacks used this weekend
- `Integer currentRaidGoldLooted`: Gold looted in raids
- `Integer currentRaidAttackLimit`: Max raid attacks available
- `Integer currentRaidbonusAttackLimit`: Bonus raid attacks available

#### Kickpoints
- `ArrayList<Kickpoint> kickpoints`: All active (non-expired) kickpoint records
- `Long kickpointstotal`: Sum of active kickpoint amounts

#### Achievements
- `AchievementData achievementDataAPI`: Current achievements from API
- `HashMap<Type, ArrayList<AchievementData>> achievementDatasInDB`: Historical achievement snapshots

### Enums

#### RoleType
Represents a player's role, either in-game or as bot admin.

```java
public enum RoleType {
    ADMIN,      // Bot administrator
    LEADER,     // Clan leader
    COLEADER,   // Clan co-leader
    ELDER,      // Clan elder (NOTE: stored as "admin" in database clan_role)
    MEMBER,     // Regular clan member
    NOTINCLAN   // Player not in any clan
}
```

**Important Note**: The database `clan_role` field uses different strings:
- "leader" → RoleType.LEADER
- "coLeader" → RoleType.COLEADER
- "admin" → RoleType.ELDER (this is the in-game "Elder" rank, NOT bot admin)
- "member" → RoleType.MEMBER
- "hiddencoleader" → Special case for hidden co-leaders

**Static Helper Methods**:
```java
// Check if role is elder or higher (ELDER, COLEADER, or LEADER)
boolean isElderOrHigher(RoleType role)

// Check if role string is elder or higher ("admin", "coLeader", or "leader")
// Note: Does NOT include "hiddencoleader"
boolean isElderOrHigherString(String role)
```

### Core Methods

#### Data Access (Database)
```java
String getNameDB()          // Get player name from database
Clan getClanDB()            // Get clan from database (authoritative for membership)
RoleType getRoleDB()        // Get clan role from database
User getUser()              // Get linked Discord user
boolean IsLinked()          // Check if player is linked to Discord account
boolean AccExists()         // Check if player exists in database
```

#### Data Access (API)
```java
String getNameAPI()         // Get current player name from CoC API
Clan getClanAPI()           // Get current clan from API (where player is now)
RoleType getRoleAPI()       // Get current clan role from API
Boolean getWarPreference()  // Check if player opts into wars
Integer getWarMapPosition() // Get position on war map
```

#### Raid Weekend Data
```java
Integer getCurrentRaidAttacks()          // Attacks used this raid
Integer getCurrentRaidGoldLooted()       // Gold looted this raid
Integer getCurrentRaidAttackLimit()      // Max attacks available
Integer getCurrentRaidbonusAttackLimit() // Bonus attacks available
```

#### Kickpoints
```java
ArrayList<Kickpoint> getKickpoints()  // Get all active kickpoint records
Long getKickpointsTotal()             // Get sum of active kickpoints
```

#### Achievements
```java
AchievementData getAchievementDataAPI() // Get current achievements from API
HashMap<Type, ArrayList<AchievementData>> getAchievementDatasInDB() // Get historical data

// Save current achievement state to database
void addAchievementDataToDB(AchievementData.Type type, Timestamp timestamp)
```

#### Verification
```java
boolean verifyCocTokenAPI(String apiToken) // Verify API token for account linking
```

#### Utility
```java
String getInfoStringDB()    // Formatted string: "[Emoji] Name (Tag)" based on role
Player refreshData()        // Clear all cached data, force reload on next access
```

### Usage Examples

#### Example 1: Get Player Information
```java
Player player = new Player("#ABC123");

// Get name (API will fetch if not cached)
String name = player.getNameAPI();

// Get clan membership (from database - authoritative)
Clan clan = player.getClanDB();
if (clan != null) {
    System.out.println(name + " is in " + clan.getNameDB());
}

// Check if linked to Discord
if (player.IsLinked()) {
    User user = player.getUser();
    System.out.println("Discord ID: " + user.getUserID());
}

// Get kickpoints
Long kp = player.getKickpointsTotal();
System.out.println("Total kickpoints: " + kp);
```

#### Example 2: Verify Player for Linking
```java
String tag = "#ABC123";
String apiToken = "abc123def456";

Player player = new Player(tag);

// Check if player exists in CoC
if (!player.AccExists()) {
    System.out.println("Player doesn't exist!");
    return;
}

// Check if already linked
if (player.IsLinked()) {
    System.out.println("Player already linked!");
    return;
}

// Verify API token
if (player.verifyCocTokenAPI(apiToken)) {
    // Token valid, create database entry
    DBUtil.executeUpdate(
        "INSERT INTO players (coc_tag, discord_id, name) VALUES (?, ?, ?)",
        tag, discordUserId, player.getNameAPI()
    );
    System.out.println("Successfully linked!");
} else {
    System.out.println("Invalid API token!");
}
```

#### Example 3: Track Raid Progress
```java
Player player = new Player("#ABC123");

int attacksUsed = player.getCurrentRaidAttacks();
int attacksLimit = player.getCurrentRaidAttackLimit();
int bonusLimit = player.getCurrentRaidbonusAttackLimit();
int totalLimit = attacksLimit + bonusLimit;

System.out.println("Raid attacks: " + attacksUsed + "/" + totalLimit);

if (attacksUsed < totalLimit) {
    System.out.println("Player hasn't finished raid attacks!");
}
```

#### Example 4: Save Achievement Snapshot
```java
Player player = new Player("#ABC123");

// Save current wins for season tracking
Timestamp now = new Timestamp(System.currentTimeMillis());
player.addAchievementDataToDB(AchievementData.Type.WINS, now);

// Later, retrieve historical data
HashMap<Type, ArrayList<AchievementData>> history = player.getAchievementDatasInDB();
ArrayList<AchievementData> winsHistory = history.get(AchievementData.Type.WINS);
for (AchievementData data : winsHistory) {
    System.out.println("Wins at " + data.getTime() + ": " + data.getData());
}
```

### Database Representation

**players table**:
```sql
CREATE TABLE players (
    coc_tag TEXT PRIMARY KEY,          -- Player tag
    discord_id TEXT,                   -- Linked Discord user ID
    clan_tag TEXT,                     -- Current clan (authoritative)
    clan_role TEXT,                    -- Clan role: "leader", "coLeader", "admin" (elder), "member"
    name TEXT,                         -- Player name (updated periodically)
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag)
);
```

### API Endpoints Used

1. **Player Information**: `GET /players/{playerTag}`
   - Returns: Name, clan, role, achievements, war preference
   - Encoded tag: `#ABC123` → `%23ABC123`

2. **Token Verification**: `POST /players/{playerTag}/verifytoken`
   - Body: `{"token": "..."}`
   - Returns: `{"status": "ok"}` or `{"status": "invalid"}`

### Important Notes

1. **Lazy Loading**: All data is fetched only when accessed via getters. First call may be slow.

2. **Caching**: Once loaded, data is cached until `refreshData()` is called.

3. **Database vs API**: 
   - Use `getClanDB()` to check official clan membership
   - Use `getClanAPI()` to check where player currently is (might be visiting)
   - Mismatches indicate player moved clans

4. **Elder Confusion**: 
   - Database stores elders as `clan_role = "admin"`
   - RoleType.ADMIN is for bot admins, not in-game elders
   - RoleType.ELDER is the in-game elder rank
   - Helper method `isElderOrHigherString("admin")` returns true

5. **Hidden Co-Leaders**: 
   - Some clans hide co-leader status in game
   - Database may store as "hiddencoleader"
   - API will show as "coLeader"
   - For role checks, treat as co-leaders

6. **Kickpoint Expiration**:
   - `getKickpoints()` returns only non-expired kickpoints
   - Expired kickpoints remain in database but don't count toward total

7. **Name Updates**:
   - Bot updates `namedb` from API every 2 hours
   - Use `getNameAPI()` for real-time name
   - Use `getNameDB()` for last known name (faster, no API call)

---

## Clan

**File**: `src/main/java/datawrapper/Clan.java`

### Purpose
Represents a Clash of Clans clan with configuration, member data, and event information from both database and CoC API.

### Constructor
```java
Clan clan = new Clan("#CLAN123");
```

### Fields

#### Identity
- `String clan_tag`: Clan tag (primary identifier)

#### Names
- `String namedb`: Clan name from database
- `String nameapi`: Clan name from CoC API

#### Member Lists
- `ArrayList<Player> playerlistdb`: Members from database (authoritative)
- `ArrayList<Player> playerlistapi`: Members from CoC API (current roster)

#### Clan War (CW)
- `Boolean cwactive`: Is clan war active?
- `ArrayList<Player> clanwarmembers`: Players in current war
- `Long CWEndTimeMillis`: War end timestamp (milliseconds since epoch)

#### Raid Weekend
- `Boolean raidactive`: Is raid weekend active?
- `ArrayList<Player> raidmembers`: Players who participated in raid
- `Long RaidEndTimeMillis`: Raid end timestamp

#### Clan War League (CWL)
- `Boolean cwlactive`: Is CWL active?
- `ArrayList<Player> cwlmemberlist`: Players in CWL roster
- `Long CWLDayEndTimeMillis`: Current CWL day end timestamp

#### Clan Games (CS)
- `Long CGEndTimeMillis`: Clan Games end timestamp

#### Settings (Database Only)
- `Long max_kickpoints`: Maximum kickpoints before action
- `Long min_season_wins`: Minimum wins required per season
- `Integer kickpoints_expire_after_days`: Days until kickpoints expire
- `ArrayList<KickpointReason> kickpoint_reasons`: Available kickpoint reason templates

#### Discord Roles (Database Only)
- `String leader_role_id`: Discord role for clan leaders
- `String co_leader_role_id`: Discord role for co-leaders
- `String elder_role_id`: Discord role for elders
- `String member_role_id`: Discord role for members

### Enums

#### Role
```java
public enum Role {
    LEADER,
    COLEADER,
    ELDER,
    MEMBER
}
```

### Core Methods

#### Basic Information
```java
String getTag()              // Get clan tag
String getNameDB()           // Get clan name from database
String getNameAPI()          // Get clan name from CoC API
```

#### Member Lists
```java
ArrayList<Player> getPlayersDB()   // Get members from database (authoritative)
ArrayList<Player> getPlayersAPI()  // Get current members from CoC API
```

#### Clan War
```java
Boolean isCWActive()                     // Check if clan war is active
ArrayList<Player> getWarMemberList()     // Get players in current war
Long getCWEndTime()                      // Get war end timestamp
JSONObject getCWJson()                   // Get raw war data from API
```

**War States (from API)**:
- `"notInWar"`: Not in a war
- `"preparation"`: War preparation phase
- `"inWar"`: War in progress
- `"warEnded"`: War just ended (brief state)

#### Raid Weekend
```java
boolean RaidActive()                     // Check if raid is active
ArrayList<Player> getRaidMemberList()    // Get raid participants
Long getRaidEndTime()                    // Get raid end timestamp
JSONObject getRaidJson()                 // Get raw raid data from API
```

#### Clan War League
```java
Boolean isCWLActive()                    // Check if CWL is active
ArrayList<Player> getCWLMemberList()     // Get CWL roster
Long getCWLDayEndTime()                  // Get current day end timestamp
JSONObject getCWLJson()                  // Get CWL group data from API
JSONObject getCWLDayJson()               // Get current CWL war data from API
```

#### Clan Games
```java
Long getCGEndTime()  // Get Clan Games end timestamp (calculated, not from API)
```

#### Configuration
```java
Long getMaxKickpoints()                      // Get kickpoint limit
Long getMinSeasonWins()                      // Get minimum wins requirement
Integer getDaysKickpointsExpireAfter()       // Get expiration period
ArrayList<KickpointReason> getKickpointReasons()  // Get reason templates
```

#### Discord Roles
```java
String getLeaderRoleID()     // Get Discord role ID for leaders
String getCoLeaderRoleID()   // Get Discord role ID for co-leaders
String getElderRoleID()      // Get Discord role ID for elders
String getMemberRoleID()     // Get Discord role ID for members
```

#### Utility
```java
Clan refreshData()  // Clear all cached data, force reload on next access
```

### Usage Examples

#### Example 1: Check War Status
```java
Clan clan = new Clan("#CLAN123");

if (clan.isCWActive()) {
    System.out.println("Clan is in war!");
    
    ArrayList<Player> warMembers = clan.getWarMemberList();
    System.out.println("War size: " + warMembers.size());
    
    Long endTime = clan.getCWEndTime();
    System.out.println("War ends at: " + new Date(endTime));
    
    // Check for missed attacks
    for (Player player : warMembers) {
        Integer mapPosition = player.getWarMapPosition();
        System.out.println(player.getNameAPI() + " at position " + mapPosition);
    }
} else {
    System.out.println("Clan not in war.");
}
```

#### Example 2: Check Member Synchronization
```java
Clan clan = new Clan("#CLAN123");

ArrayList<Player> dbMembers = clan.getPlayersDB();
ArrayList<Player> apiMembers = clan.getPlayersAPI();

// Find players in API but not DB
ArrayList<Player> needToAdd = new ArrayList<>();
for (Player apiPlayer : apiMembers) {
    boolean found = false;
    for (Player dbPlayer : dbMembers) {
        if (dbPlayer.getTag().equals(apiPlayer.getTag())) {
            found = true;
            break;
        }
    }
    if (!found) {
        needToAdd.add(apiPlayer);
    }
}

System.out.println("Players to add to database:");
for (Player p : needToAdd) {
    System.out.println("  " + p.getNameAPI() + " (" + p.getTag() + ")");
}
```

#### Example 3: Raid Weekend Check
```java
Clan clan = new Clan("#CLAN123");

if (clan.RaidActive()) {
    ArrayList<Player> raidMembers = clan.getRaidMemberList();
    ArrayList<Player> dbMembers = clan.getPlayersDB();
    
    // Find members who didn't participate
    for (Player dbMember : dbMembers) {
        boolean participated = false;
        for (Player raidMember : raidMembers) {
            if (raidMember.getTag().equals(dbMember.getTag())) {
                participated = true;
                break;
            }
        }
        if (!participated) {
            System.out.println(dbMember.getNameDB() + " didn't participate in raid!");
        }
    }
    
    // Check incomplete attacks
    for (Player raidMember : raidMembers) {
        int used = raidMember.getCurrentRaidAttacks();
        int limit = raidMember.getCurrentRaidAttackLimit() + 
                   raidMember.getCurrentRaidbonusAttackLimit();
        if (used < limit) {
            System.out.println(raidMember.getNameAPI() + " has " + 
                             (limit - used) + " attacks remaining!");
        }
    }
}
```

#### Example 4: Kickpoint Configuration
```java
Clan clan = new Clan("#CLAN123");

// Check settings
Long maxKP = clan.getMaxKickpoints();
Integer expireDays = clan.getDaysKickpointsExpireAfter();

System.out.println("Max kickpoints: " + maxKP);
System.out.println("Expire after: " + expireDays + " days");

// List available kickpoint reasons
ArrayList<KickpointReason> reasons = clan.getKickpointReasons();
System.out.println("\nAvailable kickpoint reasons:");
for (KickpointReason reason : reasons) {
    System.out.println("  " + reason.getName() + ": " + reason.getAmount() + " KP");
}
```

#### Example 5: Discord Role Check
```java
Clan clan = new Clan("#CLAN123");
ArrayList<Player> members = clan.getPlayersDB();

for (Player member : members) {
    if (!member.IsLinked()) continue;
    
    User user = member.getUser();
    Member discordMember = guild.getMemberById(user.getUserID());
    if (discordMember == null) continue;
    
    // Determine expected role
    String expectedRoleId = null;
    RoleType role = member.getRoleDB();
    switch (role) {
        case LEADER:
            expectedRoleId = clan.getLeaderRoleID();
            break;
        case COLEADER:
            expectedRoleId = clan.getCoLeaderRoleID();
            break;
        case ELDER:
            expectedRoleId = clan.getElderRoleID();
            break;
        case MEMBER:
            expectedRoleId = clan.getMemberRoleID();
            break;
    }
    
    // Check if member has expected role
    if (expectedRoleId != null) {
        Role discordRole = guild.getRoleById(expectedRoleId);
        if (!discordMember.getRoles().contains(discordRole)) {
            System.out.println(member.getNameDB() + " is missing their role!");
        }
    }
}
```

### Database Representation

**clans table**:
```sql
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
```

### API Endpoints Used

1. **Clan Information**: `GET /clans/{clanTag}`
   - Returns: Name, member list with roles

2. **Current War**: `GET /clans/{clanTag}/currentwar`
   - Returns: War state, members, attacks, end time
   - States: "notInWar", "preparation", "inWar", "warEnded"

3. **CWL Group**: `GET /clans/{clanTag}/currentwar/leaguegroup`
   - Returns: CWL participants, round schedule

4. **Capital Raids**: `GET /clans/{clanTag}/capitalraidseasons`
   - Returns: Raid participants, attacks, loot, end time

### Important Notes

1. **Event Timing Calculation**:
   - Clan Games: Hardcoded dates (22nd-28th monthly)
   - Wars/CWL/Raids: From API `endTime` field
   - All timestamps in milliseconds since epoch (Unix time)

2. **API Response Caching**:
   - API responses cached until `refreshData()` called
   - Clan war state changes require refresh to detect

3. **Member List Synchronization**:
   - Database list is authoritative for "official" membership
   - API list shows current in-game roster
   - Mismatches indicate changes need to be processed

4. **Raid Participation**:
   - Player must have at least 1 attack to appear in raid member list
   - Players with 0 attacks don't appear in API response

5. **CWL Roster**:
   - CWL roster fixed when CWL starts
   - Only rostered players can participate
   - Side clans tracked in separate `sideclans` table

6. **Configuration Defaults**:
   - Settings may be null if not configured
   - Commands require configuration before use
   - Use `/clanconfig` to set values

7. **Discord Roles**:
   - Role IDs may be null if not configured
   - Bot needs "Manage Roles" permission
   - Role hierarchy must allow bot to assign roles

---

## User

**File**: `src/main/java/datawrapper/User.java`

### Purpose
Represents a Discord user and their linked Clash of Clans accounts. Provides role information across multiple clans.

### Constructor
```java
User user = new User("123456789012345678");  // Discord user ID
```

### Fields

- `String userid`: Discord user ID
- `ArrayList<Player> linkedaccs`: All linked player accounts
- `HashMap<String, Player.RoleType> clanroles`: Map of clan tag to role type

### Core Methods

```java
String getUserID()                            // Get Discord user ID
ArrayList<Player> getAllLinkedAccounts()      // Get all linked players
Player getMainAccount()                       // Get primary linked account (first one)
HashMap<String, Player.RoleType> getClanRoles()  // Get role in each clan
User refreshData()                            // Clear cache, reload on next access
```

### Usage Examples

#### Example 1: Check User Permissions
```java
User user = new User(event.getUser().getId());
HashMap<String, Player.RoleType> roles = user.getClanRoles();

boolean isCoLeaderOrHigher = false;
for (String clanTag : roles.keySet()) {
    Player.RoleType role = roles.get(clanTag);
    if (role == Player.RoleType.ADMIN || 
        role == Player.RoleType.LEADER ||
        role == Player.RoleType.COLEADER) {
        isCoLeaderOrHigher = true;
        break;
    }
}

if (!isCoLeaderOrHigher) {
    event.reply("You need to be at least Co-Leader to use this command!").queue();
    return;
}
```

#### Example 2: List User's Accounts
```java
User user = new User(discordUserId);
ArrayList<Player> accounts = user.getAllLinkedAccounts();

if (accounts.isEmpty()) {
    System.out.println("User has no linked accounts.");
} else {
    System.out.println("Linked accounts:");
    for (Player player : accounts) {
        Clan clan = player.getClanDB();
        String clanName = (clan != null) ? clan.getNameDB() : "No clan";
        System.out.println("  " + player.getNameDB() + " (" + player.getTag() + ") - " + clanName);
    }
}
```

#### Example 3: Get Main Account
```java
User user = new User(discordUserId);
Player mainAccount = user.getMainAccount();

if (mainAccount != null) {
    System.out.println("Main account: " + mainAccount.getNameDB());
    
    // Set Discord nickname to main account name
    Member member = guild.getMemberById(discordUserId);
    if (member != null) {
        member.modifyNickname(mainAccount.getNameAPI()).queue();
    }
}
```

### Database Representation

User data is derived from the `players` table:
```sql
SELECT coc_tag FROM players WHERE discord_id = ?
```

Multiple players can have the same `discord_id`, representing linked accounts.

### Important Notes

1. **Multi-Account Support**: Users can link multiple CoC accounts to one Discord account.

2. **Main Account**: First linked account is considered "main" for nickname purposes.

3. **Clan Roles Map**: 
   - Key: Clan tag (e.g., "#CLAN123")
   - Value: Player.RoleType (role in that clan)
   - User can have different roles in different clans

4. **Permission Checks**: Most commands check for Co-Leader or higher in ANY clan, not just the specific clan being operated on.

5. **No Dedicated Users Table**: User data is derived from player linking, not stored separately (minimal users table exists but mostly unused).

---

## Kickpoint

**File**: `src/main/java/datawrapper/Kickpoint.java`

### Purpose
Represents a single kickpoint record - a penalty assigned to a player for rule violations.

### Constructor
```java
Kickpoint kp = new Kickpoint(kickpointId);
```

### Fields

- `Long id`: Unique kickpoint ID (database primary key)
- `String playertag`: Player who received the kickpoint
- `String reason`: Why it was given
- `Integer amount`: Point value
- `Timestamp date`: When it was given
- `Timestamp expires`: When it expires

### Core Methods

```java
Long getID()               // Get kickpoint ID
String getPlayerTag()      // Get player tag
String getReason()         // Get reason text
Integer getAmount()        // Get point value
Timestamp getDate()        // Get creation date
Timestamp getExpires()     // Get expiration date
boolean isExpired()        // Check if expired (expires < now)
```

### Usage Examples

#### Example 1: Display Kickpoints
```java
Player player = new Player("#ABC123");
ArrayList<Kickpoint> kickpoints = player.getKickpoints();

System.out.println("Active kickpoints for " + player.getNameDB() + ":");
for (Kickpoint kp : kickpoints) {
    System.out.println("ID " + kp.getID() + ": " + 
                      kp.getReason() + " - " + 
                      kp.getAmount() + " KP");
    System.out.println("  Given: " + kp.getDate());
    System.out.println("  Expires: " + kp.getExpires());
}

Long total = player.getKickpointsTotal();
System.out.println("Total: " + total + " KP");
```

#### Example 2: Check Expiration
```java
Kickpoint kp = new Kickpoint(kickpointId);

if (kp.isExpired()) {
    System.out.println("This kickpoint has expired and won't count.");
} else {
    long timeUntilExpiry = kp.getExpires().getTime() - System.currentTimeMillis();
    long daysUntilExpiry = timeUntilExpiry / (1000 * 60 * 60 * 24);
    System.out.println("Expires in " + daysUntilExpiry + " days");
}
```

### Database Representation

**kickpoints table**:
```sql
CREATE TABLE kickpoints (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    reason TEXT NOT NULL,
    amount INTEGER NOT NULL,
    date TIMESTAMP NOT NULL,
    expires TIMESTAMP NOT NULL,
    FOREIGN KEY (player_tag) REFERENCES players(coc_tag)
);
```

### Important Notes

1. **Expiration**: 
   - Expired kickpoints are not deleted
   - They remain in database for record-keeping
   - `getKickpoints()` automatically excludes expired ones
   - `getKickpointsTotal()` only sums non-expired kickpoints

2. **Calculation**:
   ```
   expires = date + (clan.kickpoints_expire_after_days * 1 day)
   ```

3. **Editing**: Kickpoints can be edited after creation via `/kpedit` command.

4. **Removal**: Kickpoints can be manually removed via `/kpremove` command (doesn't just expire them, deletes the record).

---

## KickpointReason

**File**: `src/main/java/datawrapper/KickpointReason.java`

### Purpose
Represents a kickpoint reason template - a pre-configured reason with a default point value for a specific clan.

### Constructor
```java
KickpointReason reason = new KickpointReason(reasonName, clanTag);
```

### Fields

- `String name`: Reason name/description (e.g., "CW_Attack_vergessen")
- `Integer amount`: Default kickpoint amount
- `String clan_tag`: Clan this reason belongs to

### Core Methods

```java
String getName()      // Get reason name
Integer getAmount()   // Get default point value
String getClanTag()   // Get clan tag
```

### Usage Examples

#### Example 1: Create Reason Template
```java
// Via /kpaddreason command
String clanTag = "#CLAN123";
String reasonName = "CW Attack Missed";
Integer amount = 2;

DBUtil.executeUpdate(
    "INSERT INTO kickpoint_reasons (name, clan_tag, amount) VALUES (?, ?, ?)",
    reasonName, clanTag, amount
);
```

#### Example 2: List Clan's Reasons
```java
Clan clan = new Clan("#CLAN123");
ArrayList<KickpointReason> reasons = clan.getKickpointReasons();

System.out.println("Kickpoint reasons for " + clan.getNameDB() + ":");
for (KickpointReason reason : reasons) {
    System.out.println("  " + reason.getName() + ": " + reason.getAmount() + " KP");
}
```

#### Example 3: Use Reason Template
```java
// When adding kickpoint, user selects reason from autocomplete
String reasonName = "CW Attack Missed";
String clanTag = playerClan.getTag();

KickpointReason reason = new KickpointReason(reasonName, clanTag);
Integer defaultAmount = reason.getAmount();

// Pre-fill modal with default amount
// User can still override if needed
```

### Database Representation

**kickpoint_reasons table**:
```sql
CREATE TABLE kickpoint_reasons (
    name TEXT NOT NULL,
    clan_tag TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (name, clan_tag),
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag)
);
```

### Important Notes

1. **Clan-Specific**: Each clan has its own set of reason templates. Same reason name can exist in multiple clans with different amounts.

2. **Autocomplete**: Reason templates appear in autocomplete when adding kickpoints, filtered by the player's clan.

3. **Template Only**: Reason templates don't enforce the amount - they just provide defaults. Users can override when adding kickpoints.

4. **Management Commands**:
   - Create: `/kpaddreason`
   - Delete: `/kpremovereason`
   - Edit: `/kpeditreason`

---

## ListeningEvent

**File**: `src/main/java/datawrapper/ListeningEvent.java`

### Purpose
Represents an automated event monitoring configuration. When triggered, performs actions like sending messages or adding kickpoints based on clan event data (wars, raids, clan games).

### Constructor
```java
ListeningEvent event = new ListeningEvent(eventId);
```

### Fields

- `Long id`: Event ID (database primary key)
- `String clan_tag`: Clan to monitor
- `LISTENINGTYPE listeningtype`: Type of event (CS, CW, CWLDAY, RAID, etc.)
- `Long durationuntilend`: Milliseconds before event end to trigger (-1 for "start" triggers)
- `ACTIONTYPE actiontype`: Action to perform (INFOMESSAGE, KICKPOINT, CWDONATOR, etc.)
- `String channelid`: Discord channel for notifications
- `ArrayList<ActionValue> actionvalues`: Configuration for actions (JSON)
- `Long timestamptofire`: Calculated timestamp when event should fire

### Enums

#### LISTENINGTYPE
```java
public enum LISTENINGTYPE {
    CS,              // Clan Games
    CW,              // Clan War
    CWLDAY,          // CWL Day
    RAID,            // Raid Weekend
    FIXTIMEINTERVAL, // Fixed time interval (not currently used)
    CWLEND           // CWL End (entire CWL)
}
```

#### ACTIONTYPE
```java
public enum ACTIONTYPE {
    INFOMESSAGE,   // Send informational message only
    CUSTOMMESSAGE, // Send custom message (placeholder for future)
    KICKPOINT,     // Add kickpoints to violators
    CWDONATOR,     // Select war donors
    FILLER,        // Check war fillers (opted-out players)
    RAIDFAILS      // Analyze raid district attacks
}
```

### Core Methods

#### Basic Information
```java
Long getID()                    // Get event ID
String getClanTag()             // Get clan tag
LISTENINGTYPE getListeningType() // Get event type
long getDurationUntilEnd()      // Get duration offset (milliseconds)
ACTIONTYPE getActionType()      // Get action type
String getChannelID()           // Get Discord channel ID
ArrayList<ActionValue> getActionValues()  // Get action configuration
Long getTimestamp()             // Get calculated fire timestamp
```

#### Event Execution
```java
void fireEvent()  // Execute the event (called by scheduler)
```

#### Utility
```java
ListeningEvent refreshData()  // Clear cache, reload on next access
```

### Event Firing Process

When `fireEvent()` is called:

1. **Validate**: Check if event should still fire (game state validation)
2. **Query API**: Fetch event data from CoC API
3. **Process**: Analyze data based on event type
4. **Build Message**: Create Discord embed with results
5. **Send**: Post message to configured channel
6. **Execute Actions**: Apply kickpoints, etc. if configured

### Event Type Details

#### Clan Games (CS)
- **Timing**: Based on hardcoded dates (22nd-28th monthly)
- **Data Source**: `achievement_data` table (snapshots at start/end)
- **Process**:
  1. Get Clan Games start/end timestamps
  2. Query achievement data for clan members at both timestamps
  3. Calculate points gained per player
  4. Identify players below threshold (e.g., < 4000 points)
  5. Send message listing low performers
  6. Add kickpoints if action type is KICKPOINT

#### Clan War (CW)
- **Timing**: Based on war end time from API
- **Special Case**: `duration = -1` triggers on war START (state change detection)
- **Data Source**: `/currentwar` API endpoint
- **Processes**:
  
  **Filler Check** (usually at start):
  - Lists members opted OUT of war
  - Used for roster management
  - Action type: FILLER or CWDONATOR with FILLER value
  
  **Missed Attacks** (at end):
  - Identifies members who didn't use all attacks
  - Action type: INFOMESSAGE or KICKPOINT
  - Excludes fillers (tracked in `cw_fillers` table)

#### CWL Day (CWLDAY)
- **Timing**: Based on current CWL war end time
- **Data Source**: `/currentwar/leaguegroup` and `/currentwar` API endpoints
- **Process**:
  1. Get CWL roster and current war
  2. Identify rostered players who didn't attack
  3. Send message listing no-shows
  4. Add kickpoints if configured

#### Raid Weekend (RAID)
- **Timing**: Based on raid end time from API
- **Data Source**: `/capitalraidseasons` API endpoint
- **Processes**:
  
  **Missed Attacks**:
  - Lists players who didn't complete all attacks
  - Lists players who didn't participate at all
  - Action type: INFOMESSAGE or KICKPOINT
  
  **Raidfails** (District Analysis):
  - Analyzes attack distribution across districts
  - Identifies over-attacked districts
  - Lists worst offenders or adds kickpoints
  - Action type: RAIDFAILS
  - Configuration via action values:
    - `capitalPeakMax`: Max attacks on Capital Peak
    - `otherDistrictsMax`: Max attacks on other districts
    - `penalizeTies`: Tie-breaker behavior (kickpoints only)

### Usage Examples

#### Example 1: Create Clan Games Event
```java
// Clan Games event - check at end, add kickpoints for < 4000 points
ListeningEvent event = new ListeningEvent(0); // ID auto-assigned
event.setClanTag("#CLAN123");
event.setListeningType(LISTENINGTYPE.CS);
event.setDurationUntilEnd(0L); // At end
event.setActionType(ACTIONTYPE.KICKPOINT);
event.setChannelID("123456789012345678");

// Action values: kickpoint reason
ArrayList<ActionValue> actions = new ArrayList<>();
ActionValue action = new ActionValue();
KickpointReason reason = new KickpointReason("Clangames_unter_4000", "#CLAN123");
action.setReason(reason);
actions.add(action);
event.setActionValues(actions);

// Save to database
ObjectMapper mapper = new ObjectMapper();
String actionValuesJson = mapper.writeValueAsString(actions);
DBUtil.executeUpdate(
    "INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) " +
    "VALUES (?, ?, ?, ?, ?, ?::jsonb)",
    "#CLAN123", "cs", 0L, "kickpoint", "123456789012345678", actionValuesJson
);
```

#### Example 2: Create War Start Event (Filler Check)
```java
// War start event - check fillers when war starts
ListeningEvent event = new ListeningEvent(0);
event.setClanTag("#CLAN123");
event.setListeningType(LISTENINGTYPE.CW);
event.setDurationUntilEnd(-1L); // Special: fire on war start
event.setActionType(ACTIONTYPE.FILLER);
event.setChannelID("123456789012345678");

// Action values: type FILLER
ArrayList<ActionValue> actions = new ArrayList<>();
ActionValue action = new ActionValue();
action.setType("FILLER");
actions.add(action);
event.setActionValues(actions);

// Save to database
String actionValuesJson = new ObjectMapper().writeValueAsString(actions);
DBUtil.executeUpdate(
    "INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) " +
    "VALUES (?, ?, ?, ?, ?, ?::jsonb)",
    "#CLAN123", "cw", -1L, "filler", "123456789012345678", actionValuesJson
);
```

#### Example 3: Fire Event Manually (Testing)
```java
ListeningEvent event = new ListeningEvent(eventId);

try {
    event.fireEvent();
    System.out.println("Event executed successfully!");
} catch (Exception e) {
    System.err.println("Event failed: " + e.getMessage());
    e.printStackTrace();
}
```

### Database Representation

**listening_events table**:
```sql
CREATE TABLE listening_events (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    listeningtype TEXT NOT NULL,  -- "cs", "cw", "cwlday", "raid", "fixtimeinterval", "cwlend"
    listeningvalue BIGINT NOT NULL, -- Duration in milliseconds (-1 for start triggers)
    actiontype TEXT NOT NULL,      -- "infomessage", "kickpoint", "cwdonator", "filler", "raidfails"
    channel_id TEXT NOT NULL,
    actionvalues JSONB,            -- JSON array of ActionValue objects
    FOREIGN KEY (clan_tag) REFERENCES clans(clan_tag)
);
```

### Scheduling System

Events are scheduled by the event polling system in `Bot.java`:

1. **Polling Frequency**: Every 2 minutes
2. **Scheduling Threshold**: Events within 5 minutes of fire time are scheduled
3. **Execution**: Events run in `schedulertasks` executor with retry logic (3 attempts)
4. **Start Triggers**: Special handling for `duration = -1` (war start detection)

**Start Trigger Flow**:
1. Polling system queries clan war state every 2 minutes
2. Compares to last known state (tracked in-memory)
3. If state changed from "notInWar"/"warEnded" to "preparation"/"inWar":
   - Fire all start events for that clan
   - Update last known state
4. If war ends, clear fired events tracker

### Action Values Format

Action values are stored as JSON in the database. Structure depends on action type:

**Kickpoint**:
```json
[{
    "reason": {
        "name": "CW Attack Missed",
        "clan_tag": "#CLAN123"
    }
}]
```

**Filler**:
```json
[{
    "type": "FILLER"
}]
```

**Raidfails**:
```json
[{
    "value": 5,           // capitalPeakMax
    "value2": 3,          // otherDistrictsMax  
    "penalizeTies": true  // Only if kickpoint_reason provided
}]
```

**Multiple Actions**:
```json
[
    {"type": "REMINDER"},
    {"value": 4000}
]
```

### Important Notes

1. **State Validation**: Before firing, events check if the game state is still valid (e.g., war is still active).

2. **Retry Logic**: Failed events retry 3 times with exponential backoff (5s, 10s, 20s).

3. **Overdue Events**: Events that are overdue on bot restart are skipped to prevent spam.

4. **Duration Calculation**:
   - `duration = 0`: Fire exactly at event end
   - `duration = 3600000`: Fire 1 hour before end
   - `duration = -1`: Fire at event start (state change)

5. **API Propagation Delay**: Clan Games events fire 1 hour after actual end (13:00 instead of 12:00) to allow API data to propagate.

6. **Filler Tracking**: When a filler check runs, players opted out are saved to `cw_fillers` table to exclude them from missed attack tracking.

7. **Kickpoint Integration**: When action type is KICKPOINT, the bot:
   - Uses the kickpoint reason from action values
   - Adds kickpoints automatically to violators
   - Respects clan's expiration settings
   - Checks kickpoint limits

8. **Message Format**: Messages are sent as Discord embeds with:
   - Title based on event type
   - Description with results
   - Timestamp
   - Color coding (red for violations, blue for info)

---

## AchievementData

**File**: `src/main/java/datawrapper/AchievementData.java`

### Purpose
Represents a snapshot of player achievements at a specific point in time. Used for historical tracking of Clan Games points, wins, donations, etc.

### Constructor
```java
AchievementData data = new AchievementData(playerTag, type, timestamp);
```

### Fields

- `String player_tag`: Player tag
- `Type type`: Achievement type (CLANGAMES_POINTS, WINS, etc.)
- `Timestamp time`: When snapshot was taken
- `JSONObject data`: Achievement data (structure varies by type)

### Enums

#### Type
```java
public enum Type {
    CLANGAMES_POINTS,  // Clan Games points
    WINS,              // Total wins
    DONATIONS,         // Donations given
    // ... potentially other achievement types
}
```

### Core Methods

```java
String getPlayerTag()    // Get player tag
Type getType()           // Get achievement type
Timestamp getTime()      // Get snapshot timestamp
JSONObject getData()     // Get achievement data
```

### Usage Examples

#### Example 1: Save Clan Games Snapshot
```java
Player player = new Player("#ABC123");
Timestamp timestamp = Timestamp.from(Bot.getPrevious22thAt7am().toInstant());

player.addAchievementDataToDB(AchievementData.Type.CLANGAMES_POINTS, timestamp);
```

#### Example 2: Calculate Clan Games Progress
```java
Player player = new Player("#ABC123");

// Get start and end timestamps
Timestamp startTime = Timestamp.from(Bot.getPrevious22thAt7am().toInstant());
Timestamp endTime = Timestamp.from(Bot.getPrevious28thAt12pm().toInstant());

// Get achievement data
HashMap<Type, ArrayList<AchievementData>> history = player.getAchievementDatasInDB();
ArrayList<AchievementData> cgData = history.get(AchievementData.Type.CLANGAMES_POINTS);

// Find start and end snapshots
Integer startPoints = null;
Integer endPoints = null;

for (AchievementData data : cgData) {
    if (data.getTime().equals(startTime)) {
        startPoints = data.getData().getInt("value");
    }
    if (data.getTime().equals(endTime)) {
        endPoints = data.getData().getInt("value");
    }
}

if (startPoints != null && endPoints != null) {
    int gained = endPoints - startPoints;
    System.out.println("Clan Games points gained: " + gained);
    
    if (gained < 4000) {
        System.out.println("Player below minimum (4000)!");
    }
}
```

#### Example 3: Track Wins Over Season
```java
Player player = new Player("#ABC123");
Timestamp seasonStart = util.SeasonUtil.fetchSeasonStartTime();
Timestamp seasonEnd = util.SeasonUtil.fetchSeasonEndTime();

// Get wins history
HashMap<Type, ArrayList<AchievementData>> history = player.getAchievementDatasInDB();
ArrayList<AchievementData> winsData = history.get(AchievementData.Type.WINS);

// Find season start and end
Integer startWins = null;
Integer endWins = null;

for (AchievementData data : winsData) {
    if (data.getTime().getTime() >= seasonStart.getTime() && 
        data.getTime().getTime() <= seasonEnd.getTime()) {
        if (startWins == null) startWins = data.getData().getInt("value");
        endWins = data.getData().getInt("value");
    }
}

if (startWins != null && endWins != null) {
    int winsGained = endWins - startWins;
    System.out.println("Wins this season: " + winsGained);
}
```

### Database Representation

**achievement_data table**:
```sql
CREATE TABLE achievement_data (
    id BIGSERIAL PRIMARY KEY,
    player_tag TEXT NOT NULL,
    type TEXT NOT NULL,
    time TIMESTAMP NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(player_tag, type, time)
);
CREATE INDEX idx_achievement_data_lookup ON achievement_data(player_tag, type, time);
```

See: `achievement_data_schema.sql` for full schema with cleanup functions.

### Data Formats

#### Clan Games Points
```json
{
    "value": 5000
}
```

#### Wins
```json
{
    "value": 1234
}
```

### Automatic Collection

The bot automatically collects achievement data at specific times:

1. **Clan Games**:
   - Start: 22nd of month at 7:00 AM
   - End: 28th of month at 12:00 PM
   - Type: CLANGAMES_POINTS

2. **Season Wins**:
   - Start: Beginning of each season (from API)
   - End: End of each season (from API)
   - Type: WINS

3. **Player Linking**:
   - When a player is linked via `/verify`, initial wins snapshot is saved

### Important Notes

1. **UNIQUE Constraint**: Each player can only have one snapshot per type per timestamp. Duplicate inserts are ignored.

2. **Auto-Cleanup**: Schema includes cleanup function to remove data older than 6 months (see `achievement_data_schema.sql`).

3. **JSONB Storage**: Data stored as JSONB for efficient querying and flexible schema.

4. **Historical Comparison**: Most useful for comparing two snapshots to calculate differences (gains/losses).

5. **Manual Snapshots**: Can manually call `player.addAchievementDataToDB()` to save custom snapshots.

---

## ActionValue

**File**: `src/main/java/datawrapper/ActionValue.java`

### Purpose
Represents configuration for a listening event action. Stored as JSON in `listening_events.actionvalues` field.

### Fields

- `String type`: Action subtype (e.g., "FILLER")
- `KickpointReason reason`: Kickpoint reason to use (if action is KICKPOINT)
- `Integer value`: Numeric configuration value (e.g., threshold, max attacks)
- `Integer value2`: Second numeric value (for raidfails)
- `Boolean penalizeTies`: Tie-breaker behavior (for raidfails with kickpoints)

### Usage

ActionValue objects are created and serialized to JSON for storage:

```java
ArrayList<ActionValue> actions = new ArrayList<>();

// For kickpoints
ActionValue kickpointAction = new ActionValue();
KickpointReason reason = new KickpointReason("CW_Attack_Missed", "#CLAN123");
kickpointAction.setReason(reason);
actions.add(kickpointAction);

// For filler check
ActionValue fillerAction = new ActionValue();
fillerAction.setType("FILLER");
actions.add(fillerAction);

// For raidfails
ActionValue raidfailsAction = new ActionValue();
raidfailsAction.setValue(5);        // capitalPeakMax
raidfailsAction.setValue2(3);       // otherDistrictsMax
raidfailsAction.setPenalizeTies(true);
actions.add(raidfailsAction);

// Serialize and save
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(actions);
DBUtil.executeUpdate(
    "UPDATE listening_events SET actionvalues = ?::jsonb WHERE id = ?",
    json, eventId
);
```

### Retrieval

```java
ListeningEvent event = new ListeningEvent(eventId);
ArrayList<ActionValue> actions = event.getActionValues();

for (ActionValue action : actions) {
    if (action.getType() != null) {
        System.out.println("Type: " + action.getType());
    }
    if (action.getReason() != null) {
        System.out.println("Kickpoint reason: " + action.getReason().getName());
    }
    if (action.getValue() != null) {
        System.out.println("Value: " + action.getValue());
    }
}
```

### Important Notes

1. **JSON Serialization**: Uses Jackson ObjectMapper for serialization/deserialization.

2. **Nullable Fields**: Not all fields are used for all action types. Check for null before accessing.

3. **Multiple Actions**: An event can have multiple ActionValue objects in its actionvalues array.

4. **Manual Creation**: Usually created programmatically via commands, not manually edited.

---

**End of Data Structures Documentation**

This completes the comprehensive documentation of all data wrapper classes in LostManager2.
