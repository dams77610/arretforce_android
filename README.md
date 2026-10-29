# arretforce_android / Nettoyage sûr V0.1

Application Android expérimentale **sans root**, avec **Shizuku 13.5**.

- Un bouton : « Nettoyer maintenant ».
- Analyse locale des processus via service Shizuku, sous identité shell.
- Préservation des composants système, applications constructeur et de nombreuses applications sensibles par règles conservatrices.
- Commande d'arrêt forcé ciblée uniquement sur des packages tiers filtrés.
- Second relevé pour distinguer les arrêts confirmés des incertains.
- Pas d'accès réseau, pas de nettoyage automatique et pas d'accès aux fichiers personnels.

**Avertissement** : un processus présent en RAM n'est pas nécessairement nuisible. Forcer l'arrêt d'une application peut interrompre ses notifications et ses tâches jusqu'à son redémarrage. Aucun gain de performance n'est garanti. Les règles de protection ne garantissent pas l'absence totale d'effets secondaires. Prototype à tester sur l'appareil.

## Compiler et installer

Voir [GUIDE_INSTALLATION.md](GUIDE_INSTALLATION.md).

Compilation GitHub Actions : `gradle :app:assembleDebug`, Java 17, Android Gradle Plugin 8.7.3, Android SDK 35, Gradle 8.9.

## État

Sources ajoutées depuis le projet Nettoyage Android V0.1 et légèrement durcies. Compilation et compatibilité matérielle à confirmer par le statut du workflow et les essais Vivo Android 16.
