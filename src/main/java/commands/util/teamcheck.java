package commands.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import datautil.DBManager;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class teamcheck extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("teamcheck"))
			return;
		
		String title = "Team-Check";
		event.deferReply().queue();

		new Thread(() -> {
			// Check permissions - must be at least co-leader
			User userExecuted = new User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userExecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN
						|| role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasPermission = true;
					break;
				}
			}
			
			if (!hasPermission) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Get parameters
			OptionMapping memberRoleOption = event.getOption("memberrole");
			OptionMapping teamRole1Option = event.getOption("team_role_1");
			OptionMapping teamRole2Option = event.getOption("team_role_2");
			OptionMapping teamRole3Option = event.getOption("team_role_3");

			if (memberRoleOption == null || teamRole1Option == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Parameter 'memberrole' und 'team_role_1' sind erforderlich.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Role memberRole = memberRoleOption.getAsRole();
			List<Role> teamRoles = new ArrayList<>();
			teamRoles.add(teamRole1Option.getAsRole());
			
			if (teamRole2Option != null) {
				teamRoles.add(teamRole2Option.getAsRole());
			}
			if (teamRole3Option != null) {
				teamRoles.add(teamRole3Option.getAsRole());
			}

			// Create button ID with role IDs
			String buttonId = "teamcheck_" + memberRole.getId();
			for (Role teamRole : teamRoles) {
				buttonId += "_" + teamRole.getId();
			}

			performTeamCheck(event.getHook(), event.getGuild(), title, memberRole, teamRoles, buttonId);

		}, "TeamCheckCommand-" + event.getUser().getId()).start();
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("teamcheck_"))
			return;

		event.deferEdit().queue();

		String title = "Team-Check";
		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		new Thread(() -> {
			// Parse button ID to extract role IDs
			String[] parts = id.substring("teamcheck_".length()).split("_");
			
			Guild guild = event.getGuild();
			if (guild == null) {
				return;
			}

			Role memberRole = guild.getRoleById(parts[0]);
			List<Role> teamRoles = new ArrayList<>();
			
			for (int i = 1; i < parts.length; i++) {
				Role teamRole = guild.getRoleById(parts[i]);
				if (teamRole != null) {
					teamRoles.add(teamRole);
				}
			}

			if (memberRole == null || teamRoles.isEmpty()) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Rollen konnten nicht gefunden werden.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			performTeamCheck(event.getHook(), guild, title, memberRole, teamRoles, id);

		}, "TeamCheckRefresh-" + event.getUser().getId()).start();
	}

	private void performTeamCheck(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild, 
			String title, Role memberRole, List<Role> teamRoles, String buttonId) {
		
		if (guild == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Dieser Befehl kann nur auf einem Server ausgeführt werden.",
					MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Load all members with the member role
		guild.loadMembers().onSuccess(allMembers -> {
			List<Member> membersWithRole = allMembers.stream()
					.filter(member -> member.getRoles().contains(memberRole))
					.toList();

			// Track statistics
			int totalMembers = membersWithRole.size();
			Map<Role, List<String>> teamMembers = new HashMap<>();
			
			List<String> noTeamList = new ArrayList<>();
			List<String> multipleTeamsList = new ArrayList<>();
			
			// Initialize team member lists
			for (Role teamRole : teamRoles) {
				teamMembers.put(teamRole, new ArrayList<>());
			}

			// Check each member
			for (Member member : membersWithRole) {
				int teamCount = 0;
				List<Role> memberTeams = new ArrayList<>();
				
				for (Role teamRole : teamRoles) {
					if (member.getRoles().contains(teamRole)) {
						teamCount++;
						memberTeams.add(teamRole);
						teamMembers.get(teamRole).add(member.getAsMention());
					}
				}

				if (teamCount == 0) {
					noTeamList.add(member.getAsMention());
				} else if (teamCount > 1) {
					StringBuilder teams = new StringBuilder();
					for (int i = 0; i < memberTeams.size(); i++) {
						if (i > 0) teams.append(", ");
						teams.append(memberTeams.get(i).getName());
					}
					multipleTeamsList.add(member.getAsMention() + " (in " + teams + ")");
				}
			}

			// Build result description
			StringBuilder description = new StringBuilder();
			
			// Summary statistics
			description.append("**Gesamtzahl der Mitglieder:** ").append(totalMembers).append("\n\n");
			
			// Team distribution - list all members per team
			description.append("**Teamverteilung:**\n");
			for (Role teamRole : teamRoles) {
				List<String> members = teamMembers.get(teamRole);
				description.append("**").append(teamRole.getName()).append(":** ").append(members.size()).append("\n");
				if (!members.isEmpty()) {
					for (String member : members) {
						description.append(member);
						if (members.indexOf(member) < members.size() - 1) {
							description.append(", ");
						}
					}
					description.append("\n");
				}
				description.append("\n");
			}

			// Members without team - list all
			description.append("**Mitglieder ohne Team:** ").append(noTeamList.size()).append("\n");
			if (!noTeamList.isEmpty()) {
				for (String member : noTeamList) {
					description.append(member);
					if (noTeamList.indexOf(member) < noTeamList.size() - 1) {
						description.append(", ");
					}
				}
				description.append("\n");
			}
			description.append("\n");

			// Members with multiple teams - list all
			description.append("**Mitglieder in mehreren Teams:** ").append(multipleTeamsList.size()).append("\n");
			if (!multipleTeamsList.isEmpty()) {
				for (String member : multipleTeamsList) {
					description.append(member).append("\n");
				}
			}

			// Create refresh button
			Button refreshButton = Button.secondary(buttonId, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));

			// Add timestamp
			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			// Always use INFO color
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, description.toString(), 
					MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
					.setActionRow(refreshButton)
					.queue();
		});
	}
}
