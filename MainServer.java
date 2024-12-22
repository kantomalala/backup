
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;


public class MainServer extends JFrame {
    private List<SecondaryServerInfo> secondaryServers;
    private JTextArea serverLog;
    private int mainServerPort = 5000;
    private ServerSocket serverSocket;
    private File storageFile;

    public MainServer() {
        secondaryServers = new ArrayList<>();
        storageFile = new File("storage.txt");
        setupGUI();
        setupServer();
    }

    private void setupGUI() {
        setTitle("Serveur Principal");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Panel pour les serveurs secondaires
        JPanel serversPanel = new JPanel();
        serverLog = new JTextArea(15, 40);
        serverLog.setEditable(false);
        serversPanel.add(new JScrollPane(serverLog));

        // Bouton démarrer
        JButton startButton = new JButton("Démarrer le serveur");
        startButton.addActionListener(e -> startServer());

        mainPanel.add(serversPanel, BorderLayout.CENTER);
        mainPanel.add(startButton, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void setupServer() {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(mainServerPort);
                updateLog("Serveur principal démarré sur le port " + mainServerPort);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    handleNewConnection(clientSocket);
                }
            } catch (IOException e) {
                updateLog("Erreur serveur: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void startServer() {
    if (serverSocket == null || serverSocket.isClosed()) {
        setupServer();
        updateLog("Serveur démarré");
    } else {
        updateLog("Le serveur est déjà en cours d'exécution");
    }
}

private void handleFileDownload(ObjectOutputStream out, String fileName) throws IOException {
    try {
        // Lire les informations du fichier depuis storage.txt
        List<String> partitionPaths = new ArrayList<>();
        int numPartitions = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
            String line;
            boolean foundFile = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(fileName + ";")) {
                    foundFile = true;
                    String[] parts = line.split(";");
                    numPartitions = Integer.parseInt(parts[2]);
                    
                    // Lire les chemins des partitions
                    for (int i = 0; i < numPartitions; i++) {
                        partitionPaths.add(reader.readLine());
                    }
                    break;
                }
            }
            
            if (!foundFile) {
                throw new IOException("Fichier non trouvé");
            }
        }

        // Récupérer et combiner les partitions
        ByteArrayOutputStream combinedFile = new ByteArrayOutputStream();
        for (String partitionPath : partitionPaths) {
            byte[] partitionData = Files.readAllBytes(Paths.get(partitionPath));
            combinedFile.write(partitionData);
        }

        // Envoyer le fichier reconstitué au client
        out.writeObject(combinedFile.toByteArray());
        
    } catch (IOException e) {
        updateLog("Erreur lors du téléchargement: " + e.getMessage());
        out.writeObject(null);
    }
}

private void sendFileList(ObjectOutputStream out) throws IOException {
    List<String> fileNames = new ArrayList<>();
    
    try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(";")) {  // Ligne contenant les infos du fichier
                String fileName = line.split(";")[0];
                fileNames.add(fileName);
            }
        }
    }
    
    out.writeObject(fileNames);
}

