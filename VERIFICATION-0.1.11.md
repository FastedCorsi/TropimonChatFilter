# Vérification 0.1.11 — optimisation sans changement de routage

## Périmètre

Le routage des envois, les commandes, le protocole de groupes, les délais et le calcul des non-lus
ne changent pas. Les anciens tests sont conservés. Aucun JAR installé n'est remplacé et aucun push
n'est effectué. Les modifications préexistantes du dépôt ne font pas partie de ce nettoyage.

## Caches

| Donnée | Limite | Invalidation |
| --- | --- | --- |
| Analyse stable, clé par identité du Text original | 4 096 messages | Connexion, compte, langue ; nettoyage également au tick de déconnexion |
| Analyse MP auxiliaire existante | 512 entrées | Même invalidation que l'analyse stable |
| Disposition de conversations | Une disposition, au plus 64 conversations | Liste immuable, sélection/non-lus, largeur, défilement, police, session |
| Libellés et mesures de rendu | Quelques champs par écran, une mesure d'horodatage | Valeur affichée, rechargement des ressources, police Unicode ; écran recréé |
| Présentation mise en cache | 4 096 présentations | Options, session/langue ; origine et date conservées séparément pour l'historique retenu |
| Profil autonome | 4 096 villes / 65 536 habitants | Déconnexion, nouvelles données, suppression et changement de région |

Les objets `Text` reçus et conservés dans l'historique sont traités comme des messages stables,
à l'image du HUD Minecraft. Les préfixes ne constituent pas une deuxième clé d'analyse.
Ni la visibilité, ni le groupe actif, ni les non-lus ne sont stockés dans le cache d'analyse.
Les caches de textures asynchrones existants ne sont pas remplacés ni désactivés.

La garde précoce des confirmations de changement de canal reste volontairement indépendante :
elle peut intervenir sur le thread réseau, avant les recopies par d'autres mods. Elle n'accède pas
au cache réservé au thread client. Le HUD partage ensuite son analyse complète entre ses consommateurs.

Les tests vérifient 10 000 consultations identiques sans nouvelle lecture/analyse du texte et
10 000 dispositions identiques sans remesure des noms. Ce sont des vérifications de travail évité,
pas une promesse chiffrée de FPS.

## Autonomie du profil

Les anciennes lectures par réflexion de `TownManager` et du profil client ont été remplacées
par un décodeur et un état appartenant exclusivement à Chat Filter. Schéma vérifié sur le protocole
des fichiers officiels Tropimon présents localement (client 0.15.5.2) :

| Identifiant reçu | Données utiles |
| --- | --- |
| `tropimon:set_current_server_packet` | Chaîne JSON, nom de région |
| `tropimon:update_towns` | Collection de villes compressée, mise à jour incrémentale dans la même région |
| `tropimon:update_town_packet` | UUID, nom, région optionnelle, recrutement, chunks, habitants |
| `tropimon:delete_town_packet` | UUID de ville |
| `tropimon:update_player_data_packet` | Profil compressé : UUID et `rankManager.playerRanks` |

Une ville conserve ici seulement son UUID, son nom et la correspondance UUID/pseudo de ses habitants.
Les rôles reconnus restent GUIDE, MODERATOR, STAFF, RESPONSABLE et ADMIN.
La compression est un tableau d'octets préfixé VarInt, contenant la taille décompressée sur quatre
octets puis un flux Zstandard. Limites : 1 Mio compressé, 16 Mio décompressé ; les tailles sont
validées avant allocation. Un paquet non reconnu ou invalide n'écrase pas le dernier état valide.

L'observation intervient après décodage vanilla, sur une vue indépendante du buffer. Elle ne
modifie ni ses indices ni son compteur de références et ne prend possession d'aucun codec.
La publication vérifie encore l'identité de connexion sur le thread client. Les réponses d'une
ancienne connexion sont ignorées. L'UUID d'une tête distante est capturé avant de lancer son
téléchargement asynchrone, sans lire l'état du profil depuis ce travailleur.

Différence volontaire liée à l'autonomie : si le serveur change ce schéma ou dépasse les limites,
le mod conserve les replis existants au lieu de consulter un autre mod. La détection immédiate du
profil ne peut alors pas être garantie. Aucune protection inter-mods n'a été supprimée.

