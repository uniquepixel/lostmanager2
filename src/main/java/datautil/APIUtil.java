package datautil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import lostmanager.Bot;

public class APIUtil {

	public static String getClanJson(String clanTag) {
		// URL-kodieren des Spieler-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(clanTag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag;

		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

		HttpResponse<String> response = null;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}

		if (response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			return responseBody;
		} else {
			System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
			System.err.println("Antwort: " + response.body());
			return null;
		}
	}
	
	public static String getPlayerJson(String playerTag) {
		// URL-kodieren des Spieler-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(playerTag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashofclans.com/v1/players/" + encodedTag;

		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

		HttpResponse<String> response = null;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}

		if (response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			return responseBody;
		} else {
			System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
			System.err.println("Antwort: " + response.body());
			return null;
		}
	}
	
}
