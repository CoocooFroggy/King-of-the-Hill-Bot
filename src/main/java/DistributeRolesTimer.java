import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.TimerTask;

public class DistributeRolesTimer extends TimerTask {
    Member kingMember;
    Member member;
    Guild guild;
    TextChannel channel;

    DistributeRolesTimer(Member kingMember, Member member, Guild guild, TextChannel channel) {
        this.kingMember = kingMember;
        this.member = member;
        this.guild = guild;
        this.channel = channel;
    }

    @Override
    public void run() {
        Statement statement = Main.statement;
        String guildId = guild.getId();
        String channelId = channel.getId();

        List<Role> kothRoles = guild.getRolesByName("King of the Hill!", false);
        Role kothRole = kothRoles.get(0);
        List<Role> pushedRoles = guild.getRolesByName("Pushed off the Hill", false);
        Role pushedRole = pushedRoles.get(0);

        //Get user ID of pushed off
        ResultSet resultSet = null;
        try {
            resultSet = statement.executeQuery(
                    "SELECT userid FROM king " +
                            "WHERE key = 'pushed' AND guildid = '" + guildId + "' AND channelid = '" + channelId + "'");

            //Remove pushed role from pushed off the hill person
            if (resultSet.next()) {
                String pushedId = (String) resultSet.getObject(resultSet.findColumn("userid"));
                if (pushedId != null) {
                    Member pushedOffMember = guild.retrieveMemberById(pushedId).complete();
                    //Remove pushed off role from pushed off person
                    guild.removeRoleFromMember(pushedOffMember, pushedRole).queue();
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        //Give king role to pusher
        guild.addRoleToMember(member, kothRole).queue();

        //In case we don't need to interact with king, return
        if (kingMember == null)
            return;

        //Give pushed off role to king
        guild.addRoleToMember(kingMember, pushedRole).queue();
        //Remove king role from king
        guild.removeRoleFromMember(kingMember, kothRole).queue();
    }
}
