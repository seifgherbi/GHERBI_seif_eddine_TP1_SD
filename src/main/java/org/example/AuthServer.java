package org.example;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthServer {
    public static void main(String[] args) {
        try {
            // Crée une instance normale de AuthServiceImpl
            AuthServiceImpl authService = new AuthServiceImpl();

            // Démarre le registre RMI sur le port 1099
            Registry registry = LocateRegistry.createRegistry(1099);

            // Enregistre le service sous le nom "AuthService"
            registry.rebind("AuthService", authService);

            System.out.println("Serveur d'authentification RMI démarré.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
