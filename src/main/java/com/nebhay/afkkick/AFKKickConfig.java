package com.nebhay.afkkick;

public class AFKKickConfig {
    private long afkThresholdMs = 300000; // 5 minutes
    private long checkIntervalMs = 5000; // 5 seconds
    private String kickMessage = "You have been kicked for being AFK for too long.";
    private String warningMessage = "You will be kicked for being AFK in 30 seconds! Move to stay connected.";
    private long warningThresholdMs = 30000; // 30 seconds before kick
    private String chatPreKickMessage = "You have been kicked for being AFK."; // Message sent before kick
    private long chatPreKickDelayMs = 2000; // Delay before kick

    public AFKKickConfig() {
    }

    public long getAfkThresholdMs() {
        return afkThresholdMs;
    }

    public void setAfkThresholdMs(long afkThresholdMs) {
        this.afkThresholdMs = afkThresholdMs;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void setKickMessage(String kickMessage) {
        this.kickMessage = kickMessage;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public long getWarningThresholdMs() {
        return warningThresholdMs;
    }

    public void setWarningThresholdMs(long warningThresholdMs) {
        this.warningThresholdMs = warningThresholdMs;
    }

    public String getChatPreKickMessage() {
        return chatPreKickMessage;
    }

    public void setChatPreKickMessage(String chatPreKickMessage) {
        this.chatPreKickMessage = chatPreKickMessage;
    }

    public long getChatPreKickDelayMs() {
        return chatPreKickDelayMs;
    }

    public void setChatPreKickDelayMs(long chatPreKickDelayMs) {
        this.chatPreKickDelayMs = chatPreKickDelayMs;
    }
}
