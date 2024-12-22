import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;
import java.util.Map;

public class ClientSans {
    private final String serverIp = "localhost";
    private final int serverPort = 5000;

    public static void main(String[] args) {
        ClientSans client = new ClientSans();
        client.start();
    }

    private void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nMenu:");
                System.out.println("1. Rafraîchir la liste des fichiers");
                System.out.println("2. Télécharger un fichier");
                System.out.println("3. Envoyer un fichier");
                System.out.println("4. Supprimer un fichier");
                System.out.println("5. Lister les fichiers envoyés");
                System.out.println("6. Vérifier la répartition des fichiers");
                System.out.println("7. Quitter");
                System.out.print("Choisissez une option : ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1" -> refreshFileList();
                    case "2" -> downloadFile(scanner);
                    case "3" -> uploadFile(scanner);
                    case "4" -> deleteFile(scanner);
                    case "5" -> listSentFiles();
                    case "6" -> checkDistribution();
                    case "7" -> {
                        System.out.println("Client arrêté.");
                        return;
                    }
                    default -> System.out.println("Option invalide. Veuillez réessayer.");
                }
            }
        }
    }

    private void refreshFileList() {
        try (Socket socket = new Socket(serverIp, serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("CLIENT");
            out.writeObject("LIST_FILES");

            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) in.readObject();
            System.out.println("Fichiers disponibles :");
            files.forEach(file -> System.out.println(" - " + file));
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération de la liste des fichiers : " + e.getMessage());
        }
    }

    private void downloadFile(Scanner scanner) {
        System.out.print("Entrez le nom du fichier à télécharger : ");
        String fileName = scanner.nextLine();
    
        System.out.print("Entrez le chemin pour enregistrer le fichier : ");
        String savePath = scanner.nextLine();
    
        // Vérifier si le répertoire existe, sinon créer
        File saveDirectory = new File(savePath);
        if (!saveDirectory.exists()) {
            if (saveDirectory.mkdirs()) {
                System.out.println("Répertoire créé : " + savePath);
            } else {
                System.err.println("Impossible de créer le répertoire : " + savePath);
                return;
            }
        }
    
        try (Socket socket = new Socket(serverIp, serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
    
            out.writeObject("CLIENT");
            out.writeObject("DOWNLOAD");
            out.writeObject(fileName);
    
            byte[] fileData = (byte[]) in.readObject();
            if (fileData != null) {
                Path path = Paths.get(savePath, fileName);
                Files.write(path, fileData);
                System.out.println("Fichier téléchargé avec succès : " + fileName);
            } else {
                System.err.println("Erreur : fichier introuvable sur le serveur.");
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erreur lors du téléchargement : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void uploadFile(Scanner scanner) {
        System.out.print("Entrez le chemin du fichier à envoyer : ");
        String filePath = scanner.nextLine();

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("Erreur : fichier invalide.");
            return;
        }

        try (Socket socket = new Socket(serverIp, serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("CLIENT");
            out.writeObject("UPLOAD");
            out.writeObject(file.getName());
            out.writeObject(file.length());

            byte[] fileData = Files.readAllBytes(file.toPath());
            out.writeObject(fileData);

            // Ajouter le fichier à la liste locale des fichiers envoyés
            recordSentFile(file.getName());

            System.out.println("Fichier envoyé avec succès : " + file.getName());
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi du fichier : " + e.getMessage());
        }
    }

    private void deleteFile(Scanner scanner) {
        System.out.print("Entrez le nom du fichier à supprimer : ");
        String fileName = scanner.nextLine();

        try (Socket socket = new Socket(serverIp, serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("CLIENT");
            out.writeObject("DELETE");
            out.writeObject(fileName);

            String response = (String) in.readObject();
            if ("SUCCESS".equals(response)) {
                System.out.println("Fichier supprimé avec succès : " + fileName);
            } else {
                System.err.println("Erreur lors de la suppression du fichier.");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la suppression : " + e.getMessage());
        }
    }

    private void listSentFiles() {
        System.out.println("Fichiers envoyés par le client (fonctionnalité locale) :");
        // Cette liste pourrait être mise à jour à chaque upload
        try (BufferedReader br = new BufferedReader(new FileReader("sent_files.txt"))) {
            br.lines().forEach(System.out::println);
        } catch (IOException e) {
            System.out.println("Aucun fichier enregistré localement pour le moment.");
        }
    }

    private void checkDistribution() {
        try (Socket socket = new Socket(serverIp, serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("CLIENT");
            out.writeObject("VERIFY");

            // Récupérer la répartition des fichiers
            @SuppressWarnings("unchecked")
            Map<String, List<String>> distribution = (Map<String, List<String>>) in.readObject();
            System.out.println("Répartition des fichiers entre serveurs :");
            distribution.forEach((file, partitions) -> {
                System.out.println("Fichier : " + file);
                partitions.forEach(partition -> System.out.println("  - " + partition));
            });

        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification de la répartition des fichiers : " + e.getMessage());
        }
    }

    private void recordSentFile(String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("sent_files.txt", true))) {
            writer.write(fileName);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Erreur lors de l'enregistrement du fichier envoyé : " + e.getMessage());
        }
    }
}
