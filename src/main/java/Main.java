import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Main extends ListenerAdapter {

    static JDA jda;
    static String token;
    static String prefix;

    //SQL
    static Connection connection;
    static Statement statement;

    public static boolean startBot() throws InterruptedException {
        JDABuilder jdaBuilder = JDABuilder.createDefault(token);
        jdaBuilder.setActivity(Activity.playing("king of the hill!"));
        try {
            jda = jdaBuilder.build();
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
        prefix = "`";

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

        registerSlashCommands();
    }

    public static void registerSlashCommands() {
//        Guild debugGuild = jda.getGuildById("685606700929384489");
//        assert debugGuild != null;

        jda.
                upsertCommand("create", "Creates a Hill in the current channel.")
                .queue();
        jda.
                upsertCommand("remove", "Removes the Hill in the current channel.")
                .queue();
        jda.
                upsertCommand("push", "Pushes a King off the Hill!")
                .addOption(OptionType.USER, "king", "Current King of the Hill", false)
                .queue();
        jda.
                upsertCommand("stats", "Checks stats of yourself or a player.")
                .addOption(OptionType.USER, "player", "Player to check stats of", false)
                .queue();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
    //Vars
        User user = event.getUser();
        Member member = event.getMember();
        Guild guild = event.getGuild();
        MessageChannel messageChannel = event.getChannel();

        if (messageChannel instanceof PrivateChannel) {
            event.reply("King of the Hill can only be played in a server!").queue();
            return;
        }
        TextChannel channel = event.getTextChannel();

        if (member == null) {
            System.out.println("Member " + user.getName() + " is null member");
            return;
        }

        // Command executor
        switch (event.getName()) {
            case "push": {
                try {
                    String reply = Commands.pushCommand(user, member, guild, channel, event.getOption("king"));
                    boolean ephemeral = false;
                    if (reply.startsWith("EPHEMERAL:")) {
                        reply = reply.replace("EPHEMERAL:", "");
                        ephemeral = true;
                    }
                    event.reply(reply).setEphemeral(ephemeral).queue();
                } catch (SQLException throwables) {
                    event.reply("Database error :(").queue();
                    throwables.printStackTrace();
                }
                break;
            }

            case "create": {
                try {
                    String reply = Commands.createCommand(guild, member, channel);
                    boolean ephemeral = false;
                    if (reply.startsWith("EPHEMERAL:")) {
                        reply = reply.replace("EPHEMERAL:", "");
                        ephemeral = true;
                    }
                    event.reply(reply).setEphemeral(ephemeral).queue();
                } catch (SQLException throwables) {
                    channel.sendMessage("Database error :(").queue();
                    throwables.printStackTrace();
                }
                break;
            }

            case "remove": {
                try {
                    String reply = Commands.removeCommand(guild, member, channel);
                    boolean ephemeral = false;
                    if (reply.startsWith("EPHEMERAL:")) {
                        reply = reply.replace("EPHEMERAL:", "");
                        ephemeral = true;
                    }
                    event.reply(reply).setEphemeral(ephemeral).queue();
                } catch (SQLException throwables) {
                    channel.sendMessage("Database error :(").queue();
                    throwables.printStackTrace();
                }
                break;
            }

            case "stats": {
                try {
                    String reply = Commands.statsCommand(guild, member, channel, event.getOption("player"));
                    boolean ephemeral = false;
                    if (reply.startsWith("EPHEMERAL:")) {
                        reply = reply.replace("EPHEMERAL:", "");
                        ephemeral = true;
                    }
                    event.reply(reply).setEphemeral(ephemeral).queue();
                } catch (SQLException throwables) {
                    channel.sendMessage("Database error :(").queue();
                    throwables.printStackTrace();
                }
                break;
            }

            case "kban": {
                String reply = Commands.kingBanCommand(guild, member, channel, event.getOption("player"));
                boolean ephemeral = false;
                if (reply.startsWith("EPHEMERAL:")) {
                    reply = reply.replace("EPHEMERAL:", "");
                    ephemeral = true;
                }
                event.reply(reply).setEphemeral(ephemeral).queue();
                break;
            }
        }
    }
}
