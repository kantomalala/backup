import java.io.*;

public class SecondaryServerInfo implements java.io.Serializable {
    private int id;
    private String ip;
    private String storagePath;
    private int port;

    // Getters et Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}