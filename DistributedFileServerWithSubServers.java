import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

public class DistributedFileServerWithSubServers {
    private static JTextArea messageArea;
    private static JButton startStopButton;
    private static boolean isRunning = false;
    private static ServerSocket serverSocket;
    private static java.util.List<SubServerInfo> subServers = new ArrayList<>();

    public static void main(String[] args) {
        
        // Créer la fenêtre principale
        JFrame frame = new JFrame("Serveur Principal avec Sous-Serveurs");
        frame.setLayout(new BorderLayout());
        frame.setSize(600, 400);
        
        // Zone de texte pour afficher les messages
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        loadConfiguration();
        JScrollPane scrollPane = new JScrollPane(messageArea);
        frame.add(scrollPane, BorderLayout.CENTER);
    
        // Panneau supérieur avec un bouton de démarrage/arrêt
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startStopButton = new JButton("Démarrer le serveur");
        JLabel statusLabel = new JLabel("État : Arrêté");
        topPanel.add(startStopButton);
        topPanel.add(statusLabel);
        frame.add(topPanel, BorderLayout.NORTH);
    
        // Bouton de démarrage/arrêt
        startStopButton.addActionListener(e -> {
            if (!isRunning) {
                new Thread(() -> startServer(statusLabel)).start();
            } else {
                stopServer(statusLabel);
            }
        });
    
        // Configurer et afficher la fenêtre
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }    

