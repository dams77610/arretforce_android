# Nettoyage sûr V0.2 — Android / Shizuku

Application Android personnelle, **sans root et sans ordinateur**, pour inventorier les applications et sélectionner manuellement celles à forcer à s'arrêter.

## Nouveautés de la V0.2

- Liste visible et consultable avec noms et icônes des applications.
- Filtres **En cours**, **Toutes** et **Protégées** ; recherche.
- Cases à cocher et **arrêt groupé uniquement sur demande**.
- Les applications système et essentielles sont visibles mais non sélectionnables.
- Les applications sans processus détecté ne sont pas sélectionnables.
- Réanalyse immédiatement avant la commande d'arrêt.
- Aucune sélection pré-cochée, aucune exécution en arrière-plan.

## Limites

La présence d'un processus n'implique pas une consommation excessive de batterie ou de RAM.
L'arrêt forcé peut interrompre notifications, synchronisations ou autres tâches d'une application jusqu'à sa réouverture.
La liste de protection est conservatrice mais ne garantit pas l'absence totale d'effets secondaires.
Si Shizuku n'est pas disponible, la liste des applications reste consultable, mais aucun arrêt n'est possible.

## Installer l'APK

Ouvrir **Actions → Compiler APK Android** et télécharger l'artefact **NettoyageSur-APK** de la dernière compilation réussie. Décompresser puis installer `app-debug.apk` sur le téléphone. L'application utilise Shizuku 13.5 préalablement démarré.

Version de test à valider sur Vivo Android 16.
