# King of the Hill Bot

### Env vars
- `KOTH_BOT_TOKEN`: Bot token
- `KOTH_BOT_SQL_URL`: jdbc:postgresql://host:port/databasename
- `KOTH_BOT_SQL_USERNAME`: Username of DB
- `KOTH_BOT_SQL_PASSWORD`: Password of DB

### Database
- `king` table (channelid text, userid text, key text, timestamp text)
- `kingstats` table (userid text, totalseconds text, channelid text, guildid text)
