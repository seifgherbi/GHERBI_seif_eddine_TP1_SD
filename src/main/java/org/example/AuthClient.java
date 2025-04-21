package org.example;

import javax.swing.*;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthClient extends JFrame {
    private authservice authService;

    public AuthClient() {
        try {
            // Connexion au serveur RMI
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            authService = (authservice) registry.lookup("AuthService");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Configuration de la fenêtre
        setTitle("Gestion des Comptes Utilisateurs");
        setSize(500, 350);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(240, 240, 240)); // Fond clair

        // Panel central avec un GridBagLayout pour une meilleure organisation
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        panel.setBackground(new Color(255, 255, 255)); // Fond du panel blanc
        add(panel, BorderLayout.CENTER);

        // Champs de texte et boutons
        JTextField usernameField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);
        JButton addButton = new JButton("Ajouter");
        JButton updateButton = new JButton("Modifier");
        JButton deleteButton = new JButton("Supprimer");

        // Personnalisation des champs de texte
        usernameField.setBackground(new Color(245, 245, 245));
        usernameField.setForeground(new Color(0, 0, 0));
        passwordField.setBackground(new Color(245, 245, 245));
        passwordField.setForeground(new Color(0, 0, 0));

        // Personnalisation des boutons
        addButton.setBackground(new Color(0, 122, 255)); // Bleu pour "Ajouter"
        addButton.setForeground(Color.WHITE);
        addButton.setFocusPainted(false);
        addButton.setBorder(BorderFactory.createLineBorder(new Color(0, 102, 204), 2));

        updateButton.setBackground(new Color(255, 165, 0)); // Orange pour "Modifier"
        updateButton.setForeground(Color.WHITE);
        updateButton.setFocusPainted(false);
        updateButton.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 2));

        deleteButton.setBackground(new Color(255, 69, 0)); // Rouge pour "Supprimer"
        deleteButton.setForeground(Color.WHITE);
        deleteButton.setFocusPainted(false);
        deleteButton.setBorder(BorderFactory.createLineBorder(new Color(255, 30, 0), 2));

        // Agencement des composants dans le panneau
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Nom d'utilisateur:"), gbc);

        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Mot de passe:"), gbc);

        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        // Boutons ajoutés sous les champs de texte
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(addButton, gbc);

        gbc.gridx = 1;
        panel.add(updateButton, gbc);

        gbc.gridx = 2;
        panel.add(deleteButton, gbc);

        // Action des boutons
        addButton.addActionListener(e -> {
            try {
                boolean result = authService.createAccount(usernameField.getText(), new String(passwordField.getPassword()));
                JOptionPane.showMessageDialog(this, result ? "Compte ajouté avec succès !" : "Utilisateur déjà existant.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        updateButton.addActionListener(e -> {
            try {
                boolean result = authService.updateAccount(usernameField.getText(), new String(passwordField.getPassword()));
                JOptionPane.showMessageDialog(this, result ? "Mot de passe mis à jour !" : "Utilisateur introuvable.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        deleteButton.addActionListener(e -> {
            try {
                boolean result = authService.deleteAccount(usernameField.getText());
                JOptionPane.showMessageDialog(this, result ? "Compte supprimé !" : "Utilisateur introuvable.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Placer l'interface à l'écran
        setLocationRelativeTo(null); // Centrer la fenêtre
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthClient());
    }
}
