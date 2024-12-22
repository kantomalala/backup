import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;

public class Client extends JFrame {
    private final JList<String> fileList;
    private final DefaultListModel<String> listModel;
    private final String serverIp;
    private final int serverPort;
    private final JTextArea logArea;

    public Client() {
        // Initialisation des paramètres
        this.serverIp = "localhost";
        this.serverPort = 5000;
        this.listModel = new DefaultListModel<>();
        this.fileList = new JList<>(listModel);
        this.logArea = new JTextArea(5, 40);
        
        setupGUI();
        refreshFileList(); // Charger la liste initiale
    }

    private void setupGUI() {
        setTitle("Client de Transfert de Fichiers");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Panel principal avec BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel pour la liste des fichiers
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("Fichiers disponibles"));
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // Panel pour les logs
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Logs"));
        logArea.setEditable(false);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Panel pour les boutons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton uploadButton = new JButton("Envoyer un fichier");
        uploadButton.addActionListener(e -> uploadFile());
        
        JButton downloadButton = new JButton("Télécharger");
        downloadButton.addActionListener(e -> downloadSelectedFile());
        
        JButton refreshButton = new JButton("Rafraîchir la liste");
        refreshButton.addActionListener(e -> refreshFileList());


JButton deleteButton = new JButton("Supprimer le fichier");
deleteButton.addActionListener(e -> deleteSelectedFile()); 

        buttonPanel.add(uploadButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(deleteButton);

        // Assembler les panels
        mainPanel.add(listPanel, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);
        mainPanel.add(buttonPanel, BorderLayout.NORTH);


        add(mainPanel);
        
        // Status initial
        log("Client démarré - Connecté à " + serverIp + ":" + serverPort);
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void uploadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionner un fichier à envoyer");
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                Socket socket = new Socket(serverIp, serverPort);
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    out.writeObject("CLIENT");
                    out.writeObject("UPLOAD");
                    out.writeObject(file.getName());
                    out.writeObject(file.length());

                    byte[] fileData = Files.readAllBytes(file.toPath());
                    out.writeObject(fileData);

                    log("Fichier envoyé avec succès: " + file.getName());
                    refreshFileList();
                }
            } catch (IOException e) {
                log("Erreur lors de l'envoi: " + e.getMessage());
                JOptionPane.showMessageDialog(this, 
                    "Erreur lors de l'envoi du fichier: " + e.getMessage(),
                    "Erreur d'envoi",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void downloadSelectedFile() {
        String selectedFile = fileList.getSelectedValue();
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this,
                "Veuillez sélectionner un fichier à télécharger",
                "Sélection requise",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Enregistrer le fichier");
        fileChooser.setSelectedFile(new File(selectedFile));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                Socket socket = new Socket(serverIp, serverPort);
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    out.writeObject("CLIENT");
                    out.writeObject("DOWNLOAD");
                    out.writeObject(selectedFile);

                    byte[] fileData = (byte[]) in.readObject();
                    if (fileData != null) {
                        Files.write(fileChooser.getSelectedFile().toPath(), fileData);
                        log("Fichier téléchargé avec succès: " + selectedFile);
                    } else {
                        throw new IOException("Le fichier n'a pas pu être récupéré du serveur");
                    }
                }
            } catch (Exception e) {
                log("Erreur lors du téléchargement: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Erreur lors du téléchargement: " + e.getMessage(),
                    "Erreur de téléchargement",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshFileList() {
        try {
            Socket socket = new Socket(serverIp, serverPort);
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("CLIENT");
                out.writeObject("LIST_FILES");

                @SuppressWarnings("unchecked")
                List<String> files = (List<String>) in.readObject();
                
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    files.forEach(listModel::addElement);
                    log("Liste des fichiers mise à jour");
                });
            }
        } catch (Exception e) {
            log("Erreur lors du rafraîchissement de la liste: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Impossible de récupérer la liste des fichiers: " + e.getMessage(),
                "Erreur de connexion",
                JOptionPane.ERROR_MESSAGE);
        }
    }


    private void deleteSelectedFile() {
        String selectedFile = fileList.getSelectedValue();
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this,
                "Veuillez sélectionner un fichier à supprimer",
                "Sélection requise",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
    
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Voulez-vous vraiment supprimer le fichier : " + selectedFile + " ?", 
            "Confirmation de suppression", 
            JOptionPane.YES_NO_OPTION);
    
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Connect to the server to request the deletion of the file
                Socket socket = new Socket(serverIp, serverPort);
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
    
                    out.writeObject("CLIENT");
                    out.writeObject("DELETE");
                    out.writeObject(selectedFile);  // Send the selected file name to the server
    
                    String response = (String) in.readObject();
                    if ("SUCCESS".equals(response)) {
                        log("Fichier supprimé avec succès: " + selectedFile);
                        refreshFileList();  // Refresh the file list after deletion
                    } else {
                        log("Erreur lors de la suppression du fichier: " + selectedFile);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                log("Erreur lors de la suppression: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Erreur lors de la suppression du fichier: " + e.getMessage(),
                    "Erreur de suppression",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            // Set Look and Feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            client.setVisible(true);
        });
    }
}