import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

public class SecondaryServer1 extends JFrame {
    private int port;
    private String storagePath;
    private String mainServerIp;
    private int mainServerPort;
    private ServerSocket serverSocket;
    private JTextArea logArea;

    public SecondaryServer1(int port, String storagePath, String mainServerIp, int mainServerPort) {
        this.port = port;
        this.storagePath = storagePath;
        this.mainServerIp = mainServerIp;
        this.mainServerPort = mainServerPort;
        
        setupGUI();
        createStorageDirectory();
        registerWithMainServer();
        startServer();
    }

    private void setupGUI() {
        setTitle("Serveur Secondaire (Port: " + port + ")");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Afficher les informations de configuration
        JPanel infoPanel = new JPanel(new GridLayout(3, 1));
        infoPanel.add(new JLabel("Port: " + port));
        infoPanel.add(new JLabel("Stockage: " + storagePath));
        infoPanel.add(new JLabel("Serveur Principal: " + mainServerIp + ":" + mainServerPort));
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        add(mainPanel);
    }

    private void createStorageDirectory() {
        File storage = new File(storagePath);
        if (!storage.exists()) {
            storage.mkdirs();
        }
    }

    private void registerWithMainServer() {
        try (Socket socket = new Socket(mainServerIp, mainServerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            
            out.writeObject("SECONDARY_SERVER");
            
            SecondaryServerInfo info = new SecondaryServerInfo();
            info.setIp(InetAddress.getLocalHost().getHostAddress());
            info.setPort(port);
            info.setStoragePath(storagePath);
            
            out.writeObject(info);
            log("Enregistré auprès du serveur principal");
            
        } catch (IOException e) {
            log("Erreur d'enregistrement: " + e.getMessage());
        }
    }

    private void startServer() {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                log("Serveur secondaire démarré sur le port " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    handleConnection(clientSocket);
                }
            } catch (IOException e) {
                log("Erreur serveur: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleConnection(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            String action = (String) in.readObject();
            
            if (action.equals("STORE_PARTITION")) {
                String partitionName = (String) in.readObject();
                byte[] partitionData = (byte[]) in.readObject();
                
                File partitionFile = new File(storagePath, partitionName);
                
                // Utilisation d'un thread séparé pour éviter de bloquer l'interface
                SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        try {
                            Files.write(partitionFile.toPath(), partitionData);
                            log("Partition reçue: " + partitionName);
                        } catch (IOException e) {
                            log("Erreur de suppression: " + e.getMessage());
                        }
                        return null;
                    }
                };
                worker.execute();
            }
        } catch (Exception e) {
            log("Erreur de traitement: " + e.getMessage());
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
        });
    }

    public static void main(String[] args) {
        // Exemple d'utilisation
        SwingUtilities.invokeLater(() -> {
            new SecondaryServer1(5002, "storage/secondary2", "localhost", 5000).setVisible(true);
        });
    }
}
