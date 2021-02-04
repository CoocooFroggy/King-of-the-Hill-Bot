import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class Commands {
    public static void pushCommand(User user, Member member, Guild guild, TextChannel channel, List<User> mentionedUsers) throws SQLException {
        //Vars
        Statement statement = Main.statement;
        String userId = user.getId();
        String guildId = guild.getId();
        String channelId = channel.getId();
        String nickname;

        //Get personal name for user
        if (member.getNickname() == null)
            nickname = user.getName();
        else
            nickname = member.getNickname();

        //Get user ID of king
        ResultSet resultSet = statement.executeQuery(
                "SELECT userid FROM king " +
                        "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        //If there's no hill in this channel + guild
        if (!resultSet.next())
            return;

        //King vars part 1
        int kingidIndex = resultSet.findColumn("userid");
        String kingId = (String) resultSet.getObject(kingidIndex);

        //If there already is row for this guild and channel, just no king
        if (kingId == null) {
            statement.execute("UPDATE king " +
                    "SET userid = '" + userId + "' " +
                    "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

            //Distribute roles
            distributeRoles(null, member, guild);

            channel.sendMessage("**" + nickname + "** is now king of the hill!").queue();
            return;
        }

        // If nobody is mentioned
        if (mentionedUsers.isEmpty()) {
            channel.sendMessage("Please mention a player to push!").queue();
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
            statement.executeUpdate("UPDATE king " +
                    "SET userid = '" + userId + "' " +
                    "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

            //Distribute roles
            distributeRoles(kingMember, member, guild);

            channel.sendMessage("**" + nickname + "** pushed **" + kingNickname + "** off the hill!").queue();
            return;
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
                        "WHERE guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

        //Check if hill already exists here
        if (resultSet.next()) {
            channel.sendMessage("Cannot create a hill here, hill already exists!").queue();
            return;
        }

        //Check if roles already exist
        List<Role> kothRole = guild.getRolesByName("King of the Hill!", false);
        List<Role> pushedRole = guild.getRolesByName("Pushed off the Hill", false);
        //Make it if it doesn't
        if (kothRole.isEmpty()) {
            guild.createRole()
                    .setName("King of the Hill!")
                    .setPermissions()
                    .queue();
        }
        if (pushedRole.isEmpty()) {
            guild.createRole()
                    .setName("Pushed off the Hill")
                    .setPermissions()
                    .queue();
        }

        //Otherwise create a hill here
        statement.execute("INSERT INTO king (guildid, channelid) VALUES " +
                "('" + guildId + "', '" + channelId + "')");
        channel.sendMessage("Hill created! Do `-push` to start!").queue();
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

    /*
    *** UTILITIES ***
    */
    public static void distributeRoles(Member kingMember, Member member, Guild guild) {
        List<Role> kothRoles = guild.getRolesByName("King of the Hill!", false);
        Role kothRole = kothRoles.get(0);
        List<Role> pushedRoles = guild.getRolesByName("Pushed off the Hill", false);
        Role pushedRole = pushedRoles.get(0);

        //Remove pushed off role from pusher
        guild.removeRoleFromMember(member, pushedRole).queue();
        //Give king role to pusher
        guild.addRoleToMember(member, kothRole).queue();

        //In case we don't need to interact with king, return
        if (kingMember == null)
            return;
        
        //Remove king role from king
        guild.removeRoleFromMember(kingMember, kothRole).queue();
        //Give pushed off role to king
        guild.addRoleToMember(kingMember, pushedRole).queue();
    }
}