private void sendPartitionToSecondary(SecondaryServerInfo server, String partitionName, byte[] partitionData) {
    try (Socket socket = new Socket(server.getIp(), server.getPort());
         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
        
        out.writeObject("STORE_PARTITION");
        out.writeObject(partitionName);
        out.writeObject(partitionData);
        
    } catch (IOException e) {
        updateLog("Erreur lors de l'envoi de la partition au serveur " + server.getId() + ": " + e.getMessage());
    }
}

    private void handleNewConnection(Socket socket) {
        // Gérer la nouvelle connexion dans un thread séparé
        Thread connectionHandler = new Thread(() -> {
            try {
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

                // Lire le type de connexion (CLIENT ou SECONDARY_SERVER)
                String connectionType = (String) in.readObject();

                if (connectionType.equals("SECONDARY_SERVER")) {
                    handleSecondaryServer(socket, in, out);
                } else if (connectionType.equals("CLIENT")) {
                    handleClient(socket, in, out);
                }
            } catch (Exception e) {
                updateLog("Erreur de connexion: " + e.getMessage());
            }
        });
        connectionHandler.start();
    }

    private void handleSecondaryServer(Socket socket, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        SecondaryServerInfo serverInfo = (SecondaryServerInfo) in.readObject();
        serverInfo.setId(secondaryServers.size() + 1);
        secondaryServers.add(serverInfo);
        updateLog("Nouveau serveur secondaire connecté: ID=" + serverInfo.getId() + 
                 ", IP=" + serverInfo.getIp() + ", Port=" + serverInfo.getPort());
    }

    private void handleClient(Socket socket, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        String action = (String) in.readObject();
    
        if (action.equals("UPLOAD")) {
            handleFileUpload(in);
        } else if (action.equals("DOWNLOAD")) {
            handleFileDownload(out, (String) in.readObject());
        } else if (action.equals("LIST_FILES")) {
            sendFileList(out);
        } else if (action.equals("DELETE")) {  // Handle delete action
            String fileName = (String) in.readObject();
            deleteFile(fileName, out);
        }
    }
    

    private void handleFileUpload(ObjectInputStream in) throws IOException, ClassNotFoundException {
        String fileName = (String) in.readObject();
        long fileSize = (Long) in.readObject();
        byte[] fileData = (byte[]) in.readObject();

        // Diviser le fichier en partitions
        int numPartitions = secondaryServers.size();
        int partitionSize = (int) Math.ceil(fileData.length / (double) numPartitions);

        // Distribuer les partitions
        for (int i = 0; i < numPartitions; i++) {
            SecondaryServerInfo server = secondaryServers.get(i);
            int start = i * partitionSize;
            int end = Math.min(start + partitionSize, fileData.length);
            byte[] partition = Arrays.copyOfRange(fileData, start, end);
            
            // Envoyer la partition au serveur secondaire
            sendPartitionToSecondary(server, fileName + ".part" + (i+1), partition);
        }

        // Enregistrer les informations dans storage.txt
        saveToStorage(fileName, fileSize, numPartitions);
    }

    private void saveToStorage(String fileName, long fileSize, int numPartitions) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(storageFile, true))) {
            writer.println(fileName + ";" + fileSize + ";" + numPartitions);
            for (int i = 0; i < numPartitions; i++) {
                writer.println(secondaryServers.get(i).getStoragePath() + "/" + fileName + ".part" + (i+1));
            }
        } catch (IOException e) {
            updateLog("Erreur d'enregistrement dans storage.txt: " + e.getMessage());
        }
    }

    private void updateLog(String message) {
        SwingUtilities.invokeLater(() -> {
            serverLog.append(message + "\n");
        });
    }

    private void deleteFile(String fileName, ObjectOutputStream out) {
        try {
            // Read from storage.txt and remove the file entry
            List<String> lines = new ArrayList<>();
            boolean fileFound = false;
    
            try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(fileName + ";")) {
                        fileFound = true;  // File found, don't add it to the new list
                        // Delete corresponding files from secondary servers
                        String[] parts = line.split(";");
                        int numPartitions = Integer.parseInt(parts[2]);
                        for (int i = 0; i < numPartitions; i++) {
                            String partitionPath = reader.readLine();
                            Files.deleteIfExists(Paths.get(partitionPath));  // Delete the partition file
                        }
                    } else {
                        lines.add(line);  // Keep the lines that don't match the file
                    }
                }
            }
    
            if (fileFound) {
                // Rewrite the storage.txt without the deleted file
                try (PrintWriter writer = new PrintWriter(new FileWriter(storageFile))) {
                    for (String line : lines) {
                        writer.println(line);
                    }
                }
    
                updateLog("Fichier supprimé de storage.txt et des serveurs secondaires: " + fileName);
                out.writeObject("SUCCESS");
            } else {
                out.writeObject("ERROR: Fichier non trouvé");
            }
        } catch (IOException e) {
            updateLog("Erreur lors de la suppression du fichier: " + e.getMessage());
            try {
                out.writeObject("ERROR: " + e.getMessage());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MainServer().setVisible(true);
        });
    }
}

