package util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.RichCustomEmoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.coobird.thumbnailator.Thumbnails;

/**
 * Manages app emojis for the bot
 * Handles downloading PNGs from GitHub, processing them, and uploading as Discord app emojis
 */
public class EmojiManager {
  
  private static final int MAX_BOT_EMOJIS = 1900;
  private static final int MAX_EMOJI_SIZE = 256 * 1024; // 256 KB
  private static final int EMOJI_DIMENSION = 128; // 128x128 pixels
  private static final String BOT_EMOJI_PREFIX = "lm2_"; // Prefix to identify bot-created emojis
  
  // Cache of emoji names to IDs to avoid repeated API calls
  private static final ConcurrentHashMap<String, String> emojiCache = new ConcurrentHashMap<>();
  
  /**
   * Get or create an app emoji for the given image
   * @param guild The guild to create emoji in
   * @param imagePath The relative path from image_map.json
   * @param emojiName The base name for the emoji (without prefix)
   * @return The emoji in Discord format <:name:id> or null if failed
   */
  public static String getOrCreateEmoji(Guild guild, String imagePath, String emojiName) {
    if (guild == null || imagePath == null || imagePath.isEmpty() || emojiName == null || emojiName.isEmpty()) {
      return null;
    }
    
    // Sanitize emoji name (Discord requires alphanumeric + underscores)
    String sanitizedName = sanitizeEmojiName(emojiName);
    String fullEmojiName = BOT_EMOJI_PREFIX + sanitizedName;
    
    // Check cache first
    if (emojiCache.containsKey(fullEmojiName)) {
      String emojiId = emojiCache.get(fullEmojiName);
      return "<:" + fullEmojiName + ":" + emojiId + ">";
    }
    
    // Check if emoji already exists in guild
    try {
      List<RichCustomEmoji> emojis = guild.retrieveEmojis().complete();
      for (RichCustomEmoji emoji : emojis) {
        if (emoji.getName().equals(fullEmojiName)) {
          emojiCache.put(fullEmojiName, emoji.getId());
          return "<:" + fullEmojiName + ":" + emoji.getId() + ">";
        }
      }
    } catch (Exception e) {
      System.err.println("Error retrieving emojis: " + e.getMessage());
      return null;
    }
    
    // Emoji doesn't exist, create it
    try {
      // Check emoji limit
      List<RichCustomEmoji> currentEmojis = guild.retrieveEmojis().complete();
      int botEmojiCount = 0;
      for (RichCustomEmoji emoji : currentEmojis) {
        if (emoji.getName().startsWith(BOT_EMOJI_PREFIX)) {
          botEmojiCount++;
        }
      }
      
      // If at limit, delete a random bot emoji
      if (botEmojiCount >= MAX_BOT_EMOJIS) {
        deleteRandomBotEmoji(guild, currentEmojis);
      }
      
      // Download and process image
      String imageUrl = ImageMapCache.buildImageUrl(imagePath);
      byte[] processedImage = downloadAndProcessImage(imageUrl);
      
      if (processedImage == null) {
        System.err.println("Failed to process image for emoji: " + fullEmojiName);
        return null;
      }
      
      // Upload as app emoji
      RichCustomEmoji newEmoji = guild.createEmoji(fullEmojiName, net.dv8tion.jda.api.entities.Icon.from(processedImage)).complete();
      emojiCache.put(fullEmojiName, newEmoji.getId());
      
      System.out.println("Created new app emoji: " + fullEmojiName + " (ID: " + newEmoji.getId() + ")");
      
      return "<:" + fullEmojiName + ":" + newEmoji.getId() + ">";
      
    } catch (Exception e) {
      System.err.println("Error creating emoji " + fullEmojiName + ": " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Download image from URL and process it according to requirements
   * - Remove transparent background with 25% tolerance
   * - Crop to content
   * - Resize to 128x128 (centered, maintaining aspect ratio)
   * - Compress to max 256KB
   */
  private static byte[] downloadAndProcessImage(String imageUrl) {
    try {
      // Download image
      URL url = new URL(imageUrl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      if (conn.getResponseCode() != 200) {
        System.err.println("Failed to download image: HTTP " + conn.getResponseCode() + " for " + imageUrl);
        return null;
      }
      
      BufferedImage originalImage;
      try (InputStream is = conn.getInputStream()) {
        originalImage = ImageIO.read(is);
      }
      
      if (originalImage == null) {
        System.err.println("Failed to read image from URL: " + imageUrl);
        return null;
      }
      
      // Process image: crop transparent borders with tolerance
      BufferedImage croppedImage = cropTransparentBorders(originalImage, 0.25f);
      
      // Resize to fit within 128x128 while maintaining aspect ratio
      BufferedImage resizedImage = resizeAndCenter(croppedImage, EMOJI_DIMENSION, EMOJI_DIMENSION);
      
      // Compress to PNG with size limit
      return compressImage(resizedImage, MAX_EMOJI_SIZE);
      
    } catch (Exception e) {
      System.err.println("Error processing image from " + imageUrl + ": " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Crop transparent borders from image with tolerance
   * @param image The image to crop
   * @param tolerance Tolerance for transparency (0.0 to 1.0), 0.25 = 25%
   * @return Cropped image
   */
  private static BufferedImage cropTransparentBorders(BufferedImage image, float tolerance) {
    int width = image.getWidth();
    int height = image.getHeight();
    
    int minX = width;
    int minY = height;
    int maxX = 0;
    int maxY = 0;
    
    int toleranceAlpha = (int) (255 * tolerance);
    
    // Find bounding box of non-transparent pixels
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixel = image.getRGB(x, y);
        int alpha = (pixel >> 24) & 0xff;
        
        // If pixel is not transparent (beyond tolerance)
        if (alpha > toleranceAlpha) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    
    // Handle case where image is completely transparent
    if (minX > maxX || minY > maxY) {
      return image;
    }
    
    // Crop to bounding box
    int cropWidth = maxX - minX + 1;
    int cropHeight = maxY - minY + 1;
    
    return image.getSubimage(minX, minY, cropWidth, cropHeight);
  }
  
  /**
   * Resize image to fit within target dimensions while maintaining aspect ratio
   * and center it on a transparent canvas
   * @param image The image to resize
   * @param targetWidth Target width
   * @param targetHeight Target height
   * @return Resized and centered image
   */
  private static BufferedImage resizeAndCenter(BufferedImage image, int targetWidth, int targetHeight) {
    try {
      int originalWidth = image.getWidth();
      int originalHeight = image.getHeight();
      
      // Calculate scaling factor to fit within target dimensions
      double scaleX = (double) targetWidth / originalWidth;
      double scaleY = (double) targetHeight / originalHeight;
      double scale = Math.min(scaleX, scaleY);
      
      int scaledWidth = (int) (originalWidth * scale);
      int scaledHeight = (int) (originalHeight * scale);
      
      // Resize image
      BufferedImage scaledImage = Thumbnails.of(image)
          .size(scaledWidth, scaledHeight)
          .asBufferedImage();
      
      // Create target canvas with transparency
      BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = result.createGraphics();
      
      // Fill with transparent background
      g2d.setColor(new Color(0, 0, 0, 0));
      g2d.fillRect(0, 0, targetWidth, targetHeight);
      
      // Center the scaled image
      int x = (targetWidth - scaledWidth) / 2;
      int y = (targetHeight - scaledHeight) / 2;
      g2d.drawImage(scaledImage, x, y, null);
      g2d.dispose();
      
      return result;
      
    } catch (Exception e) {
      System.err.println("Error resizing image: " + e.getMessage());
      e.printStackTrace();
      return image;
    }
  }
  
  /**
   * Compress image to PNG with size limit
   * @param image The image to compress
   * @param maxSizeBytes Maximum size in bytes
   * @return Compressed image bytes or null if failed
   */
  private static byte[] compressImage(BufferedImage image, int maxSizeBytes) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      // Try PNG compression
      ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
      ImageWriteParam writeParam = writer.getDefaultWriteParam();
      
      try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), writeParam);
      }
      writer.dispose();
      
      byte[] result = baos.toByteArray();
      
      // Check size
      if (result.length <= maxSizeBytes) {
        return result;
      }
      
      System.err.println("Warning: Compressed image size (" + result.length + " bytes) exceeds limit (" + maxSizeBytes + " bytes)");
      
      // If still too large, return anyway (Discord will reject it, but we tried)
      return result;
      
    } catch (Exception e) {
      System.err.println("Error compressing image: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Delete a random bot-created emoji to make room for new ones
   * @param guild The guild
   * @param currentEmojis Current emoji list
   */
  private static void deleteRandomBotEmoji(Guild guild, List<RichCustomEmoji> currentEmojis) {
    try {
      List<RichCustomEmoji> botEmojis = new ArrayList<>();
      for (RichCustomEmoji emoji : currentEmojis) {
        if (emoji.getName().startsWith(BOT_EMOJI_PREFIX)) {
          botEmojis.add(emoji);
        }
      }
      
      if (botEmojis.isEmpty()) {
        return;
      }
      
      // Select random emoji to delete
      Random random = new Random();
      RichCustomEmoji toDelete = botEmojis.get(random.nextInt(botEmojis.size()));
      
      System.out.println("Deleting emoji to make room: " + toDelete.getName() + " (ID: " + toDelete.getId() + ")");
      
      toDelete.delete().complete();
      emojiCache.remove(toDelete.getName());
      
    } catch (Exception e) {
      System.err.println("Error deleting random emoji: " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  /**
   * Sanitize emoji name for Discord (alphanumeric + underscores only)
   * @param name The name to sanitize
   * @return Sanitized name
   */
  private static String sanitizeEmojiName(String name) {
    // Replace spaces and special characters with underscores
    String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
    
    // Remove consecutive underscores
    sanitized = sanitized.replaceAll("_+", "_");
    
    // Remove leading/trailing underscores
    sanitized = sanitized.replaceAll("^_+|_+$", "");
    
    // Discord emoji names must be 2-32 characters
    if (sanitized.length() < 2) {
      sanitized = "item_" + sanitized;
    }
    if (sanitized.length() > 32) {
      sanitized = sanitized.substring(0, 32);
    }
    
    return sanitized.toLowerCase();
  }
  
  /**
   * Clear the emoji cache (useful for testing or refresh)
   */
  public static void clearCache() {
    emojiCache.clear();
  }
}
