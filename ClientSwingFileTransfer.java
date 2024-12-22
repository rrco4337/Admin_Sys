import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

public class ClientSwingFileTransfer {
    private static JTextArea messageArea;
    private static JTextField ipField, portField;
    private static JButton selectFileButton, sendFileButton, downloadFileButton;
    private static File selectedFile;

    public static void main(String[] args) {
        // Créer la fenêtre principale
        JFrame frame = new JFrame("Client de Transfert de Fichiers");
        frame.setLayout(new BorderLayout());
        frame.setSize(600, 400);

        // Panneau supérieur pour les informations de connexion
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("IP du serveur :"));
        ipField = new JTextField("localhost", 10);
        topPanel.add(ipField);
        topPanel.add(new JLabel("Port :"));
        portField = new JTextField("1234", 5);
        topPanel.add(portField);
        frame.add(topPanel, BorderLayout.NORTH);

        // Zone de texte pour afficher les messages
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Panneau inférieur pour les actions
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectFileButton = new JButton("Sélectionner un fichier");
        sendFileButton = new JButton("Envoyer le fichier");
        sendFileButton.setEnabled(false);
        downloadFileButton = new JButton("Télécharger un fichier");
        bottomPanel.add(selectFileButton);
        bottomPanel.add(sendFileButton);
        bottomPanel.add(downloadFileButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Action pour sélectionner un fichier
        selectFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                selectedFile = fileChooser.getSelectedFile();
                messageArea.append("Fichier sélectionné : " + selectedFile.getAbsolutePath() + "\n");
                sendFileButton.setEnabled(true);
            }
        });

        // Action pour envoyer un fichier
        sendFileButton.addActionListener(e -> sendFile());

        // Action pour télécharger un fichier
        downloadFileButton.addActionListener(e -> showFileListAndDownload());

        // Configurer et afficher la fenêtre
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void sendFile() {
        if (selectedFile == null) {
            messageArea.append("Aucun fichier sélectionné.\n");
            return;
        }

        String serverIP = ipField.getText();
        int port = Integer.parseInt(portField.getText());

        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             FileInputStream fileIn = new FileInputStream(selectedFile)) {

            dataOut.writeUTF("UPLOAD");
            dataOut.writeUTF(selectedFile.getName());
            dataOut.writeLong(selectedFile.length()); // Envoyer la taille du fichier

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                dataOut.write(buffer, 0, bytesRead);
            }

            messageArea.append("Fichier envoyé avec succès : " + selectedFile.getName() + "\n");
        } catch (IOException e) {
            messageArea.append("Erreur d'envoi : " + e.getMessage() + "\n");
        }
    }

    private static void showFileListAndDownload() {
        String serverIP = ipField.getText();
        int port = Integer.parseInt(portField.getText());
    
        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             InputStream in = socket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in)) {
    
            // Envoyer une commande pour demander la liste des fichiers disponibles
            dataOut.writeUTF("LIST_FILES");
    
            // Lire la réponse du serveur
            String response = dataIn.readUTF();
            if ("OK".equalsIgnoreCase(response)) {
                // Lire le nombre de fichiers disponibles
                int fileCount = dataIn.readInt();
                if (fileCount == 0) {
                    messageArea.append("Aucun fichier disponible sur le serveur.\n");
                    return;
                }
    
                // Lire les noms des fichiers disponibles
                String[] fileList = new String[fileCount];
                for (int i = 0; i < fileCount; i++) {
                    fileList[i] = dataIn.readUTF();
                }
    
                // Afficher la liste des fichiers à l'utilisateur pour sélection
                String selectedFile = (String) JOptionPane.showInputDialog(
                        null,
                        "Sélectionnez un fichier à télécharger :",
                        "Liste des fichiers disponibles",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        fileList,
                        fileList[0]);
    
                if (selectedFile != null && !selectedFile.trim().isEmpty()) {
                    // Télécharger le fichier sélectionné
                    downloadFile(selectedFile.trim());
                } else {
                    messageArea.append("Téléchargement annulé : aucun fichier sélectionné.\n");
                }
            } else {
                messageArea.append("Erreur : le serveur n'a pas pu fournir la liste des fichiers.\n");
            }
        } catch (IOException e) {
            messageArea.append("Erreur lors de la connexion au serveur : " + e.getMessage() + "\n");
        }
    }
    
    
    private static void downloadFile(String fileName) {
        String serverIP = ipField.getText();
        int port = Integer.parseInt(portField.getText());
    
        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             InputStream in = socket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in)) {
    
            dataOut.writeUTF("DOWNLOAD");
            dataOut.writeUTF(fileName);
    
            String response = dataIn.readUTF();
            if ("OK".equalsIgnoreCase(response)) {
                long fileSize = dataIn.readLong();
                
                File tempFile = new File("telechargement/Téléchargé_" + fileName);
                tempFile.getParentFile().mkdirs(); // Créer les dossiers nécessaires
                try (FileOutputStream fileOut = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileSize;
                    int bytesRead;
                    while (remaining > 0 && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }
                messageArea.append("Fichier téléchargé avec succès : " + fileName + "\n");
            } else {
                messageArea.append("Erreur : fichier non trouvé sur le serveur.\n");
            }
        } catch (IOException e) {
            messageArea.append("Erreur de téléchargement : " + e.getMessage() + "\n");
        }
    }        
}