    private static void startServer(JLabel statusLabel) {
        try {
            // Charger le port du serveur principal depuis la configuration
            int port = Integer.parseInt(loadConfig("main.server.port", "1234"));
    
            // Vérifier que les sous-serveurs sont configurés correctement
            if (subServers.isEmpty()) {
                messageArea.append("Erreur : Aucun sous-serveur configuré. Vérifiez la configuration.\n");
                return;
            }
    
            // Initialiser le socket principal
            serverSocket = new ServerSocket(port);
            isRunning = true;
            startStopButton.setText("Arrêter le serveur");
            statusLabel.setText("État : En cours d'exécution sur le port " + port);
            messageArea.append("Serveur principal démarré sur le port " + port + "\n");
    
            // Boucle principale pour gérer les connexions des clients
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    messageArea.append("Client connecté : " + clientSocket.getInetAddress() + "\n");
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) {
                        messageArea.append("Erreur lors de la connexion du client : " + e.getMessage() + "\n");
                    }
                }
            }
        } catch (NumberFormatException e) {
            messageArea.append("Erreur : Port de serveur principal invalide. Vérifiez la configuration.\n");
        } catch (IOException e) {
            messageArea.append("Erreur lors du démarrage du serveur principal : " + e.getMessage() + "\n");
        } finally {
            stopServer(statusLabel);
        }
    }
    
    // Fonction pour charger une configuration avec une valeur par défaut
    private static String loadConfig(String key, String defaultValue) {
        Properties config = new Properties();
        try (FileInputStream configFile = new FileInputStream("config.properties")) {
            config.load(configFile);
        } catch (IOException e) {
            messageArea.append("Impossible de charger le fichier de configuration. Utilisation des valeurs par défaut.\n");
        }
        return config.getProperty(key, defaultValue);
    }
    

    private static void stopServer(JLabel statusLabel) {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            startStopButton.setText("Démarrer le serveur");
            statusLabel.setText("État : Arrêté");
            messageArea.append("Serveur principal arrêté.\n");
            clearTempFolder();
        } catch (IOException e) {
            messageArea.append("Erreur lors de l'arrêt : " + e.getMessage() + "\n");
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in);
             OutputStream out = clientSocket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out))
              {

            String command = dataIn.readUTF();

            if ("UPLOAD".equalsIgnoreCase(command)) {
                String fileName = dataIn.readUTF();
                long fileSize = dataIn.readLong();
                messageArea.append("Réception du fichier : " + fileName + "\n");

                // Recevoir le fichier
                File tempFile = new File("temp/" + fileName);
                tempFile.getParentFile().mkdirs(); // Créer les dossiers nécessaires
                try (FileOutputStream fileOut = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long remaining = fileSize;
                    while ((bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                messageArea.append("Fichier temporaire reçu : " + tempFile.getAbsolutePath() + "\n");

                // Diviser et distribuer le fichier
                splitAndDistributeFile(tempFile, subServers.size());
            } else if ("DOWNLOAD".equalsIgnoreCase(command)) {
                String fileName = dataIn.readUTF();
                messageArea.append("Client demande le fichier : " + fileName + "\n");
            
                // Assemblez le fichier à partir des parties stockées sur les sous-serveurs
                File assembledFile = assembleFileFromSubServers(fileName);
                if (assembledFile != null && assembledFile.exists()) {
                    dataOut.writeUTF("OK");
                    dataOut.writeLong(assembledFile.length());
            
                    // Envoyez le fichier assemblé au client
                    try (FileInputStream fileIn = new FileInputStream(assembledFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fileIn.read(buffer)) > 0) {
                            dataOut.write(buffer, 0, bytesRead);
                        }
                    }
                    messageArea.append("Fichier envoyé au client : " + assembledFile.getName() + "\n");
                } else {
                    dataOut.writeUTF("ERROR");
                    messageArea.append("Erreur : fichier non trouvé ou assemblage échoué.\n");
                }
            } else if("LIST_FILES".equalsIgnoreCase(command)){
                String[] files = showConsolidatedFileListAndDownload(subServers);
                if (files != null && files.length > 0) {
                    dataOut.writeUTF("OK");
                    dataOut.writeInt(files.length);
                    for (String file : files) {
                        dataOut.writeUTF(file);
                    }
                } else {
                    dataOut.writeUTF("OK");
                    dataOut.writeInt(0); // Aucun fichier disponible
                }
            } else {
            
                    dataOut.writeInt(0); // Aucun fichier disponible
                }
            } catch (IOException e) {
            messageArea.append("Erreur de transfert : " + e.getMessage() + "\n");
        }
    }
    private static Map<String, List<String>> collectFilePartsFromServers(List<SubServerInfo> subServers) {
        Map<String, List<String>> fileParts = new HashMap<>();

        for (SubServerInfo server : subServers) {
            try (Socket socket = new Socket(server.host, server.port);
                 OutputStream out = socket.getOutputStream();
                 DataOutputStream dataOut = new DataOutputStream(out);
                 InputStream in = socket.getInputStream();
                 DataInputStream dataIn = new DataInputStream(in)) {

                dataOut.writeUTF("LIST_PARTS");

                String response = dataIn.readUTF();
                if ("OK".equalsIgnoreCase(response)) {
                    int partCount = dataIn.readInt();
                    for (int i = 0; i < partCount; i++) {
                        String partName = dataIn.readUTF();

                        // Extraire le nom de fichier sans extension ".partX"
                        String fileName = partName.substring(0, partName.lastIndexOf(".part"));
                        fileParts.putIfAbsent(fileName, new ArrayList<>());
                        fileParts.get(fileName).add(partName);
                    }
                }
            } catch (IOException e) {
                messageArea.append("Erreur de connexion au sous-serveur " + server + " : " + e.getMessage() + "\n");
            }
        }

        return fileParts;
    }
    private static String[] showConsolidatedFileListAndDownload(List<SubServerInfo> subServers) {
        Map<String, List<String>> fileParts = collectFilePartsFromServers(subServers);

        // Construire une liste des fichiers complets disponibles
        String[] consolidatedFiles = fileParts.keySet().toArray(new String[0]);
        return consolidatedFiles;
    }

    private static void splitAndDistributeFile(File file, int numParts) throws IOException {
        // Taille de chaque partie
        long fileSize = file.length();
        long partSize = fileSize / numParts;
        long remainingBytes = fileSize % numParts;

        try (FileInputStream fileIn = new FileInputStream(file)) {
            for (int i = 0; i < numParts; i++) {
                File partFile = new File("temp/" + file.getName() + ".part" + (i + 1));
                try (FileOutputStream partOut = new FileOutputStream(partFile)) {
                    byte[] buffer = new byte[4096];
                    long bytesToWrite = partSize + (i == numParts - 1 ? remainingBytes : 0);
                    int bytesRead;
                    while (bytesToWrite > 0 && (bytesRead = fileIn.read(buffer, 0, (int) Math.min(buffer.length, bytesToWrite))) > 0) {
                        partOut.write(buffer, 0, bytesRead);
                        bytesToWrite -= bytesRead;
                    }
                }

                // Envoyer la partie au sous-serveur
                distributePartToSubServer(partFile, subServers.get(i));

                if (partFile.delete()) {
                    messageArea.append("Partie supprimée : " + partFile.getName() + "\n");
                } else {
                    messageArea.append("Impossible de supprimer : " + partFile.getName() + "\n");
                }
            }
        }
    }

    private static void distributePartToSubServer(File partFile, SubServerInfo subServer) {
        try (Socket socket = new Socket(subServer.host, subServer.port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             FileInputStream fileIn = new FileInputStream(partFile)) {

            // Envoyer la commande UPLOAD au sous-serveur
            dataOut.writeUTF("UPLOAD");
            dataOut.writeUTF(partFile.getName());
            dataOut.writeLong(partFile.length());

            // Transférer la partie
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                dataOut.write(buffer, 0, bytesRead);
            }

            messageArea.append("Partie envoyée : " + partFile.getName() + " -> " + subServer + "\n");

        } catch (IOException e) {
            messageArea.append("Erreur lors de la distribution vers " + subServer + ": " + e.getMessage() + "\n");
        }
    }

    private static File assembleFileFromSubServers(String fileName) {
        File assembledFile = new File("temp/" + fileName);
    
        try (FileOutputStream fileOut = new FileOutputStream(assembledFile)) {
            for (int i = 0; i < subServers.size(); i++) {
                SubServerInfo subServer = subServers.get(i);
                String partName = fileName + ".part" + (i + 1);
    
                try (Socket socket = new Socket(subServer.host, subServer.port);
                     OutputStream out = socket.getOutputStream();
                     DataOutputStream dataOut = new DataOutputStream(out);
                     InputStream in = socket.getInputStream();
                     DataInputStream dataIn = new DataInputStream(in)) {
    
                    // Demande de la partie au sous-serveur
                    dataOut.writeUTF("SEND_PART");
                    dataOut.writeUTF(partName);
    
                    String response = dataIn.readUTF();
                    if ("OK".equalsIgnoreCase(response)) {
                        long partSize = dataIn.readLong();
    
                        byte[] buffer = new byte[4096];
                        long remaining = partSize;
                        int bytesRead;
                        while (remaining > 0 && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                            fileOut.write(buffer, 0, bytesRead);
                            remaining -= bytesRead;
                        }
                        messageArea.append("Partie récupérée et assemblée : " + partName + "\n");
                    } else {
                        messageArea.append("Erreur : partie non trouvée sur " + subServer + "\n");
                    }
                }
            }
        } catch (IOException e) {
            messageArea.append("Erreur lors de l'assemblage du fichier : " + e.getMessage() + "\n");
            return null;
        }

        assembledFile.deleteOnExit();
        return assembledFile;
    }        

    private static void clearTempFolder() {
        File tempDir = new File("temp");
        if (tempDir.exists() && tempDir.isDirectory()) {
            for (File file : tempDir.listFiles()) {
                if (file.isFile() && file.delete()) {
                    messageArea.append("Fichier temporaire supprimé : " + file.getName() + "\n");
                }
            }
        }
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
    
            messageArea.append("Configuration chargée : " + subServers.size() + " sous-serveurs configurés.\n");
        } catch (IOException e) {
            messageArea.append("Erreur lors du chargement de la configuration : " + e.getMessage() + "\n");
        }
    }    
}