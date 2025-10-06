package commands.links;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class link extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("link"))
			return;
		event.deferReply().queue();
		String title = "User-Link";

		boolean b = false;
		User userexecuted = new User(event.getUser().getId());
		for (String clantag : DBManager.getAllClans()) {
			if (userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER) {
				b = true;
				break;
			}
		}
		if (b == false) {
			event.getHook()
			.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		OptionMapping tagOption = event.getOption("tag");
		OptionMapping useroption = event.getOption("user");

		if (tagOption == null || useroption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Beide Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String tag = tagOption.getAsString();
		String userid = useroption.getAsMentionable().getId();

		Player p = new Player(tag);

		if (p.AccExists()) {
			String playername = null;
			try {
				playername = p.getNameAPI();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!p.IsLinked()) {
				DBUtil.executeUpdate("INSERT INTO players (cr_tag, discord_id, name) VALUES (?, ?, ?)", tag, userid,
						playername);
				Player player = new Player(tag);
				String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB())
						+ " wurde erfolgreich mit dem User <@" + userid + "> verknüpft.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
						.queue();
			} else {
				Player player = new Player(tag);
				String linkeduserid = player.getUser().getUserID();
				String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " ist bereits mit <@"
						+ linkeduserid + "> verknüpft. Bitte verwende zuerst ``/unlink``.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue();
			}
		} else {
			String desc = "Der Spieler mit dem Tag " + tag + " existiert nicht oder es ist ein API-Fehler aufgetreten.";
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
					.queue();
		}

	}

}
