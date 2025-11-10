# Listening Events - Automated Event Tracking

## Overview
The Listening Events feature automates tracking and notification for various Clash of Clans clan events, including Clan Games, Clan Wars, Clan War Leagues, and Raids.

## Command Usage

### `/listeningevent add`
Creates a new listening event for a clan.

**Parameters:**
- `clan` (required): The clan to monitor (autocomplete enabled)
- `type` (required): Type of event to track
  - `cs` - Clan Games
  - `cw` - Clan War
  - `cwlday` - CWL Day
  - `raid` - Raid Weekend

**Modal Fields:**
- **Duration** (required): Time in milliseconds before event end to trigger
  - Example: `3600000` for 1 hour before end
  - Example: `0` to trigger at event end
- **Action Type** (required): What action to take when event fires
  - `infomessage` - Send informational message
  - `custommessage` - Send custom message
  - `kickpoint` - Automatically add kickpoints
  - `cwdonator` - Check war preferences (filler action)
- **Channel ID** (required): Discord channel ID where messages are sent
- **Action Values** (optional): JSON configuration for actions
  - Example: `[{"type":"FILLER"}]` for war preference check
  - Example: `[{"reason":{"name":"Missed CW Attack","clan_tag":"#ABCD1234"}}]` for kickpoint

### `/listeningevent list`
Lists all configured listening events.

**Parameters:**
- `clan` (optional): Filter events by clan

**Output:**
- Event ID
- Clan name and tag
- Event type
- Action type
- Channel
- Time until event fires

### `/listeningevent remove`
Removes a listening event.

**Parameters:**
- `id` (required): The event ID to remove (from `/listeningevent list`)

## Event Types Explained

### Clan Games (CS)
Tracks player performance during Clan Games by comparing achievement points between start and end dates.

**Timing:**
- Start: 22nd of month at 7:00 AM (hardcoded)
- End: 28th of month at 12:00 PM (hardcoded)

**Behavior:**
- Compares points in `achievement_data` table at start vs end timestamps
- Reports players below threshold (e.g., < 4000 points)
- Can automatically add kickpoints for low performers

**Example Setup:**
```
Duration: 0 (fires at end)
Action Type: kickpoint
Channel ID: 1234567890
Action Values: [{"reason":{"name":"Clan Games < 4000","clan_tag":"#ABCD1234"}}]
```

### Clan War (CW)
Tracks clan war participation and attacks.

**Two Modes:**

1. **Filler Action (Start of War)**
   - Fires during preparation phase
   - Lists members opted OUT of war
   - Useful for checking war roster
   
   **Example Setup:**
   ```
   Duration: 86400000 (24 hours before war end = during prep)
   Action Type: cwdonator
   Channel ID: 1234567890
   Action Values: [{"type":"FILLER"}]
   ```

2. **Missed Attacks (End of War)**
   - Fires when war ends
   - Reports members who didn't use all attacks
   - Can add kickpoints for missed attacks
   
   **Example Setup:**
   ```
   Duration: 0 (at war end)
   Action Type: kickpoint
   Channel ID: 1234567890
   Action Values: []
   ```

### CWL Day (CWLDAY)
Tracks missed attacks for each day of Clan War League.

**Behavior:**
- Fires at end of each CWL war day
- Reports members who didn't attack (CWL = 1 attack per day)
- Can add kickpoints for no-shows

**Example Setup:**
```
Duration: 0 (at day end)
Action Type: kickpoint
Channel ID: 1234567890
Action Values: [{"reason":{"name":"CWL No Attack","clan_tag":"#ABCD1234"}}]
```

### Raid Weekend (RAID)
Tracks raid participation and attacks.

**Behavior:**
- Fires based on raid end time from API
- Reports members who didn't complete all raid attacks
- Reports members who didn't participate at all
- Can add kickpoints for incomplete raids

**Example Setup:**
```
Duration: 0 (at raid end)
Action Type: kickpoint  
Channel ID: 1234567890
Action Values: []
```

## Action Types Explained

### infomessage
Sends an informational message to the configured channel without taking any action.

**Use Cases:**
- Simple notifications
- Tracking without penalties

### custommessage
Placeholder for custom message functionality (not fully implemented).

### kickpoint
Automatically adds kickpoints to players who violate rules.

**Configuration:**
- Can specify custom kickpoint reason in action values
- Amount comes from kickpoint reason if configured
- Default amount is 1 if no reason specified
- Kickpoints expire based on clan settings

### cwdonator (filler)
Special action type for Clan War start events.

**Behavior:**
- Checks which clan members are opted OUT of war
- Lists them in the channel
- Does NOT add kickpoints
- Useful for roster management

## Action Values JSON Format

Action values allow fine-tuning event behavior:

### Type-based Actions
```json
[{"type":"FILLER"}]
```
Used for filler action (war preference check).

### Reason-based Actions (Kickpoints)
```json
[{"reason":{"name":"Missed CW Attack","clan_tag":"#ABCD1234"}}]
```
Specifies which kickpoint reason to use. Must be a reason configured via `/kpaddreason`.

### Value-based Actions
```json
[{"value":4000}]
```
Can be used for thresholds (implementation-dependent).

### Multiple Actions
```json
[{"type":"REMINDER"}, {"value":24}]
```
Combine multiple action values (implementation-dependent).

## Database Schema

Events are stored in the `listening_events` table:

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

## Event Scheduling

- Events are scheduled when bot starts via `Bot.restartAllEvents()`
- Events calculate fire time based on:
  - Event type (determines base timestamp)
  - Duration value (offset from base)
- After firing, events remain in database but don't auto-reschedule
- Manual recreation required for recurring events

## Tips and Best Practices

1. **Test with INFOMESSAGE first**: Before adding kickpoints, test with `infomessage` to verify behavior

2. **Use appropriate durations**:
   - Clan Games: `0` (fires at end)
   - CW Filler: `86400000` (24 hours before end = during prep)
   - CW Missed Attacks: `0` (at war end)
   - CWL: `0` (at each day end)
   - Raid: `0` (at raid end)

3. **Channel permissions**: Ensure bot has send message permissions in target channel

4. **Kickpoint reasons**: Configure reasons via `/kpaddreason` before using in events

5. **Monitor and adjust**: Use `/listeningevent list` to review active events

6. **Cleanup**: Remove old/test events with `/listeningevent remove`

## Troubleshooting

**Event not firing:**
- Check event exists: `/listeningevent list`
- Verify fire time is in future
- Check bot has restarted since event creation
- Verify event type matches current game state (e.g., CW event needs active war)

**Messages not appearing:**
- Verify channel ID is correct
- Check bot permissions in channel
- Ensure bot is online

**Kickpoints not added:**
- Verify action type is `kickpoint`
- Check clan has kickpoint settings configured
- Verify players are in clan database

**API errors:**
- CoC API rate limits may cause issues
- War/raid must be active for related events
- Check API key is valid in bot configuration
