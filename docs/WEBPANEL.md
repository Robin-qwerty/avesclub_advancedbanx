# Avesban web panel (Laravel)

This is the integration guide for a staff website. **Do not write punishment rows from PHP.** Velocity owns the live cache, kicks, history IDs, notifications, and voice-chat mute (LuckPerms on one BackendLink).

No website code lives in this repository. Build the panel in your Laravel app using the protocol below.

## Architecture

1. **Read** `Punishments`, `PunishmentHistory`, and `Players` from the Avesban MySQL database (read-only user).
2. **Write** by publishing an `EXECUTE_COMMAND` JSON message to Redis. Velocity runs the same command path as `/ban`, `/mute`, `/unban`, …
3. After an ACK, `SELECT` MySQL again to refresh the page.
4. Never publish `VOICE_PERM`. That is Velocity → named BackendLink only.

```
Laravel  --SELECT-->  Avesban MySQL
Laravel  --PUBLISH EXECUTE_COMMAND-->  Redis  -->  Velocity Avesban
Velocity --INSERT/DELETE Punishments-->  Avesban MySQL
Velocity --PUBLISH ACK-->  Redis  -->  Laravel
Velocity --VOICE_PERM-->  Redis  -->  one BackendLink  -->  LuckPerms API
```

Put Redis and MySQL on the **private network**. Do not expose Redis to the public internet.

## MySQL (read-only)

Create a MySQL user that can only `SELECT` on the Avesban database.

```sql
GRANT SELECT ON avesban.* TO 'avesban_web'@'laravel-host' IDENTIFIED BY '...';
FLUSH PRIVILEGES;
```

In Laravel `.env`:

```
AVESBAN_DB_HOST=127.0.0.1
AVESBAN_DB_PORT=3306
AVESBAN_DB_DATABASE=avesban
AVESBAN_DB_USERNAME=avesban_web
AVESBAN_DB_PASSWORD=...
```

`config/database.php`:

```php
'avesban' => [
    'driver' => 'mysql',
    'host' => env('AVESBAN_DB_HOST'),
    'port' => env('AVESBAN_DB_PORT', 3306),
    'database' => env('AVESBAN_DB_DATABASE'),
    'username' => env('AVESBAN_DB_USERNAME'),
    'password' => env('AVESBAN_DB_PASSWORD'),
    'charset' => 'utf8mb4',
    'collation' => 'utf8mb4_unicode_ci',
    'strict' => true,
    'read' => [
        'host' => [env('AVESBAN_DB_HOST')],
    ],
    'write' => [
        // unused — never write through this connection
        'host' => [env('AVESBAN_DB_HOST')],
    ],
],
```

Use `DB::connection('avesban')` for every query.

### Tables

#### `Punishments` (active only)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | int AI | Punishment id (`/unpunish`, layouts `%ID%`) |
| `name` | varchar(16) | Last known Minecraft name |
| `uuid` | varchar(35) | Player UUID **without dashes**, or an IPv4/IPv6 string for IP bans |
| `reason` | varchar(255) | May start with `@layout` |
| `operator` | varchar(16) | Staff name (web operators are truncated to 16 chars) |
| `punishmentType` | varchar(16) | See types below |
| `start` | bigint | Epoch **milliseconds** |
| `end` | bigint | Epoch ms, or **`-1`** for permanent |
| `calculation` | varchar(50) | Time-layout calculation key, or null |
| `server` | varchar(64) | Proxy/backend that created it, or null |
| `targetServer` | varchar(64) | Scoped punishment server, or null = network-wide |

#### `PunishmentHistory` (every punishment ever issued)

Same columns as `Punishments`. Kicks are history-only (they are not copied into `Punishments`). Unbans **delete** the active row; the history row stays.

#### `Players` (join/leave tracking)

| Column | Type | Notes |
| --- | --- | --- |
| `uuid` | varchar(35) PK | UUID without dashes |
| `name` | varchar(16) | Last seen name |
| `firstIp` | varchar(45) | First recorded IP |
| `lastIp` | varchar(45) | Last recorded IP |
| `lastJoin` | bigint | Epoch ms |
| `lastLeave` | bigint | Epoch ms (null while online / never left) |
| `firstSeen` | bigint | Epoch ms |

### `punishmentType` values

`BAN`, `TEMP_BAN`, `IP_BAN`, `TEMP_IP_BAN`, `MUTE`, `TEMP_MUTE`, `WARNING`, `TEMP_WARNING`, `KICK`, `NOTE`

Active list:

```sql
SELECT * FROM Punishments ORDER BY start DESC;
```

Still muted?

```sql
SELECT * FROM Punishments
WHERE uuid = ? AND punishmentType IN ('MUTE', 'TEMP_MUTE')
  AND (end = -1 OR end > ?);  -- ? = UNIX_TIMESTAMP() * 1000
```

IP bans store the IP in `uuid`, not the player's UUID. Resolve the player via `Players.lastIp` / `Players.firstIp` when listing alts.

## Redis

Match Velocity `config.yml`:

```yaml
Redis:
  Enabled: true
  Host: 127.0.0.1
  Port: 6379
  Password: ''
  Database: 0
  AuthToken: 'your-long-random-secret'
  Channels:
    ProxyIn: avesban:proxy:in
    ProxyOut: avesban:proxy:out
    AckKeyPrefix: avesban:ack:
```

