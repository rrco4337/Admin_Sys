import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import javax.swing.JTextArea;

public class Subserver {

    private static boolean isRunning = true;
    public static JTextArea messageArea;

    public static void startSubServer(SubServerInfo subServer) {
        try (ServerSocket subServerSocket = new ServerSocket(subServer.port)) {
            messageArea.append("Sous-serveur démarré : " + subServer + "\n");

            File storageDir = new File(subServer.storagePath);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            handleSubServer(subServer, subServerSocket);
        } catch (IOException e) {
            messageArea.append("Erreur au démarrage du sous-serveur " + subServer + ": " + e.getMessage() + "\n");
        }
    }

    private static void handleSubServer(SubServerInfo subServer, ServerSocket subServerSocket) {
        while (isRunning) {
            try (Socket clientSocket = subServerSocket.accept();
                 InputStream in = clientSocket.getInputStream();
                 DataInputStream dataIn = new DataInputStream(in);
                 OutputStream out = clientSocket.getOutputStream();
                 DataOutputStream dataOut = new DataOutputStream(out)) {

                String command = dataIn.readUTF();

                if ("UPLOAD".equalsIgnoreCase(command)) {
                    String fileName = dataIn.readUTF();
                    long fileSize = dataIn.readLong();

                    File receivedFile = new File(subServer.storagePath, fileName);
                    try (FileOutputStream fileOut = new FileOutputStream(receivedFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long remaining = fileSize;
                        while ((bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                            fileOut.write(buffer, 0, bytesRead);
                            remaining -= bytesRead;
                        }
                    }

                    messageArea.append("Fichier reçu par " + subServer + ": " + fileName + "\n");
                } else if ("SEND_PART".equalsIgnoreCase(command)) {
                    String partName = dataIn.readUTF();

                    File partFile = new File(subServer.storagePath, partName);
                    if (partFile.exists()) {
                        dataOut.writeUTF("OK");
                        dataOut.writeLong(partFile.length());

                        try (FileInputStream fileIn = new FileInputStream(partFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fileIn.read(buffer)) > 0) {
                                dataOut.write(buffer, 0, bytesRead);
                            }
                        }
                        messageArea.append("Partie envoyée : " + partFile.getName() + "\n");
                    } else {
                        dataOut.writeUTF("ERROR");
                        messageArea.append("Partie introuvable : " + partName + "\n");
                    }
                } else if (command.equalsIgnoreCase("LIST_PARTS")) {
                    File directory = new File(subServer.storagePath); // Répertoire des fichiers
                    File[] files = directory.listFiles();
                    if (files != null) {
                        dataOut.writeUTF("OK");
                        dataOut.writeInt(files.length); // Nombre de fichiers
                        for (File file : files) {
                            if (file.isFile() && file.getName().contains(".part")) {
                                dataOut.writeUTF(file.getName());
                            }
                        }
                    } else {
                        dataOut.writeUTF("ERROR");
                    }
                }                
            } catch (IOException e) {
                messageArea.append("Erreur dans " + subServer + ": " + e.getMessage() + "\n");
            }
        }
    }
}
