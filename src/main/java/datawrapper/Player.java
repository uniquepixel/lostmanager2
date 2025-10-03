package datawrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;
import lostmanager.Bot;

public class Player {

	public enum RoleType {
		ADMIN, LEADER, COLEADER, ELDER, MEMBER, NOTINCLAN
	};

	private String tag;
	private Boolean warpreference;
	private Integer warmapposition;
	private String namedb;
	private String nameapi;
	private User user;
	private Clan clandb;
	private Clan clanapi;
	private ArrayList<Kickpoint> kickpoints;
	private Integer kickpointstotal = -1;
	private RoleType role;

	public Player(String tag) {
		this.tag = tag;
	}

	// sets all Data except tag to null for reloading with getters

	public Player refreshData() {
		warpreference = null;
		namedb = null;
		nameapi = null;
		user = null;
		clandb = null;
		clanapi = null;
		kickpoints = null;
		kickpointstotal = null;
		role = null;
		return this;
	}

	// setter; only use if already known -> better performance if not needed to be
	// requested
	// returns self, allows chaining

	public Player setWarPreference(Boolean b) {
		this.warpreference = b;
		return this;
	}

	public Player setWarMapPosition(Integer i) {
		this.warmapposition = i;
		return this;
	}

	public Player setNameDB(String name) {
		this.namedb = name;
		return this;
	}

	public Player setNameAPI(String name) {
		this.nameapi = name;
		return this;
	}

	public Player setUser(User user) {
		this.user = user;
		return this;
	}

	public Player setClanDB(Clan clan) {
		this.clandb = clan;
		return this;
	}

	public Player setClanAPI(Clan clan) {
		this.clanapi = clan;
		return this;
	}

	public Player setClanDB(ArrayList<Kickpoint> kickpoints) {
		this.kickpoints = kickpoints;
		return this;
	}

	public Player setKickpointsTotal(Integer i) {
		this.kickpointstotal = i;
		return this;
	}

	public Player setRole(RoleType role) {
		this.role = role;
		return this;
	}

	// getter; creates Data from API/DB if needed -> Null if not existant