Laravel `.env`:

```
REDIS_HOST=127.0.0.1
REDIS_PASSWORD=null
REDIS_PORT=6379
AVESBAN_REDIS_TOKEN=your-long-random-secret
```

Use the **same Redis database index** as Velocity (`Redis.Database`).

### Channels the website may use

| Channel / key | Direction | Purpose |
| --- | --- | --- |
| `avesban:proxy:in` | Laravel → Velocity | `EXECUTE_COMMAND` |
| `avesban:proxy:out` | Velocity → Laravel | `ACK` / `ERROR` for that request `id` |
| `avesban:ack:{id}` | Velocity SET, TTL 60s | Same JSON as the pub/sub ACK (avoids a subscribe race) |

Do **not** publish to `avesban:link:*` or `avesban:link:acks`. Those are Velocity ↔ BackendLink.

### JSON envelope

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "EXECUTE_COMMAND",
  "ts": 1726700000000,
  "token": "your-long-random-secret",
  "source": "web",
  "operator": "StaffName",
  "command": "ban",
  "args": ["Player", "Cheating"]
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `id` | yes | UUID v4. Echoed on the ACK. Use a new id per button click. |
| `type` | yes | Website only sends `EXECUTE_COMMAND` |
| `ts` | yes | Unix epoch **milliseconds**. Messages older than `Redis.MaxAgeMs` (default 60s) are rejected |
| `token` | yes | Must equal `Redis.AuthToken` |
| `source` | no | Use `web` |
| `operator` | no | Stored as the punishment operator (max 16 chars). Defaults to `CONSOLE` |
| `command` | yes | Whitelisted name below |
| `args` | yes | Array of strings, same as in-game command arguments |
| `payload` | no | Optional. `operator`, `command`, and `args` may also live here |

Velocity replies:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "ACK",
  "ts": 1726700000120,
  "token": "your-long-random-secret",
  "source": "velocity",
  "ok": true,
  "message": "ok"
}
```

`type` is `ERROR` and `ok` is `false` on failure (`invalid token`, `stale timestamp`, `command not allowed`, `invalid arguments`, `command failed`, …).

### Allowed `command` values

`ban`, `tempban`, `punish`, `banip`, `ban-ip`, `ipban`, `tempipban`, `mute`, `tempmute`, `warn`, `tempwarn`, `kick`, `unban`, `unmute`

Examples:

- Permanent ban: `"command": "ban", "args": ["Steve", "Cheating"]`
- Temp ban: `"command": "tempban", "args": ["Steve", "7d", "Cheating"]`
- IP ban: `"command": "banip", "args": ["1.2.3.4", "Alt evading"]` or a player name
- Mute: `"command": "mute", "args": ["Steve", "Voice + chat"]`
- Unban: `"command": "unban", "args": ["Steve"]` (optional extra arg: a scoped server name or `all`)
- Unmute: `"command": "unmute", "args": ["Steve"]`

Syntax is the same as the in-game commands (duration tokens `7d`, `12h`, `#layout`, optional scoped server suffix).

### PHP: publish and wait for ACK

Pub/sub ACKs can arrive **before** Laravel subscribes. Prefer polling the ACK key Velocity writes:

```php
use Illuminate\Support\Facades\Redis;
use Illuminate\Support\Str;

function avesbanCommand(string $operator, string $command, array $args): array
{
    $id = (string) Str::uuid();
    $payload = json_encode([
        'id' => $id,
        'type' => 'EXECUTE_COMMAND',
        'ts' => (int) floor(microtime(true) * 1000),
        'token' => env('AVESBAN_REDIS_TOKEN'),
        'source' => 'web',
        'operator' => $operator,
        'command' => $command,
        'args' => array_values($args),
    ]);

    Redis::publish('avesban:proxy:in', $payload);

    $key = 'avesban:ack:' . $id;
    for ($i = 0; $i < 50; $i++) { // ~5 seconds
        $ack = Redis::get($key);
        if (is_string($ack) && $ack !== '') {
            return json_decode($ack, true, 512, JSON_THROW_ON_ERROR);
        }
        usleep(100000);
    }

    throw new RuntimeException('Timed out waiting for Velocity ACK ' . $id);
}

// Ban:
// avesbanCommand($staff->name, 'ban', ['Steve', 'Cheating']);
// Mute (also voice-mutes via LuckPerms on the primary BackendLink):
// avesbanCommand($staff->name, 'mute', ['Steve', 'Chat + voice']);
```

Then reload from MySQL:

```php
$active = DB::connection('avesban')
    ->table('Punishments')
    ->where('name', 'Steve')
    ->orderByDesc('start')
    ->get();
```

Match concurrent clicks by `id`. Never reuse an id.

## What the panel must not do

- `INSERT` / `UPDATE` / `DELETE` on `Punishments` or `PunishmentHistory`
- Publish `VOICE_PERM` or talk to `avesban:link:{name}`
- Call LuckPerms or Simple Voice Chat from PHP
- Send commands other than the whitelist above
- Share `Redis.AuthToken` with untrusted code

Voice mute (`voicechat.speak` = false) is applied by **one** BackendLink through the LuckPerms API when Velocity processes `/mute` or `/tempmute` (including mutes issued from this panel). Unmute and expired temp mutes remove that node only if the player has no remaining mute.
