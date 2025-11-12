package commands.coc.util;

import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import datawrapper.AchievementData;
import datawrapper.AchievementData.Type;
import datawrapper.Clan;
import datawrapper.Player;
import dbutil.DBManager;
import dbutil.DBUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class wins extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("wins"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Wins Statistik";

			OptionMapping clanOption = event.getOption("clan");
			OptionMapping playerOption = event.getOption("player");
			OptionMapping seasonOption = event.getOption("season");

			if (clanOption == null && playerOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Einer der beiden Parameter (clan oder player) ist erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (clanOption != null && playerOption != null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Bitte gib nur einen Parameter an!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (seasonOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Season Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String seasonStr = seasonOption.getAsString();
			YearMonth selectedMonth = YearMonth.parse(seasonStr, DateTimeFormatter.ofPattern("yyyy-MM"));
			YearMonth currentMonth = YearMonth.now();

			String desc = "";

			if (playerOption != null) {
				// Single player mode
				String playerTag = playerOption.getAsString();
				Player player = new Player(playerTag);

				if (!player.IsLinked()) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler ist nicht verifiziert.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				desc += "## " + MessageUtil.unformat(player.getInfoStringDB()) + "\n";
				desc += getPlayerWinsForSeason(player, selectedMonth, currentMonth);

			} else if (clanOption != null) {
				// Clan mode
				String clanTag = clanOption.getAsString();
				Clan clan = new Clan(clanTag);

				desc += "## " + MessageUtil.unformat(clan.getInfoString()) + "\n";
				desc += "Season: " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
						+ "\n\n";

				ArrayList<Player> members = clan.getAllMembers();
				if (members == null || members.isEmpty()) {
					desc += "Keine Mitglieder gefunden.\n";
				} else {
					// Sort members by wins (descending)
					ArrayList<PlayerWinsData> playerWinsDataList = new ArrayList<>();

					for (Player member : members) {
						if (!member.IsLinked()) {
							continue;
						}

						Integer wins = getPlayerWinsDifferenceForSeason(member, selectedMonth, currentMonth);
						if (wins != null) {
							playerWinsDataList.add(new PlayerWinsData(member, wins));
						}
					}

					// Sort by wins descending
					playerWinsDataList.sort((a, b) -> b.wins.compareTo(a.wins));

					for (int i = 0; i < playerWinsDataList.size(); i++) {
						PlayerWinsData data = playerWinsDataList.get(i);
						desc += (i + 1) + ". " + MessageUtil.unformat(data.player.getInfoStringDB()) + ": **"
								+ data.wins + "** Wins\n";
					}

					if (playerWinsDataList.isEmpty()) {
						desc += "Keine Daten für diese Season verfügbar.\n";
					}
				}
			}

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO)).queue();
		}, "WinsCommand-" + event.getUser().getId()).start();
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("wins"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("player")) {
				List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);
				event.replyChoices(choices).queue();

			} else if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
				event.replyChoices(choices).queue();

			} else if (focused.equals("season")) {
				List<Command.Choice> choices = getAvailableSeasons(input);
				event.replyChoices(choices).queue();
			}
		}, "WinsAutocomplete-" + event.getUser().getId()).start();
	}

	/**
	 * Get list of available seasons based on data in the database
	 */
	private List<Command.Choice> getAvailableSeasons(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		// Get all unique timestamps from the achievements table for WINS type
		String sql = "SELECT DISTINCT data->>'WINS' as wins_data FROM achievements WHERE data ? 'WINS' AND data->'WINS' != 'null'::jsonb";

		try {
			ArrayList<String> results = DBUtil.getArrayListFromSQL(sql, String.class);
			HashMap<YearMonth, Boolean> availableMonths = new HashMap<>();

			for (String winsData : results) {
				if (winsData == null || winsData.equals("[]"))
					continue;

				// Parse the wins data to extract timestamps
				// The data is a JSON array of AchievementData objects
				org.json.JSONArray winsArray = new org.json.JSONArray(winsData);
				for (int i = 0; i < winsArray.length(); i++) {
					org.json.JSONObject winData = winsArray.getJSONObject(i);
					if (winData.has("time")) {
						String timeStr = winData.getString("time");
						Timestamp ts = Timestamp.valueOf(timeStr.replace("T", " ").replace("Z", ""));
						YearMonth month = YearMonth.from(ts.toLocalDateTime());
						availableMonths.put(month, true);
					}
				}
			}

			// Also add current month
			YearMonth currentMonth = YearMonth.now();
			availableMonths.put(currentMonth, true);

			// Sort months in descending order (most recent first)
			List<YearMonth> sortedMonths = new ArrayList<>(availableMonths.keySet());
			sortedMonths.sort((a, b) -> b.compareTo(a));

			// Format and filter
			DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);
			DateTimeFormatter valueFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

			for (YearMonth month : sortedMonths) {
				String display = month.format(displayFormatter);
				String value = month.format(valueFormatter);

				if (display.toLowerCase().contains(input.toLowerCase()) || value.contains(input)) {
					choices.add(new Command.Choice(display, value));
					if (choices.size() >= 25) {
						break;
					}
				}
			}

		} catch (Exception e) {
			System.err.println("Error getting available seasons: " + e.getMessage());
			e.printStackTrace();
		}

		// If no choices, at least add current month
		if (choices.isEmpty()) {
			YearMonth currentMonth = YearMonth.now();
			DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);
			DateTimeFormatter valueFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
			choices.add(new Command.Choice(currentMonth.format(displayFormatter), currentMonth.format(valueFormatter)));
		}

		return choices;
	}

	/**
	 * Get player wins description for a specific season
	 */
	private String getPlayerWinsForSeason(Player player, YearMonth selectedMonth, YearMonth currentMonth) {
		Integer winsDiff = getPlayerWinsDifferenceForSeason(player, selectedMonth, currentMonth);

		if (winsDiff == null) {
			return "Season: " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
					+ "\n\nnoch keine Daten, zu kurz verlinkt\n";
		}

		return "Season: " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
				+ "\n\n**" + winsDiff + "** Wins in dieser Season\n";
	}

	/**
	 * Get player wins difference for a specific season Returns null if no data
	 * available
	 */
	private Integer getPlayerWinsDifferenceForSeason(Player player, YearMonth selectedMonth, YearMonth currentMonth) {
		HashMap<Type, ArrayList<AchievementData>> allData = player.getAchievementDatasDB();

		if (allData == null || !allData.containsKey(Type.WINS)) {
			return null;
		}

		ArrayList<AchievementData> winsData = allData.get(Type.WINS);
		if (winsData == null || winsData.isEmpty()) {
			return null;
		}

		// For current month: use last saved data and current API data
		if (selectedMonth.equals(currentMonth)) {
			// Find the most recent timestamp in current month or the season start (1st of
			// month)
			AchievementData startData = null;
			Timestamp latestTimestamp = null;

			for (AchievementData data : winsData) {
				Timestamp ts = data.getTimeExtracted();
				YearMonth dataMonth = YearMonth.from(ts.toLocalDateTime());

				// Get the last data point from previous month or beginning of current month
				if (dataMonth.isBefore(currentMonth) || dataMonth.equals(currentMonth)) {
					if (latestTimestamp == null || ts.after(latestTimestamp)) {
						latestTimestamp = ts;
						startData = data;
					}
				}
			}

			if (startData == null) {
				return null;
			}

			// Get current wins from API
			try {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				AchievementData currentData = player.getAchievementDataAPI(Type.WINS, now);

				if (currentData == null || currentData.getData() == null) {
					return null;
				}

				Integer startWins = (Integer) startData.getData();
				Integer currentWins = (Integer) currentData.getData();

				return currentWins - startWins;
			} catch (Exception e) {
				System.err.println("Error fetching current wins for player " + player.getTag() + ": " + e.getMessage());
				return null;
			}
		} else {
			// For past months: find season start and season end data
			AchievementData startData = null;
			AchievementData endData = null;

			YearMonth startMonth = selectedMonth;
			YearMonth endMonth = selectedMonth.plusMonths(1);

			for (AchievementData data : winsData) {
				Timestamp ts = data.getTimeExtracted();
				YearMonth dataMonth = YearMonth.from(ts.toLocalDateTime());

				// Find data at the start of selected month (or last data before it)
				if (dataMonth.equals(startMonth) || (dataMonth.isBefore(startMonth)
						&& (startData == null || ts.after(startData.getTimeExtracted())))) {
					startData = data;
				}

				// Find data at the start of next month (season end)
				if (dataMonth.equals(endMonth) || (dataMonth.equals(startMonth)
						&& (endData == null || ts.after(endData.getTimeExtracted())))) {
					endData = data;
				}
			}

			if (startData == null || endData == null) {
				return null;
			}

			Integer startWins = (Integer) startData.getData();
			Integer endWins = (Integer) endData.getData();

			return endWins - startWins;
		}
	}

	/**
	 * Helper class to store player and wins data for sorting
	 */
	private static class PlayerWinsData {
		Player player;
		Integer wins;

		PlayerWinsData(Player player, Integer wins) {
			this.player = player;
			this.wins = wins;
		}
	}
}
