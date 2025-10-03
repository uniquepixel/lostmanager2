package commands.kickpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Kickpoint;
import datawrapper.Player;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class kpclan extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpclan"))
			return;
		event.deferReply().queue();
		String title = "Aktive Kickpunkte des Clans";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter Clan ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();

		Clan c = new Clan(clantag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		String desc = "### Kickpunkte aller Spieler des Clans " + c.getInfoString() + ":\n";

		HashMap<String, Integer> kpamounts = new HashMap<>();

		for (Player p : c.getPlayersDB()) {
			ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

			int totalkps = 0;
			for (Kickpoint kpi : activekps) {
				totalkps += kpi.getAmount();
			}
			if (totalkps > 0) {
				kpamounts.put(p.getInfoString(), totalkps);
			}
		}

		LinkedHashMap<String, Integer> sorted = kpamounts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

		// Ausgabe sortiert
		for (String key : sorted.keySet()) {
			String kp = sorted.get(key) == 1 ? "Kickpunkt" : "Kickpunkte";
			desc += key + ": " + sorted.get(key) + " " + kp + "\n\n";
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO)).queue();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpclan"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

			event.replyChoices(choices).queue();
		}
	}

}
