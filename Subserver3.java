import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Properties;

public class Subserver3 {
    public static java.util.List<SubServerInfo> subServers = new ArrayList<>();
    public static void main(String[] args) {
        loadConfiguration();
        SubServerMain start = new SubServerMain(subServers.get(2));    
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
    
            // Charger le port du serveur principal
            int mainServerPort = Integer.parseInt(properties.getProperty("main.server.port", "1234"));
    
            // Charger les sous-serveurs
            subServers.clear();
            int index = 1;
            while (true) {
                String hostKey = "subserver." + index + ".host";
                String portKey = "subserver." + index + ".port";
                String pathKey = "subserver." + index + ".storagePath";
    
                if (!properties.containsKey(hostKey) || !properties.containsKey(portKey) || !properties.containsKey(pathKey)) {
                    break;
                }
    
                String host = properties.getProperty(hostKey);
                int port = Integer.parseInt(properties.getProperty(portKey));
                String storagePath = properties.getProperty(pathKey);
    
                subServers.add(new SubServerInfo(host, port, storagePath));
                index++;
            }
    
            System.out.println("Configuration chargée : " + subServers.size() + " sous-serveurs configurés.\n");
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement de la configuration : " + e.getMessage() + "\n");
        }
    }
}
