package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.User;

/**
 * Data Transfer Object for Player in REST API
 */
public class PlayerDTO {
    
    @JsonProperty("tag")
    private String tag;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("roleInClan")
    private String roleInClan;
    
    @JsonProperty("nameAPI")
    private String nameAPI;
    
    @JsonProperty("clanDB")
    private String clanDB;
    
    @JsonProperty("clanAPI")
    private String clanAPI;
    
    public PlayerDTO() {
        // Default constructor for Jackson
    }
    
    /**
     * Create PlayerDTO from Player object
     * @param player The player to convert
     */
    public PlayerDTO(Player player) {
        this.tag = player.getTag();
        
        // Get user ID if player is linked
        User user = player.getUser();
        this.userId = user != null ? user.getUserID() : null;
        
        // Get role in DB clan (not checking admin status)
        Player.RoleType roleDB = player.getRoleDB();
        if (roleDB != null) {
            this.roleInClan = roleDB.name();
        } else {
            this.roleInClan = "NOTINCLAN";
        }
        
        // Get name from API
        try {
            this.nameAPI = player.getNameAPI();
        } catch (Exception e) {
            this.nameAPI = null;
        }
        
        // Get clan from DB
        Clan clanDbObj = player.getClanDB();
        this.clanDB = clanDbObj != null ? clanDbObj.getTag() : null;
        
        // Get clan from API
        Clan clanApiObj = player.getClanAPI();
        this.clanAPI = clanApiObj != null ? clanApiObj.getTag() : null;
    }
    
    // Getters and setters
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getRoleInClan() {
        return roleInClan;
    }
    
    public void setRoleInClan(String roleInClan) {
        this.roleInClan = roleInClan;
    }
    
    public String getNameAPI() {
        return nameAPI;
    }
    
    public void setNameAPI(String nameAPI) {
        this.nameAPI = nameAPI;
    }
    
    public String getClanDB() {
        return clanDB;
    }
    
    public void setClanDB(String clanDB) {
        this.clanDB = clanDB;
    }
    
    public String getClanAPI() {
        return clanAPI;
    }
    
    public void setClanAPI(String clanAPI) {
        this.clanAPI = clanAPI;
    }
}
