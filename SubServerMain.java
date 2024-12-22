import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SubServerMain extends JFrame {

    private JTextArea messageArea;
    private SubServerInfo subServerInfo;

    public SubServerMain(SubServerInfo subServerInfo) {
        this.subServerInfo = subServerInfo;

        // Configuration de la fenêtre principale
        setTitle("Sous-Serveur - " + subServerInfo.host + ":" + subServerInfo.port);
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel pour afficher les informations du sous-serveur
        JPanel infoPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        infoPanel.add(new JLabel("Hôte : " + subServerInfo.host));
        infoPanel.add(new JLabel("Port : " + subServerInfo.port));
        infoPanel.add(new JLabel("Chemin de stockage : " + subServerInfo.storagePath));

        JButton startButton = new JButton("Démarrer Sous-Serveur");
        infoPanel.add(startButton);

        // Zone d'affichage des messages
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        JScrollPane messageScrollPane = new JScrollPane(messageArea);

        // Ajout des composants à la fenêtre
        add(infoPanel, BorderLayout.NORTH);
        add(messageScrollPane, BorderLayout.CENTER);

        // Action pour le bouton de démarrage
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startSubServerAction();
            }
        });

        setVisible(true);
    }

    private void startSubServerAction() {
        // Définit la zone de message globale pour Subserver
        Subserver.messageArea = messageArea;

        // Démarre le sous-serveur dans un nouveau thread
        new Thread(() -> Subserver.startSubServer(subServerInfo)).start();

        messageArea.append("Tentative de démarrage du sous-serveur : " + subServerInfo + "...\n");
    }

    public static void main(String[] args) {
        // Exemple de configuration pour un sous-serveur
        SubServerInfo subServerInfo = new SubServerInfo("localhost", 8081, "/path/to/storage");

        // Création de l'interface utilisateur avec les informations du sous-serveur
        SwingUtilities.invokeLater(() -> new SubServerMain(subServerInfo));
    }
}

