package commands.links;

import java.util.ArrayList;
import java.util.List;

import datautil.DBManager;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class playerinfo extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("playerinfo"))
			return;
		event.deferReply().queue();
		String title = "Spielerinformation";

		OptionMapping userOption = event.getOption("user");
		OptionMapping playerOption = event.getOption("player");

		if (userOption == null && playerOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Einer der beiden Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		if (userOption != null && playerOption != null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Bitte gib nur einen Parameter an!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		String userid = null;
		String playertag = null;
		ArrayList<Player> linkedaccs = new ArrayList<>();

		Player player = null;

		enum ConvertionType {
			USERTOACCS, ACCTOUSER
		}

		ConvertionType conv = null;

		if (userOption != null) {
			userid = userOption.getAsMentionable().getId();
			linkedaccs = new User(userid).getAllLinkedAccounts();
			conv = ConvertionType.USERTOACCS;
		}
		if (playerOption != null) {
			playertag = playerOption.getAsString();
			player = new Player(playertag);
			if (player.IsLinked()) {
				userid = player.getUser().getUserID();
				conv = ConvertionType.ACCTOUSER;
			} else {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Dieser Spieler ist nicht verifiziert.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		String desc = "";

		if (conv == ConvertionType.ACCTOUSER) {
			try {
				desc += "## " + MessageUtil.unformat(player.getInfoString()) + "\n";
			} catch (Exception e) {
				e.printStackTrace();
			}
			desc += "Verlinkter Discord Account: <@" + userid + ">\n";
			if (player.getClanDB() != null) {
				desc += "Eingetragen in Clan: " + player.getClanDB().getInfoString() + "\n";
			} else {
				desc += "Eingetragen in Clan: ---\n";
			}
			if (player.getClanAPI() != null) {
				desc += "Ingame in Clan: " + player.getClanAPI().getInfoString() + "\n";
			} else {
				desc += "Ingame in Clan: ---\n";
			}
			desc += "Aktuelle Anzahl Kickpunkte: " + player.getActiveKickpoints().size() + "\n";
			desc += "Ingesamte Anzahl Kickpunkte: " + player.getTotalKickpoints();

			final String uuid = userid;
			MessageChannelUnion channel = event.getChannel();
			channel.sendMessage(".").queue(sentMessage -> {
				new Thread(() -> {
					try {
						Thread.sleep(100);
						sentMessage.editMessage("<@" + uuid + ">").queue();
						Thread.sleep(100);
						sentMessage.delete().queue();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}).start();
			});
		}
		if (conv == ConvertionType.USERTOACCS) {
			try {
				desc += "## <@" + userid + "> \n";
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (linkedaccs.isEmpty()) {
				desc += "	Keine verlinkten Accounts.\n";
			} else {
				desc += "Verlinkte Accounts: \n";
				for (Player p : linkedaccs) {
					desc += "   \\- " + MessageUtil.unformat(p.getInfoString()) + "\n";
				}
			}
		}
		final String descr = desc;
		new Thread(() -> {
			try {
				Thread.sleep(500);
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, descr, MessageUtil.EmbedType.INFO))
						.queue();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("playerinfo"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);

			event.replyChoices(choices).queue();
		}
	}

}
