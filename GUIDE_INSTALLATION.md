# Nettoyage sûr V0.1 — depuis le téléphone

Ce dépôt contient le **code source**, pas encore un APK vérifié sur Android.

1. Ouvrir **Actions** sur ce dépôt GitHub.
2. Choisir **Compiler APK Android** et ouvrir la dernière exécution du workflow.
3. Si elle est verte (Success), ouvrir son détail puis télécharger **NettoyageSur-APK** en bas de la page (Artifacts).
4. Extraire l'archive ZIP reçue puis ouvrir **app-debug.apk**.
5. Autoriser ponctuellement « Installer des applications inconnues » pour le navigateur ou le gestionnaire de fichiers.
6. Installer, ouvrir « Nettoyage sûr » puis accepter l'autorisation Shizuku (Shizuku doit être démarré).
7. Appuyer sur « Nettoyer maintenant » pour la première analyse.

**Important** : le téléphone et les applications jugées essentielles ne doivent pas être arrêtés. Cependant l'arrêt forcé d'une application tierce peut couper ses notifications, alarmes, téléchargements ou synchronisations. Cette version est un prototype : effectuer un premier essai prudent.

L'application ne dispose pas de l'autorisation INTERNET. Elle ne contient pas de publicité, n'accède pas aux fichiers utilisateur et n'est pas destinée à être installée depuis le Play Store.
