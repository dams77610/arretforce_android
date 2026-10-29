# Nettoyage sûr V0.3.2

Prototype Android 16 utilisant Shizuku, sans root et sans ordinateur.

## Classification des applications

La précédente version verrouillait parfois trop largement les applications téléchargées, en se basant sur le nom de leur paquet ou sur la simple présence de services sensibles.

La V0.3.2 distingue désormais :

- **Système : arrêt interdit**. Déterminé par les informations officielles d'Android (FLAG_SYSTEM / FLAG_UPDATED_SYSTEM_APP), pas par une ressemblance de nom. Le fait d'avoir installé une mise à jour du Play Store ne transforme pas une application préinstallée en application tierce.
- **Fonction active essentielle : arrêt interdit**. Par exemple l'application actuellement utilisée comme écran d'accueil, téléphone ou SMS par défaut, clavier actif, administrateur d'appareil actif ou service Shizuku. Ces applications peuvent avoir été installées par l'utilisateur.
- **Protection conseillée : choix possible**. VPN, application proposant un clavier non utilisé, service d'accessibilité, accès aux notifications ou permissions d'alarme. L'utilisateur doit confirmer le choix.
- **Application ordinaire : choix possible**. Case à cocher et arrêt groupé.

Appuie sur une application verrouillée pour consulter la raison exacte, le nom du paquet et l'origine Android système/tierce.

## Limitations

La présence d'un processus dans la mémoire n'implique pas une consommation inutile. Les commandes d'arrêt forcé peuvent interrompre des notifications, alarmes, téléchargements et synchronisations. Même avec les protections, le risque d'effets secondaires sur une application tierce ne peut être éliminé.

Lorsqu'Android ne permet pas de détecter l'activité des applications, l'inventaire des applications installées reste disponible, et un arrêt peut être demandé explicitement via Shizuku. L'activité d'une application reste alors **inconnue** : pas de promesse de « nettoyage » réel.

## Installation depuis le téléphone

Dans l'onglet Actions de ce dépôt, ouvrir la dernière compilation verte de « Compiler APK Android », puis télécharger l'artefact NettoyageSur-APK. Extraire app-debug.apk et installer. Shizuku doit être démarré et autorisé.
