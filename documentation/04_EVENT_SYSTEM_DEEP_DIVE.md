# Event System - Deep Dive

This document provides an in-depth technical explanation of LostManager2's automated event monitoring and scheduling system.

---

## Table of Contents
1. [System Overview](#system-overview)
2. [Architecture](#architecture)
3. [Event Polling System](#event-polling-system)
4. [Event Types](#event-types)
5. [Timestamp Calculation](#timestamp-calculation)
6. [Execution Flow](#execution-flow)
7. [State Management](#state-management)
8. [Error Handling and Retry Logic](#error-handling-and-retry-logic)
9. [Performance Considerations](#performance-considerations)

---

## System Overview

### Purpose
The event system automates monitoring of Clash of Clans clan events and performs actions based on configured rules. It eliminates manual tracking of:
- Clan Games participation
- War attacks
- CWL participation
- Raid Weekend attacks

### Key Features
- **Automated Detection**: Polls CoC API every 2 minutes
- **Flexible Actions**: Info messages, kickpoints, donor selection, etc.
- **State Validation**: Checks game state before firing
- **Retry Logic**: 3 attempts with exponential backoff
- **Start Triggers**: Special handling for war start detection
- **Overdue Handling**: Skips old events after bot restart

---

## Architecture

### Components

#### 1. Schedulers (Bot.java)
Three separate `ScheduledExecutorService` instances:

**schedulernames** (2 hour interval):
```java
schedulernames = Executors.newSingleThreadScheduledExecutor();
schedulernames.scheduleAtFixedRate(nameUpdateTask, 0, 2, TimeUnit.HOURS);
```
- Purpose: Update player names from API
- Not related to events, but part of scheduling system

**schedulertasks** (event executor):
```java
schedulertasks = Executors.newSingleThreadScheduledExecutor();
```
- Purpose: Execute all timed tasks
- Used for: Clan Games, Season Wins, Individual Events
- Single-threaded to prevent race conditions

**Event Polling** (2 minute interval):
```java
schedulertasks.scheduleAtFixedRate(pollingTask, 0, 2, TimeUnit.MINUTES);
```
- Purpose: Check all events, schedule upcoming ones
- Runs immediately on startup, then every 2 minutes

#### 2. Event Classes

**ListeningEvent** (`datawrapper/ListeningEvent.java`):
- Represents event configuration from database
- Provides `fireEvent()` method for execution
- Calculates timestamps based on event type

**ActionValue** (`datawrapper/ActionValue.java`):
- Configuration for event actions
- Serialized as JSONB in database

#### 3. Database Integration

**listening_events table**:
```sql
CREATE TABLE listening_events (
    id BIGSERIAL PRIMARY KEY,
    clan_tag TEXT NOT NULL,
    listeningtype TEXT NOT NULL,
    listeningvalue BIGINT NOT NULL,
    actiontype TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    actionvalues JSONB
);
```

---

## Event Polling System

### Polling Task Structure

**Location**: `Bot.java` → `startEventPolling()` → `pollingTask`

**Execution**:
```java
Runnable pollingTask = () -> {
    // 1. Query all events from database
    // 2. Calculate fire timestamps
    // 3. Schedule events within 5 minutes
    // 4. Handle start triggers (CW start detection)
    // 5. Clean up old scheduled events
};

schedulertasks.scheduleAtFixedRate(pollingTask, 0, 2, TimeUnit.MINUTES);
```

### Polling Algorithm

**Pseudocode**:
```
EVERY 2 MINUTES:
    
    1. LOAD events from database
    
    2. FOR EACH event:
        IF event.duration == -1:  // Start trigger
            Add to cwStartEvents[clan_tag]
            CONTINUE to next event
        
        IF already scheduled:
            SKIP
        
        fire_time = event.getTimestamp()
        
        IF fire_time is null or invalid:
            SKIP
        
        time_until_fire = fire_time - now
        
        IF time_until_fire <= 5 minutes AND time_until_fire > 0:
            SCHEDULE event at fire_time
            MARK as scheduled
        
        ELSE IF time_until_fire <= 0:
            MARK as scheduled (skip overdue)
    
    3. FOR EACH clan WITH start events:
        current_state = API.getWarState(clan)
        last_state = memory[clan]
        
        IF state_changed_to_war:
            FOR EACH start event:
                IF not fired_this_war:
                    FIRE event
                    MARK as fired
            UPDATE last_state
        
        ELSE IF war_ended:
            CLEAR fired_events[clan]
            UPDATE last_state
    
    4. CLEANUP old scheduled events (>1 hour past fire time)
```

### Scheduling Threshold

**Why 5 Minutes?**
- Balance between responsiveness and API calls
- Ensures events aren't missed if poll hits exactly at fire time
- Allows for minor timing variations
- Prevents spam scheduling of same event

**Example**:
```
Event fire time: 14:30:00
Current time: 14:26:00 (4 min before)
Action: Schedule event

Current time: 14:31:00 (1 min after)
Action: Skip (overdue, likely already fired or bot was down)
```

---

## Event Types

### Clan Games (CS)

**Timing**: Hardcoded dates, not from API

**Start**: 22nd of month at 7:00 AM
**End**: 28th of month at 12:00 PM
**Fire**: 28th at 1:00 PM (1 hour buffer for API propagation)

**Timestamp Calculation**:
```java
Long getTimestamp() {
    if (getListeningType() == LISTENINGTYPE.CS) {
        ZonedDateTime cgEnd = Bot.getNext28thAt1pm();
        timestamptofire = cgEnd.toInstant().toEpochMilli() - getDurationUntilEnd();
    }
}
```

**Fire Process**:
```
1. Get start time: getPrevious22thAt7am()
2. Get end time: getPrevious28thAt12pm()
3. Query achievement_data for clan members at both times
4. Calculate points difference for each player
5. Identify players below threshold (e.g., < 4000)
6. Build message listing violators
7. Send to channel
8. If actiontype=KICKPOINT: Add kickpoints to violators
```

**Example**:
```
Event Configuration:
- Clan: LOST F2P
- Type: CS
- Duration: 0 (fire at end)
- Action: kickpoint
- Reason: Clangames_unter_4000

Execution (January 28, 1:00 PM):
1. Start time: January 22, 7:00 AM
2. End time: January 28, 12:00 PM
3. Query: SELECT data FROM achievement_data 
         WHERE player_tag IN (clan members)
           AND type = 'CLANGAMES_POINTS'
           AND time IN (start_time, end_time)
4. Calculate: end_points - start_points for each
5. Violators: Where difference < 4000
6. Message: "Player1 (2500 points), Player2 (3000 points)..."
7. Kickpoints: Add 3 KP to each violator
```

---

### Clan War (CW)

**Timing**: From CoC API `/currentwar` endpoint

**States**:
- `notInWar`: Not in a war
- `preparation`: Preparation phase
- `inWar`: War in progress
- `warEnded`: Just ended (brief)

**Special: Start Triggers** (duration = -1)

**Detection**:
```
IF last_state IN ('notInWar', 'warEnded')
   AND current_state IN ('preparation', 'inWar'):
    War just started!
```

**Timestamp Calculation**:
```java
Long getTimestamp() {
    if (getDurationUntilEnd() == -1) {
        // Start trigger - return far future to prevent normal scheduling
        return Long.MAX_VALUE;
    }
    
    if (getListeningType() == LISTENINGTYPE.CW) {
        Clan clan = new Clan(getClanTag());
        if (!clan.isCWActive()) {
            return null;  // No active war
        }
        Long cwEndTime = clan.getCWEndTime();
        timestamptofire = cwEndTime - getDurationUntilEnd();
    }
}
```

**Fire Process (Missed Attacks)**:
```
1. Get war members from API
2. For each member:
   a. Check attacks_used vs attacks_available
   b. Check if player is filler (in cw_fillers table)
   c. If attacks incomplete and not filler: Add to violators
3. Build message listing violators
4. Send to channel
5. If actiontype=KICKPOINT: Add kickpoints
```

**Fire Process (Filler Check)**:
```
1. Get all clan members from database
2. Get war roster from API
3. For each database member:
   a. If NOT in war roster: They're opted out (filler)
   b. Add to fillers list
4. Build message listing fillers
5. Save fillers to cw_fillers table (for exclusion in attack check)
6. Send to channel
```

**Example (End Event)**:
```
Event Configuration:
- Clan: LOST F2P
- Type: CW
- Duration: 0
- Action: kickpoint
- Reason: CW_Attack_vergessen

API Response:
{
  "state": "inWar",
  "endTime": "20240120T143000.000Z",
  "teamSize": 30,
  "clan": {
    "members": [
      {"tag": "#ABC", "attacks": 1},
      {"tag": "#DEF", "attacks": 2},
      {"tag": "#GHI", "attacks": 0}
    ]
  }
}

Execution (at war end):
1. War ends: 2024-01-20 14:30
2. Fire time: 14:30 (duration = 0)
3. Check attacks:
   - #ABC: 1/2 attacks → VIOLATOR
   - #DEF: 2/2 attacks → OK
   - #GHI: 0/2 attacks, but in cw_fillers → SKIP
4. Message: "#ABC missed 1 attack"
5. Kickpoint: Add 2 KP to #ABC
```

**Example (Start Event)**:
```
Event Configuration:
- Clan: LOST F2P
- Type: CW
- Duration: -1 (start trigger)
- Action: filler
- Reason: none

State Detection:
Poll 1 (14:00): state = "notInWar"
Poll 2 (14:02): state = "preparation" → WAR STARTED!

Execution (immediately at 14:02):
1. Get database members: 30 players
2. Get war roster: 25 players
3. Calculate fillers: 5 players not in roster
4. Message: "Fillers: Player1, Player2, Player3, Player4, Player5"
5. Save to cw_fillers table
```

---

### CWL Day (CWLDAY)

**Timing**: From CoC API `/currentwar/leaguegroup` and `/currentwar`

**Detection**: 
```
1. Check if CWL active via leaguegroup endpoint
2. Get current CWL war via currentwar endpoint
3. War end time is the "day" end time
```

**Timestamp Calculation**:
```java
Long getTimestamp() {
    if (getListeningType() == LISTENINGTYPE.CWLDAY) {
        Clan clan = new Clan(getClanTag());
        if (!clan.isCWLActive()) {
            return null;
        }
        Long cwlDayEndTime = clan.getCWLDayEndTime();
        timestamptofire = cwlDayEndTime - getDurationUntilEnd();
    }
}
```

**Fire Process**:
```
1. Get CWL roster from leaguegroup
2. Get current war from currentwar
3. For each rostered player:
   a. Check if they attacked (attacks_used > 0)
   b. If no attack: Add to violators
4. Build message listing violators
5. Send to channel
6. If actiontype=KICKPOINT: Add kickpoints
```

**Example**:
```
Event Configuration:
- Clan: LOST F2P
- Type: CWLDAY
- Duration: 0
- Action: kickpoint
- Reason: CWL_Angriff_verpasst

CWL State:
- Round: 3/7
- War ends: 2024-01-20 16:00
- Roster: 15 players

Execution (at 16:00):
1. Get roster: 15 players
2. Check attacks:
   - 13 players attacked
   - 2 players didn't attack
3. Message: "CWL Day 3: Player1, Player2 didn't attack"
4. Kickpoints: Add 2 KP to each
```

---

### Raid Weekend (RAID)

**Timing**: From CoC API `/capitalraidseasons`

**Detection**:
```
1. Query raid seasons (returns list, newest first)
2. Check if state = "ongoing"
3. Get endTime from API
```

**Timestamp Calculation**:
```java
Long getTimestamp() {
    if (getListeningType() == LISTENINGTYPE.RAID) {
        Clan clan = new Clan(getClanTag());
        if (!clan.RaidActive()) {
            return null;
        }
        Long raidEndTime = clan.getRaidEndTime();
        timestamptofire = raidEndTime - getDurationUntilEnd();
    }
}
```

**Fire Process (Missed Attacks)**:
```
1. Get raid members from API (players with attacks > 0)
2. Get database members
3. For each database member:
   a. If not in raid members: Didn't participate
   b. Else: Check attacks_used vs (attackLimit + bonusAttackLimit)
   c. If incomplete: Add to violators
4. Build message with two sections:
   - Incomplete attacks
   - Didn't participate
5. Send to channel
6. If actiontype=KICKPOINT: Add kickpoints to all violators
```

**Fire Process (Raidfails - District Analysis)**:
```
1. Get action values: capitalPeakMax, otherDistrictsMax, penalizeTies
2. Get raid data from API
3. For each district:
   a. Count total attacks on district
   b. If district is Capital Peak:
      - If attacks > capitalPeakMax: Over-attacked
   c. Else:
      - If attacks > otherDistrictsMax: Over-attacked
4. For each over-attacked district:
   a. Get list of attackers and attack counts
   b. If kickpoint_reason provided:
      - Find player(s) with most attacks
      - If tie and penalizeTies=true: Penalize all tied
      - If tie and penalizeTies=false: Penalize none
      - Add kickpoints
   c. Else:
      - Just list all attackers (info only)
5. Build message and send
```

**Example (Missed Attacks)**:
```
Event Configuration:
- Clan: LOST F2P
- Type: RAID
- Duration: 0
- Action: kickpoint
- Reason: Raid_nicht_gemacht

Raid State:
- Ends: 2024-01-22 07:00
- 25 members participated
- 5 members didn't participate

Execution (at 07:00):
1. Raid members: 25 players
2. Check attacks:
   - Player1: 5/6 attacks (incomplete)
   - Player2: 4/6 attacks (incomplete)
   - Player3: 6/6 attacks (complete)
   - ...
3. Database members not in raid: 5 players
4. Message:
   "Incomplete Attacks:
    Player1 (5/6), Player2 (4/6)
    
    Didn't Participate:
    Player20, Player21, Player22, Player23, Player24"
5. Kickpoints: Add 1 KP to all 7 violators
```

**Example (Raidfails)**:
```
Event Configuration:
- Clan: LOST F2P
- Type: RAID
- Duration: 0
- Action: raidfails
- Action Values: {capitalPeakMax: 5, otherDistrictsMax: 3, penalizeTies: true}
- Reason: Raidfail_zuviele_Angriffe

Raid Results:
- Capital Peak: 7 attacks (MAX: 5) → OVER
- District 1: 3 attacks (MAX: 3) → OK
- District 2: 5 attacks (MAX: 3) → OVER
- District 3: 2 attacks (MAX: 3) → OK

Capital Peak Attackers:
- Player1: 3 attacks (MOST)
- Player2: 2 attacks
- Player3: 2 attacks

District 2 Attackers:
- Player4: 2 attacks (TIE for most)
- Player5: 2 attacks (TIE for most)
- Player6: 1 attack

Execution:
1. Identify over-attacked: Capital Peak, District 2
2. Capital Peak: Player1 has most (3) → Kickpoint
3. District 2: Player4 and Player5 tied (2 each)
   - penalizeTies=true → Both get kickpoints
4. Message: 
   "Capital Peak (7/5 attacks): Player1 (3), Player2 (2), Player3 (2)
    → Penalized: Player1
    
    District 2 (5/3 attacks): Player4 (2), Player5 (2), Player6 (1)
    → Penalized: Player4, Player5"
5. Kickpoints: Add to Player1, Player4, Player5
```

---

## Timestamp Calculation

### Calculation Methods

Each event type has a unique calculation:

**Clan Games**:
```java
ZonedDateTime cgEnd = Bot.getNext28thAt1pm();
long fireTime = cgEnd.toInstant().toEpochMilli() - duration;
```

**Clan War** (end):
```java
Long cwEndTime = clan.getCWEndTime();  // From API
long fireTime = cwEndTime - duration;
```

**Clan War** (start):
```
// Special handling - no timestamp
// Fires on state change detection
return Long.MAX_VALUE;  // Prevents normal scheduling
```

**CWL Day**:
```java
Long cwlDayEnd = clan.getCWLDayEndTime();  // From API
long fireTime = cwlDayEnd - duration;
```

**Raid**:
```java
Long raidEnd = clan.getRaidEndTime();  // From API
long fireTime = raidEnd - duration;
```

### Duration Values

**duration = -1**: Start trigger
- Only used for CW start events
- Fires on state change detection
- Not scheduled via timestamp

**duration = 0**: End trigger
- Fires exactly at event end
- Most common configuration

**duration > 0**: Before end trigger
- Fires N milliseconds before end
- Example: 3600000 = 1 hour before end
- Used for reminders

### Time Zone Handling

**CoC API Times**:
- Returned in ISO 8601 format with UTC offset
- Example: `"20240120T143000.000Z"`
- Converted to milliseconds since epoch

**Bot Times**:
- Clan Games: Uses system timezone
- All others: Uses API timezone (UTC)

**Conversion**:
```java
// API string to timestamp
Instant instant = Instant.parse("20240120T143000.000Z");
long millis = instant.toEpochMilli();

// Timestamp to date
Date date = new Date(millis);
```

---

## Execution Flow

### Event Lifecycle

**1. Creation** (via `/listeningevent add`):
```
User → Discord Command → Modal → Database INSERT → Event Created
```

**2. Polling** (every 2 minutes):
```
Poll Task → Query Database → Calculate Timestamps → Check Threshold
```

**3. Scheduling** (if within 5 minutes):
```
Schedule in schedulertasks → Wait until fire time → Execute
```

**4. Execution** (at fire time):
```
executeEventWithRetry() → Validate State → Fire Event → Actions
```

**5. Completion**:
```
Log Success/Failure → Event Remains in Database (for history)
```

### Execution with Retry

**Method**: `executeEventWithRetry(ListeningEvent le, Long eventId, int maxRetries)`

**Process**:
```
attempt = 0
WHILE attempt <= maxRetries:
    TRY:
        IF NOT shouldEventFire(le):
            RETURN  // Skip, state invalid
        
        le.fireEvent()
        LOG "Event executed successfully"
        RETURN  // Success!
    
    CATCH error:
        LOG "Event failed (attempt X)"
        IF attempt < maxRetries:
            WAIT exponential_backoff_time
        ELSE:
            LOG "Event failed after all attempts"
    
    attempt++
```

**Backoff Times**:
- Attempt 1 fails: Wait 5 seconds (2^0 * 5000ms)
- Attempt 2 fails: Wait 10 seconds (2^1 * 5000ms)
- Attempt 3 fails: Wait 20 seconds (2^2 * 5000ms)

**Example**:
```
Event 123 scheduled for 14:30:00

14:30:00.000: Attempt 1
  - API call fails
  - Wait 5 seconds

14:30:05.000: Attempt 2
  - API call fails
  - Wait 10 seconds

14:30:15.000: Attempt 3
  - API call succeeds
  - Event fires successfully
  - Done!
```

### State Validation

**Method**: `shouldEventFire(ListeningEvent le)`

**Purpose**: Prevent firing events when game state changed

**Checks**:

**Clan Games**:
```
ALWAYS return true
(uses historical data, doesn't depend on current state)
```

**Clan War**:
```
IF clan.isCWActive():
    return true
ELSE:
    LOG "No active clan war"
    return false
```

**CWL Day**:
```
IF clan.isCWLActive():
    return true
ELSE:
    LOG "No active CWL"
    return false
```

**Raid**:
```
IF clan.RaidActive():
    return true
ELSE:
    LOG "No active raid"
    return false
```

**Example Scenario**:
```
Event: CW missed attacks check
Scheduled: War ends at 14:30

14:25: War is active
14:30: War ends → State changes to "warEnded"
14:30: Event scheduled to fire
  → shouldEventFire(): clan.isCWActive() returns false
  → Event skipped (war already ended and state reset)

This prevents errors when war ends early or state changes unexpectedly.
```

---

## State Management

### CW Start State Tracking

**Purpose**: Detect when wars start to fire start triggers

**In-Memory Storage**:
```java
private static HashMap<String, String> cwLastStates = new HashMap<>();

private static String getCWLastState(String clanTag);
private static void setCWLastState(String clanTag, String state);
```

**States**:
- `"notInWar"`: Not in a war
- `"preparation"`: War prep phase
- `"inWar"`: War in progress
- `"warEnded"`: War just ended

**Initialization** (on bot startup):
```
FOR EACH clan WITH CW events:
    current_state = API.getWarState(clan)
    setCWLastState(clan, current_state)
```

**Update** (every 2 minutes during polling):
```
FOR EACH clan WITH CW start events:
    last_state = getCWLastState(clan)
    current_state = API.getWarState(clan)
    
    IF state_changed_to_war:
        FIRE all start events for clan
        setCWLastState(clan, current_state)
    
    ELSE IF war_ended:
        CLEAR firedStartEvents[clan]
        setCWLastState(clan, "notInWar")
```

**Transition Detection**:
```
War Start:
  last_state IN ("notInWar", "warEnded")
  AND current_state IN ("preparation", "inWar")

War End:
  current_state IN ("notInWar", "warEnded")
```

### Fired Events Tracking

**Purpose**: Prevent duplicate firing of start events within same war

**In-Memory Storage**:
```java
private static Map<String, Set<Long>> firedStartEvents = new ConcurrentHashMap<>();
```

**Key**: Clan tag
**Value**: Set of event IDs that have fired for current war

**Usage**:
```
WHEN war starts:
    FOR EACH start event:
        IF event.id NOT IN firedStartEvents[clan]:
            FIRE event
            ADD event.id TO firedStartEvents[clan]
        ELSE:
            SKIP (already fired this war)

WHEN war ends:
    REMOVE firedStartEvents[clan]
```

**Example**:
```
Clan LOST F2P has 2 start events:
- Event 101: Filler check
- Event 102: Donor selection

Poll 1 (14:00): War starts
  - Fire event 101 → firedStartEvents["#LOST_F2P"] = {101}
  - Fire event 102 → firedStartEvents["#LOST_F2P"] = {101, 102}

Poll 2 (14:02): War still active
  - Event 101 in firedStartEvents → Skip
  - Event 102 in firedStartEvents → Skip

Poll 3 (16:00): War ends
  - CLEAR firedStartEvents["#LOST_F2P"]

Poll 4 (16:02): New war starts
  - Fire event 101 → firedStartEvents["#LOST_F2P"] = {101}
  - Fire event 102 → firedStartEvents["#LOST_F2P"] = {101, 102}
```

### Scheduled Events Tracking

**Purpose**: Prevent duplicate scheduling of same event

**In-Memory Storage**:
```java
private static Set<Long> scheduledEvents = ConcurrentHashMap.newKeySet();
private static Map<Long, Long> scheduledEventTimestamps = new ConcurrentHashMap<>();
```

**scheduledEvents**: Set of event IDs that are scheduled
**scheduledEventTimestamps**: Map of event ID to fire timestamp

**Usage**:
```
DURING polling:
    FOR EACH event:
        IF event.id IN scheduledEvents:
            SKIP (already scheduled)
        
        IF time_until_fire <= 5_minutes:
            SCHEDULE event
            ADD event.id TO scheduledEvents
            SET scheduledEventTimestamps[event.id] = fire_time
```

**Cleanup** (every poll):
```
FOR EACH scheduled event:
    fire_time = scheduledEventTimestamps[event.id]
    IF fire_time < (now - 1_hour):
        REMOVE from scheduledEvents
        REMOVE from scheduledEventTimestamps
```

**Example**:
```
Event 123 fires at 14:30

Poll at 14:25 (5 min before):
  - Not in scheduledEvents
  - Schedule for 14:30
  - ADD to scheduledEvents
  - scheduledEventTimestamps[123] = 14:30 timestamp

Poll at 14:27 (3 min before):
  - IN scheduledEvents → Skip

Poll at 14:30 (at fire time):
  - Event fires
  - Still in scheduledEvents (prevents re-scheduling)

Poll at 15:31 (1 hour 1 min after):
  - Cleanup: 14:30 < (15:31 - 1 hour)
  - REMOVE from scheduledEvents
  - REMOVE from scheduledEventTimestamps
```

---

## Error Handling and Retry Logic

### Error Categories

**1. Network Errors**:
- CoC API unreachable
- Timeout
- Connection reset

**Handling**: Retry with backoff

**2. API Errors**:
- 404: Clan/player not found
- 403: Private war log or invalid key
- 503: API maintenance

**Handling**: Log error, retry if temporary (503)

**3. Discord Errors**:
- Channel not found
- Missing permissions
- Rate limit

**Handling**: Log error, don't retry immediately

**4. Database Errors**:
- Connection lost
- Query failed
- Constraint violation

**Handling**: Retry connection, log error

**5. Logic Errors**:
- Invalid configuration
- Missing data
- Calculation errors

**Handling**: Log error with context, skip event

### Retry Strategy

**When to Retry**:
- Network timeouts
- Temporary API errors (503)
- Database connection issues
- Transient failures

**When NOT to Retry**:
- Invalid configuration (will fail again)
- 404 errors (resource doesn't exist)
- Permission errors (requires manual fix)
- Discord rate limits (wait period required)

**Implementation**:
```java
private static void executeEventWithRetry(ListeningEvent le, Long eventId, int maxRetries) {
    int attempt = 0;
    boolean success = false;
    
    while (attempt <= maxRetries && !success) {
        try {
            // Validate state
            if (!shouldEventFire(le)) {
                System.out.println("Event validation failed, skipping");
                return;
            }
            
            // Execute
            le.fireEvent();
            
            System.out.println("Event " + eventId + " executed successfully");
            success = true;
            
        } catch (Exception e) {
            System.err.println("Event " + eventId + " failed (attempt " + (attempt + 1) + "): " + e.getMessage());
            e.printStackTrace();
            
            if (attempt < maxRetries) {
                long waitTime = (long) Math.pow(2, attempt) * 5000;  // 5s, 10s, 20s
                System.err.println("Retrying in " + (waitTime/1000) + " seconds...");
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        attempt++;
    }
    
    if (!success) {
        System.err.println("Event " + eventId + " failed after " + (maxRetries + 1) + " attempts");
    }
}
```

### Logging

**Success**:
```
Executing event 123 (attempt 1/4)
Event validation: OK
API call: Success
Message sent: Success
Event 123 executed successfully
```

**Failure**:
```
Executing event 123 (attempt 1/4)
Event 123 execution failed (attempt 1/4): API timeout
Retrying event 123 in 5 seconds...
Executing event 123 (attempt 2/4)
Event 123 execution failed (attempt 2/4): API timeout
Retrying event 123 in 10 seconds...
Executing event 123 (attempt 3/4)
Event validation: OK
API call: Success
Message sent: Success
Event 123 executed successfully
```

**Total Failure**:
```
Executing event 123 (attempt 1/4)
Event 123 execution failed (attempt 1/4): Channel not found
Retrying event 123 in 5 seconds...
Executing event 123 (attempt 2/4)
Event 123 execution failed (attempt 2/4): Channel not found
Retrying event 123 in 10 seconds...
Executing event 123 (attempt 3/4)
Event 123 execution failed (attempt 3/4): Channel not found
Event 123 failed after 4 attempts
```

---

## Performance Considerations

### API Call Optimization

**Problem**: Each event requires API calls, which have rate limits

**Optimizations**:

1. **Polling Interval**: 2 minutes instead of real-time
   - Reduces API calls by 30x (vs. every 4 seconds)
   - Still responsive enough for most use cases

2. **Scheduled vs. Polling**:
   - Events scheduled within 5 minutes
   - Reduces repeated timestamp calculations

3. **Caching**:
   - API responses cached in data wrappers
   - Refreshed only when needed

4. **Batch State Checks**:
   - CW start detection batches all clans
   - One API call per clan, not per event

### Memory Usage

**In-Memory Structures**:
- cwLastStates: O(n clans)
- firedStartEvents: O(n clans × m events)
- scheduledEvents: O(k scheduled events)
- scheduledEventTimestamps: O(k scheduled events)

**Cleanup**:
- firedStartEvents cleared when war ends
- scheduledEvents cleaned every poll (>1 hour old)
- cwLastStates persists (small size)

**Typical Memory**:
- 10 clans: ~1 KB
- 50 events: ~5 KB
- Total: < 10 KB

### Database Load

**Query Frequency**:
- Event list: Every 2 minutes
- Event details: As needed during scheduling
- State updates: Never (in-memory only)

**Optimization**:
- Index on listening_events(clan_tag)
- Index on listening_events(listeningtype)
- Queries use primary key when possible

**Typical Load**:
- 1 SELECT every 2 minutes
- 50 events = 1 ms query time
- Negligible impact

### Thread Safety

**Scheduler**: Single-threaded executor
- No race conditions
- Sequential execution
- Predictable behavior

**In-Memory Maps**: ConcurrentHashMap
- Thread-safe
- No locking needed
- Safe for concurrent reads

**Database**: Prepared statements
- Connection pooling (single connection)
- No concurrent writes to same row
- Safe from SQL injection

---

## Troubleshooting

### Event Not Firing

**Check**:
1. Event exists: `/listeningevent list`
2. Fire time calculated correctly
3. Game state is valid (war/raid active)
4. Bot has been running continuously
5. No errors in console logs

**Logs to Look For**:
```
"Scheduling event X to fire in Y minutes" - Event was scheduled
"Event X executed successfully" - Event fired
"Event validation failed" - State invalid at fire time
"Event X failed after N attempts" - Event failed completely
```

### Duplicate Events

**Symptoms**: Same event fires twice for one war/raid

**Causes**:
- Bot restarted mid-event
- firedStartEvents cleared incorrectly
- Scheduled events not tracked

**Prevention**:
- Don't restart bot during active events
- firedStartEvents properly managed
- scheduledEvents tracking

### Late/Early Firing

**Symptoms**: Event fires not exactly at end time

**Causes**:
- Polling interval (up to 2 min variance)
- Scheduling threshold (up to 5 min early)
- Bot was down and restarted

**Expected Behavior**:
- Events can fire 0-2 minutes late (polling interval)
- Events scheduled 0-5 minutes early
- Overdue events skipped after restart

### API Rate Limits

**Symptoms**: Events fail with "rate limit exceeded"

**Solutions**:
- Reduce polling frequency
- Reduce number of events
- Implement API call queue
- Use longer duration values

---

## Future Enhancements

### Potential Improvements

1. **Recurring Events**:
   - Auto-recreate after firing
   - Template-based event creation

2. **Event Dependencies**:
   - Fire event B only if event A succeeded
   - Chain multiple actions

3. **Custom Schedules**:
   - Cron-like scheduling
   - Specific date/time triggers

4. **Event History**:
   - Log all event executions
   - Performance metrics
   - Success/failure rates

5. **Dynamic Configuration**:
   - Adjust thresholds based on history
   - Machine learning for prediction

6. **Multi-Guild Support**:
   - Support multiple Discord servers
   - Shared clan data

7. **Webhook Integration**:
   - Send events to external services
   - Integration with other bots

---

**End of Event System Deep Dive**

For command usage, see `02_COMMANDS_REFERENCE.md`.

For database schema, see `03_DATABASE_SCHEMA.md`.

For architecture overview, see `00_MASTER_OVERVIEW.md`.