## Nettoyage confirmé

- Le calcul d'auteur `ChatPlayerName` n'a plus d'appelant dans la production. Il reste uniquement
  en source de test comme oracle de comparaison de l'ancien comportement.
- Les anciennes méthodes de filtrage privé/groupe redondantes ont été supprimées après vérification
  des usages, mixins et ressources. Le filtre partagé utilise les mêmes prédicats.
- Le détecteur d'auteur de groupe a été déplacé dans le protocole propre au mod.
- Aucun mixin ni ressource d'interface n'a été supprimé.

## Tests

Les tests couvrent classification, pseudo/auteur, filtres actuels, routage sortant, confirmations
de canal uniquement, protocoles et présence des groupes, cache/session, disposition, décodage du
profil, absence de ville, rangs, mises à jour incrémentales et buffers partagés avec un second lecteur.
Un test d'architecture interdit les références aux autres packages Tropimon et contrôle les
dépendances déclarées, les mixins et les points d'entrée.

Le test Minecraft `runSmoke` utilise Java 21, un monde plat local et une fenêtre de chat réelle.
Il exerce l'historique du HUD, les MP reçus/réponses, le masquage sans ligne invisible, les événements
clic/survol, les non-lus, les aperçus et les groupes. Les commandes de canal n'existent pas sur ce
serveur vanilla local : leurs erreurs ne servent pas de preuve du routage sur le serveur Tropimon.
Le routage et les commandes sont vérifiés par les tests de non-régression dédiés.

Le chargement en développement a rencontré des conflits de remappage de dépendances tierces.
La coexistence est donc testée avec `runProductionSmoke`, en utilisant directement les JAR distribués,
sans remappage de ces dépendances ni copie dans le launcher. Elle inclut les mods Tropimon,
leurs dépendances requises, MoreChatHistory et ChatAnimation. Les essais ne remplacent pas un test
de conversation entre plusieurs vrais joueurs sur le serveur Tropimon.

## Résultats du 30 août 2026

- `test build` : 42 tests, zéro échec, zéro test ignoré, Java 21.
- JAR de production seul avec Minecraft 1.21.1, Fabric Loader 0.16.9 et Fabric API 0.110.0 :
  monde local chargé, toutes les assertions du HUD validées, rendu réel du chat puis arrêt normal.
- JAR de production avec les autres mods : mêmes assertions validées, arrêt normal avec Loader
  0.17.3 / Fabric API 0.116.6. Ensemble testé : Bid Maker 0.23.2, Catch Preview 0.6.0,
  Damage Calc 0.3.34, Stocks Manager 0.1.0, Team Builder 0.59.3, Team Hunt 0.1.0,
  UI Battle 0.1.0, TropimonBuild, Tropicosmetics, client officiel 0.15.5.2 et dépendances,
  MoreChatHistory 1.3.1 et ChatAnimation 1.0.6 (113 entrées Fabric, bibliothèques incluses).
- Les deux essais de production exercent le décodeur Zstandard privé avec une trame connue,
  les préfixes heure/tête activés et désactivés, la tête uniquement sur la première ligne,
  la réutilisation de présentation et les événements clic/survol.
- JAR contrôlé : version 0.1.11, six mixins présents, aucune classe d'un autre mod Tropimon,
  aucune classe de test, décodeur relocalisé et licence présents. Seules dépendances obligatoires
  déclarées : Minecraft, Fabric Loader, Fabric API et Java.
- Le pack émet des avertissements de ressources/recettes absentes dans cet environnement sans
  les ressources du serveur. Ils n'ont pas empêché les assertions de Chat Filter de passer.
- Aucun paquet n'a été envoyé au serveur public Tropimon. La détection de profil est vérifiée
  sur des trames reproduisant son protocole, pas sur une nouvelle connexion à ce serveur.
- JAR installé inchangé, SHA-256 avant/après :
  `DAB4F9BA5133E92E677AC638C18A19EDB21110AC2F07FB095BDA2AFA230AE71A`.
  Aucune installation et aucun push.

Traces locales : `build/verification/isolated-production.log`,
`build/verification/coexistence-production.log`, `build/reports/tests/test/index.html`.
