# Nettoyage sûr V0.3

## Installer sur Android 16

1. Ouvrir **Shizuku**, vérifier **en cours de fonctionnement**.
2. Ouvrir **Actions → Compiler APK Android** sur ce dépôt GitHub.
3. Choisir la dernière exécution **verte**, télécharger **NettoyageSur-APK** dans **Artifacts**.
4. Extraire le ZIP et installer `app-debug.apk`. Si Android refuse une mise à jour pour signature différente, désinstaller la précédente version puis réinstaller ; aucune liste sauvegardée n'est conservée.
5. Ouvrir Nettoyage sûr, accepter la demande d'autorisation Shizuku et appuyer sur **Actualiser**.

## Utilisation

- **En cours** : uniquement les processus détectés (peut être vide si Android limite l'inventaire).
- **Toutes** : applications installées ; sélection manuelle possible même si l'activité ne peut pas être identifiée, **à condition que Shizuku soit connecté**.
- **Protégées** : deux protections distinctes :
  - **Système / fonction essentielle** : verrou dur, aucune sélection possible.
  - **Protection conseillée** : application tierce sélectionnable après avertissement explicite.
- Les applications ne sont jamais présélectionnées. Le bouton d'arrêt agit uniquement sur la sélection, avec une nouvelle validation de sécurité.

**Attention :** forcer l'arrêt peut interrompre les notifications, synchronisations ou réveils d'une application. Un processus conservé en mémoire ne prouve pas une consommation excessive de ressources. Prototype à tester avec prudence.