	public boolean IsLinked() {
		String sql = "SELECT 1 FROM players WHERE coc_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean AccExists() {
		try {
			String encodedTag = URLEncoder.encode(tag, "UTF-8");
			// Clash of Clans API-Endpunkt
			URL url = new URL("https://api.clashofclans.com/v1/players/" + encodedTag);

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", "Bearer " + Bot.api_key);
			connection.setRequestProperty("Accept", "application/json");

			int responseCode = connection.getResponseCode();

			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				StringBuilder responseContent = new StringBuilder();
				while ((line = in.readLine()) != null) {
					responseContent.append(line);
				}
				in.close();

				return true;
			} else {
				System.out.println("Verifizierung fehlgeschlagen. Fehlercode: " + responseCode);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean verifyCocTokenAPI(String playerApiToken) {
		try {
			String encodedTag = URLEncoder.encode(tag, "UTF-8");
			URL url = new URL("https://api.clashofclans.com/v1/players/" + encodedTag + "/verifytoken");

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Authorization", "Bearer " + Bot.api_key);
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setDoOutput(true);

			// JSON-Body mit Token senden
			String requestBody = "{ \"token\": \"" + playerApiToken + "\" }";
			try (OutputStream os = connection.getOutputStream()) {
				byte[] input = requestBody.getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			int responseCode = connection.getResponseCode();

			// Wenn 200: Antwort lesen und JSON prüfen (expect: { "status":"ok" } wenn
			// richtig)
			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String responseLine = in.readLine();
				in.close();
				return responseLine != null && responseLine.contains("\"status\":\"ok\"");
			} else {
				System.out.println("Verifizierung fehlgeschlagen. Fehlercode: " + responseCode);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public boolean getWarPreference() {
		if (warpreference == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
			warpreference = jsonObject.getString("warPreference").equals("IN");
		}
		return warpreference;
	}

	public Integer getWarMapPosition() {
		if (warmapposition == null) {
			Clan c = getClanAPI();
			if (c != null) {
				String clantag = c.getTag();

				String json;

				String encodedTag = java.net.URLEncoder.encode(clantag, java.nio.charset.StandardCharsets.UTF_8);

				String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar";

				HttpClient client = HttpClient.newHttpClient();

				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
						.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET()
						.build();

				HttpResponse<String> response = null;
				try {
					response = client.send(request, HttpResponse.BodyHandlers.ofString());
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
					json = null;
				}

				if (response.statusCode() == 200) {
					String responseBody = response.body();
					// Einfacher JSON-Name-Parser ohne Bibliotheken:
					json = responseBody;
				} else {
					System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
					System.err.println("Antwort: " + response.body());
					json = null;
				}

				JSONObject jsonObject = new JSONObject(json);
				String state = jsonObject.getString("state");
				if (!state.equals("NOT_IN_WAR")) {
					JSONObject WarClan = jsonObject.getJSONObject("clan");
					JSONArray ClanWarMemberList = WarClan.getJSONArray("members");

					for (int i = 0; i < ClanWarMemberList.length(); i++) {
						JSONObject member = ClanWarMemberList.getJSONObject(i);
						String tag = member.getString("tag");
						if (tag.equals(this.tag)) {
							warmapposition = member.getInt("mapPosition");
						}
					}
				}
			}
		}
		return warmapposition;
	}

	public String getInfoString() {
		try {
			return getNameDB() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getTag() {
		return tag;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
			nameapi = jsonObject.getString("name");
		}
		return nameapi;
	}

	public String getNameDB() {
		if (namedb == null) {
			namedb = DBUtil.getValueFromSQL("SELECT name FROM players WHERE coc_tag = ?", String.class, tag);
		}
		return namedb;
	}

	public User getUser() {
		if (user == null) {
			String value = DBUtil.getValueFromSQL("SELECT discord_id FROM players WHERE coc_tag = ?", String.class,
					tag);
			user = value == null ? null : new User(value);
		}
		return user;
	}

	public Clan getClanAPI() {
		if (clanapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
			if (jsonObject.has("clan") && !jsonObject.isNull("clan")) {
				JSONObject clanObject = jsonObject.getJSONObject("clan");
				if (clanObject.has("tag")) {
					clanapi = new Clan(clanObject.getString("tag"));
				}
			}
		}
		return clanapi;
	}

	public Clan getClanDB() {
		if (clandb == null) {
			String value = DBUtil.getValueFromSQL("SELECT clan_tag FROM clan_members WHERE player_tag = ?",
					String.class, tag);
			clandb = value == null ? null : new Clan(value);
		}
		return clandb;
	}

	public ArrayList<Kickpoint> getActiveKickpoints() {
		if (kickpoints == null) {
			kickpoints = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ?";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				Kickpoint kp = new Kickpoint(id);
				if (kp.getExpirationDate().isAfter(OffsetDateTime.now())) {
					kickpoints.add(kp);
				}
			}
		}
		return kickpoints;
	}

	public int getTotalKickpoints() {
		if (kickpointstotal == null) {
			ArrayList<Kickpoint> a = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ?";
			for (Integer id : DBUtil.getArrayListFromSQL(sql, Integer.class, tag)) {
				a.add(new Kickpoint(id));
			}
			kickpointstotal = a.size();
		}
		return kickpointstotal;
	}

	public RoleType getRole() {
		if (role == null) {
			if (new Player(tag).getClanDB() == null) {
				return RoleType.NOTINCLAN;
			}
			String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						String rolestring = rs.getString("clan_role");
						role = rolestring.equals("leader") ? RoleType.LEADER
								: rolestring.equals("coLeader") ? RoleType.COLEADER
										: rolestring.equals("admin") ? RoleType.ELDER
												: rolestring.equals("member") ? RoleType.MEMBER : null;
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return role;
	}
}
