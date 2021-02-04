import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.List;

public class Main extends ListenerAdapter {

    static JDA jda;
    static String token;
    static String prefix;

    //SQL
    static Connection connection;
    static Statement statement;

    public static boolean startBot() throws InterruptedException {
        JDABuilder preBuild = JDABuilder.createDefault(token);
        preBuild.setActivity(Activity.playing("king of the hill!"));
        try {
            jda = preBuild.build();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        jda.addEventListener(new Main());
        jda.awaitReady();
        return true;
    }

    public static void main(String[] args) {
        token = System.getenv("KOTH_BOT_TOKEN");
        prefix = "-";

        String url = System.getenv("KOTH_BOT_SQL_URL");
        try {
            connection = DriverManager.getConnection(url,System.getenv("KOTH_BOT_SQL_USERNAME"),System.getenv("KOTH_BOT_SQL_PASSWORD"));
            statement = connection.createStatement();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        
        try {
            startBot();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Command executor
    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        Message message = event.getMessage();
        String messageRaw = message.getContentRaw();
        User user = event.getAuthor();
        Member member = event.getMember();
        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel();
        List<User> mentionedUsers = message.getMentionedUsers();

        if (!messageRaw.startsWith(prefix))
            return;

        String messageNoPrefix = messageRaw.substring(prefix.length());

        switch (messageNoPrefix.split("\\s")[0]) {
            case "p":
            case "push": {
                try {
                    Commands.pushCommand(user, member, guild, channel, mentionedUsers);
                } catch (SQLException throwables) {
                    channel.sendMessage("Database error :(").queue();
                    throwables.printStackTrace();
                }
                break;
            }
            case "c":
            case "create":
                try {
                    Commands.createCommand(guild, member, channel);
                } catch (SQLException throwables) {
                    channel.sendMessage("Database error :(").queue();
                    throwables.printStackTrace();
                }
                break;
            case "r":
            case "remove":
                try {
                    Commands.removeCommand(guild, member, channel);
                } catch (SQLException throwables) {
                    channel.sendMessage("Database error :(").queue();
                    throwables.printStackTrace();
                }
        }
    }
}
