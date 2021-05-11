import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import javax.swing.plaf.nimbus.State;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

public class Commands {
    public static void pushCommand(User user, Member member, Guild guild, TextChannel channel, List<User> mentionedUsers) throws SQLException {
        //Vars
        Statement statement = Main.statement;
        String userId = user.getId();
        String guildId = guild.getId();
        String channelId = channel.getId();
        String nickname;
        Instant currentTimestamp = Instant.now();
        long currentTimestampEpoch = Instant.now().toEpochMilli();

        //Fetch all bans
        ResultSet bansResultSet = statement.executeQuery("SELECT expiretimestamp FROM kingbans " +
                "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "' AND userid = '" + userId + "'");

        //If a ban exists for the user ID
        while (bansResultSet.next()) {
            long banExpireTimestamp = Long.parseLong(bansResultSet.getString("expiretimestamp"));
            //if the current date is before the timestamp
            if (currentTimestampEpoch < banExpireTimestamp) {
                long banDuration = banExpireTimestamp - currentTimestampEpoch;
                String banDurationFormatted = String.format("%d hours and %d minutes",
                        TimeUnit.SECONDS.toHours(banDuration),
                        TimeUnit.SECONDS.toMinutes(banDuration) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(banDuration))
                );

                channel.sendMessage("You are banned for **" + banDurationFormatted + "** :(").queue();
                return;
            }
        }

        //Get personal name for user
        if (member.getNickname() == null)
            nickname = user.getName();
        else
            nickname = member.getNickname();

        //Get user ID of king
        ResultSet resultSet = statement.executeQuery(
                "SELECT userid, timestamp FROM king " +
                        "WHERE key = 'king' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        //If there's no hill in this channel + guild
        if (!resultSet.next())
            return;

        //King vars part 1
        int kingidIndex = resultSet.findColumn("userid");
        int kingTimestampIndex = resultSet.findColumn("timestamp");
        String kingId = resultSet.getString(kingidIndex);
        String kingTimestamp = resultSet.getString(kingTimestampIndex);

        //If there already is row for this guild and channel, just no king
        if (kingId == null) {
            statement.execute("UPDATE king " +
                    "SET userid = '" + userId + "', timestamp = '" + currentTimestampEpoch + "' " +
                    "WHERE key = 'king' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

            //Create roles if they don't exist
            createRoles(guild);

            //Distribute roles
            distributeRoles(null, member, guild, channel);

            channel.sendMessage("**" + nickname + "** is now king of the hill!").queue();
            return;
        }

        // If nobody is mentioned
        if (mentionedUsers.isEmpty()) {
            //If you're the king
            if (userId.equals(kingId)) {
                channel.sendMessage("You're the king already!").queue();
            } else {
                channel.sendMessage("Please mention a player to push!").queue();
            }
            return;
        }


        //King vars P2
        String pushedUserId = mentionedUsers.get(0).getId();
        User kingUser = Main.jda.retrieveUserById(kingId).complete();
        Member kingMember = guild.retrieveMember(kingUser).complete();
        String kingNickname;

        //If you mention yourself
        if (pushedUserId.equals(userId)) {
            channel.sendMessage("You can't push yourself off, silly!").queue();
            return;
        }

        //Get personal name for king
        if (kingMember.getNickname() == null)
            kingNickname = kingUser.getName();
        else
            kingNickname = kingMember.getNickname();

        //Otherwise check if they pushed the king off
        if (pushedUserId.equals(kingId)) {
            //Create roles if they don't exist
            createRoles(guild);

            //Distribute roles
            distributeRoles(kingMember, member, guild, channel);

            //Update king's stats (person who just got pushed off, not the pusher)
            updateKingStats(guildId, channelId, kingId, kingTimestamp, currentTimestamp);

            //Add one to the pusher's king count
            statement.executeUpdate("UPDATE kingstats " +
                    "SET totalkings = totalkings + 1 " +
                    "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "' AND userid = '" + userId + "' ");

            //Push off the king
            statement.executeUpdate("UPDATE king " +
                    "SET userid = '" + kingId + "' " +
                    "WHERE key = 'pushed' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

            //Make the pusher the new king
            statement.executeUpdate("UPDATE king " +
                    "SET userid = '" + userId + "', timestamp = '" + currentTimestampEpoch + "'" +
                    "WHERE key = 'king' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");


            channel.sendMessage("**" + nickname + "** pushed **" + kingNickname + "** off the hill!").queue();
        }
        //Else if they didn't push the king off the hill
        else {
            channel.sendMessage("Please push **" + kingNickname + "** off the hill!").queue();
        }
    }

    public static void createCommand(Guild guild, Member member, TextChannel channel) throws SQLException {
        //Vars
        Statement statement = Main.statement;
        String guildId = guild.getId();
        String channelId = channel.getId();

        //If they don't have manage server perms, ignore them
        if (!member.hasPermission(Permission.MANAGE_SERVER))
            return;

        //See if hill exists here
        ResultSet resultSet = statement.executeQuery(
                "SELECT * FROM king " +
                        "WHERE key = 'king' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        //Check if hill already exists here
        if (resultSet.next()) {
            channel.sendMessage("Cannot create a hill here, hill already exists!").queue();
            return;
        }

        createRoles(guild);

        //Otherwise create a hill here
        statement.execute("INSERT INTO king (key, guildid, channelid) VALUES " +
                "('king', '" + guildId + "', '" + channelId + "')");
        statement.execute("INSERT INTO king (key, guildid, channelid) VALUES " +
                "('pushed', '" + guildId + "', '" + channelId + "')");
        channel.sendMessage("Hill created! Do ``push` to start!").queue();
    }

    public static void removeCommand(Guild guild, Member member, TextChannel channel) throws SQLException {
        //DELETE FROM king WHERE channelid = '685618172975513625';
        //Vars
        Statement statement = Main.statement;
        String guildId = guild.getId();
        String channelId = channel.getId();

        //If they don't have manage server perms, ignore them
        if (!member.hasPermission(Permission.MANAGE_SERVER))
            return;

        statement.execute("DELETE FROM king WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        channel.sendMessage("Removed hill from this channel.").queue();
    }

    public static void statsCommand(Guild guild, Member member, TextChannel channel, List<User> mentionedUsers) throws SQLException {
        //Vars
        Statement statement = Main.statement;
        String userId = member.getId();
        String guildId = guild.getId();
        String channelId = channel.getId();
        int totalSeconds = 0;
        int totalkings = 0;
        int totalpushed = 0;

        String nickname;

        //if they mentioned a user
        if (!mentionedUsers.isEmpty()) {
            //Then set the user ID to that mentioned user
            userId = mentionedUsers.get(0).getId();
            member = guild.retrieveMemberById(userId).complete();
        }
        //Otherwise it'll be the user ID of the user who triggered the command


        //Get personal name for user
        if (member.getNickname() == null)
            nickname = member.getUser().getName();
        else
            nickname = member.getNickname();

        //Get stats
        ResultSet kingstatsResultSet = statement.executeQuery("SELECT totalseconds, totalkings, totalpushed FROM kingstats " +
                "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "' AND userid = '" + userId + "'");

        //Get stored seconds
        if (kingstatsResultSet.next()) {
            String totalsecondsString = kingstatsResultSet.getString("totalseconds");
            totalkings = kingstatsResultSet.getInt("totalkings");
            totalpushed = kingstatsResultSet.getInt("totalpushed");
            totalSeconds = Integer.parseInt(totalsecondsString);
        }

        //Get king of the channel + timestamp
        ResultSet kingResultSet = statement.executeQuery("SELECT userid, timestamp FROM king " +
                "WHERE key = 'king' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        //If there's no hill in this channel
        if (!kingResultSet.next())
            return;

        //Add current session's seconds if you're king
        int kingIdIndex = kingResultSet.findColumn("userid");
        String kingId = kingResultSet.getString(kingIdIndex);
        if (userId.equals(kingId)) {
            Instant currentInstant = Instant.now();
            String kingStartEpoch = kingResultSet.getString("timestamp");
            Instant kingStartInstant = Instant.ofEpochMilli(Long.parseLong(kingStartEpoch));

            Duration between = Duration.between(kingStartInstant, currentInstant);

            totalSeconds += between.getSeconds();
        }

        //If your time is 0
        if (totalSeconds == 0) {
            channel.sendMessage("**" + nickname + "** hasn't been king yet.").queue();
            return;
        }

        String formattedTime = String.format("%d hours, %d minutes, %d seconds",
                TimeUnit.SECONDS.toHours(totalSeconds),
                TimeUnit.SECONDS.toMinutes(totalSeconds) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(totalSeconds)),
                totalSeconds -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(totalSeconds))
        );

        channel.sendMessage("**" + nickname + "** has been king for **" + formattedTime + "**.\n" +
                "They've also been king " + totalkings + " times and has been pushed off " + totalpushed + " times.").queue();
    }

    public static void kingBanCommand(Guild guild, Member member, TextChannel channel, List<User> mentionedUsers) {
        //Vars
        Statement statement = Main.statement;
        String guildId = guild.getId();
        String channelId = channel.getId();

        //If they don't have manage server perms, ignore them
        if (!member.hasPermission(Permission.MANAGE_SERVER))
            return;
    }

    /*
     *** UTILITIES ***
     */

    //Distribute roles
    static Timer distributeTimer;

    public static void distributeRoles(Member kingMember, Member member, Guild guild, TextChannel channel) throws SQLException {
        //If we don't have perms to distribute roles, do nothing
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES))
            return;

        //Vars
        Statement statement = Main.statement;
        String pushedId = null;
        String guildId = guild.getId();
        String channelId = channel.getId();

        //Cancel existing timers
        if (distributeTimer != null) {
            distributeTimer.cancel();
            distributeTimer.purge();
        }

        //Get user ID of pushed off
        ResultSet resultSet;
        resultSet = statement.executeQuery(
                "SELECT userid FROM king " +
                        "WHERE key = 'pushed' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        if (resultSet.next()) {
            pushedId = resultSet.getString("userid");
        }

        distributeTimer = new Timer();
        distributeTimer.schedule(new DistributeRolesTimer(member, kingMember, pushedId, guild, channel), 5000);
    }

    public static void createRoles(Guild guild) {
        //If we don't have manage role perms, just don't do anything
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES))
            return;

        //Check if roles already exist
        List<Role> kothRoles = guild.getRolesByName("King of the Hill!", false);
        List<Role> pushedRoles = guild.getRolesByName("Pushed off the Hill", false);

        //Make it if it doesn't
        if (kothRoles.isEmpty()) {
            guild.createRole()
                    .setName("King of the Hill!")
                    .setPermissions()
                    .queue();
        }
        if (pushedRoles.isEmpty()) {
            guild.createRole()
                    .setName("Pushed off the Hill")
                    .setPermissions()
                    .queue();
        }
    }

    public static void updateKingStats(String guildId, String channelId, String kingId, String kingTimestamp, Instant currentTimestamp) throws SQLException {
        //Vars
        Statement statement = Main.statement;
        Instant kingStartDate = Instant.ofEpochMilli(Long.parseLong(kingTimestamp));

        //Get king's totalseconds and totalpushed
        ResultSet statsResultSet = statement.executeQuery("SELECT totalseconds, totalpushed FROM kingstats " +
                "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "' AND userid = '" + kingId + "'");

        //Stats vars
        long totalseconds = 0;
        long totalpushed = 0;

        //If they already have stats, add time
        if (statsResultSet.next()) {
            //Time
            String totalsecondsString = statsResultSet.getString("totalseconds");
            totalseconds = Long.parseLong(totalsecondsString);
            //Kings
            totalpushed = statsResultSet.getInt("totalpushed");
        }

        //Calculate how long they've been king
        Duration between = Duration.between(kingStartDate, currentTimestamp);
        long kingSessionSeconds = between.getSeconds();

        //Add to totalseconds
        totalseconds += kingSessionSeconds;

        //Add to totalpushed
        totalpushed++;

        //Update table with new stats
        statement.execute("UPDATE kingstats SET totalseconds = '" + totalseconds + "', totalpushed = '" + totalpushed + "' WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "' AND userid = '" + kingId + "'; " +
                "INSERT INTO kingstats (guildid, channelid, userid, totalseconds, totalpushed) " +
                "SELECT '" + guildId + "', '" + channelId + "', '" + kingId + "', '" + totalseconds + "', '" + totalpushed + "' " +
                "WHERE NOT EXISTS (SELECT 1 FROM kingstats WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "' AND userid = '" + kingId + "');"
        );
    }
}
