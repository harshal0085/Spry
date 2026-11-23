# Spry developer powers v2 (planned, not built yet)

Asked for: 1 kick, 2 force-play to everyone online, 3 last seen + now playing, 4 maintenance mode,
5 force update, 6 delete account data, 7 hidden mode, 8 featured song (Dev's pick), 9 global
listening room, 10 stats dashboard, 11 custom badges, 14 pop-up alerts to one person.
Not doing (agreed): reading chats in-app, secret listening, location.

## Data (Realtime Database)
- `config` (read: anyone; write: admin)
  - `maintenance: {on: bool, text}`
  - `minVersion: Int` (versionCode), `updateUrl: String` (default https://github.com/harshal0085/Spry/releases)
  - `featured: {videoId, title, artist, thumb, at}`
  - `room: {uid, title, on}` (the global room the developer hosts)
- `badges/$uid: String` (read: signed in; write: admin)
- `alerts/$uid/$id: {text, at}` (read+delete: owner; write: admin)
- `commands/$uid: {type: "play"|"kick", videoId?, title?, artist?, thumb?, at}` (read+delete: owner; write: admin)
- `presence/$uid/playing: "Title · Artist"` (written by the owner while playing; removed on pause)

## Rules changes
- Admin read on `friends`, `chatAt` (counts only for stats); admin delete on users, usernames,
  friends/*/uid, requests, invites, sessions, presence, chats, chatAt (for "delete account data").
- Maintenance on: block non-admin writes to requests, invites, vibe, chats.
- Global room: `sessions/$uid` readable by anyone signed in when `config/room/uid === $uid`;
  tunnel write allowed when `config/room/uid === $host`.

## App hooks (already found)
- Start hosting: `service.startTogetherHost(port, name, VibeRoomSettings)` with
  `port = ServerSocket(0).use { it.localPort }` (see SpryFriends.acceptVibe). `publishHosting` publishes sessions/{me}.
- Join a host: `joinLink(Friend(uid, name, true, HostedSession(sid, key)))` then `service.joinTogether(link, myName)` (see adminJoin).
- Play a song: `playerConnection.playQueue(YouTubeQueue(...))`; find the WatchEndpoint(videoId) constructor (HomeGridItems uses `YouTubeQueue.radio(item.toMediaMetadata())`).
- Leave a session (kick): find the MusicService together stop/leave function (`stopTogetherInternal` exists in MusicServiceTogether.kt).
- Version: BuildConfig.VERSION_CODE (check buildConfig is enabled in app/build.gradle.kts).
- Global composables go in GlobalDialogsHost (next to VibeRequestDialog / AnnouncementDialog); they have LocalPlayerConnection.

## UI
- Global: maintenance dialog (non-admins), blocking "Update required" dialog with link, alert dialog
  (then delete the alert), command handler (play / kick, mark handled by `at`), now-playing publisher (skipped in hidden mode).
- Home: "Dev's pick" glass card + "Live room" card (one tap Join) above the Online panel.
- Developer panel: stats card (users, online, hosting, active 24h/7d, messages today, top songs now),
  featured (feature what I'm playing / clear), room (open / close), force-play current song to all online,
  maintenance switch + text, minimum version (set to this build / clear) + URL, hidden mode switch;
  per-user ⋯ menu: Listen, Kick, Play my song to them, Alert, Badge, Reset name, Ban/Unban, Delete all data.
- Badges shown next to usernames (friends list, chat header, dev panel); last seen / playing under names.

## After building
Publish firebase/database.rules.json again; test on both phones (Realme PNDYS8RCAUUSLZR8 + second phone).
