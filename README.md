# Tropimon Chat Filter

By FastedCorsi

Mod client Fabric 1.21.1 qui organise le chat Tropimon sans modifier les messages reçus :

- **ALL** affiche le chat global et les messages système ;
- l'onglet **Ville** porte automatiquement le nom de la ville détectée dans le profil du joueur et n'affiche que son canal ;
- l'onglet **Staff** est réservé aux rôles Guide, Modérateur, Staff, Responsable et Admin, et isole entièrement `/chat STAFF` ;
- un **groupe temporaire** peut réunir jusqu'à huit joueurs équipés du mod ; chaque message est distribué à tous ses membres via les MP Tropimon ;
- chaque correspondant privé possède sa propre conversation, regroupant les messages reçus et les réponses envoyées.

Les conversations privées conservent leur compteur de messages non lus jusqu'à leur ouverture. Elles peuvent être fermées et parcourues à la molette. Lorsqu'un MP ou un message de ville arrive alors que le chat est fermé, seul le canal concerné est affiché temporairement ; après 60 secondes sans réponse, l'affichage revient automatiquement sur ALL.

Un clic gauche sur un pseudo conserve l'ouverture du profil Tropimon. Un clic droit prépare `@Pseudo ` dans le chat public ou `/msg Pseudo ` dans une conversation privée.

Le menu compact permet de masquer les messages système, sans grade, Champion, Super, Hyper ou Master. Il permet aussi d'afficher l'heure, d'ajouter la tête des joueurs et de choisir le délai de retour automatique sur ALL. Les réglages sont sauvegardés dans `config/tropimon-chat-filter.json`.

Le même menu crée et gère le groupe temporaire grâce à une fenêtre de sélection des joueurs connectés. Aucune commande de groupe n'est nécessaire. Les messages techniques sont masqués par le mod et les envois sont espacés afin de ne pas provoquer de ralentissement.

L'interface suit la hauteur du chat, adapte automatiquement la largeur du nom de ville et évite les animations provoquées par des messages filtrés. L'onglet Ville utilise `/chat TOWN`, Staff utilise `/chat STAFF`, ALL utilise `/chat GLOBAL` et une conversation privée envoie avec `/msg Pseudo`.

Les demandes de téléportation Tropimon affichent aussi une fenêtre compacte avec la tête du joueur et les actions fournies directement par le serveur. La fenêtre se déplace par son en-tête lorsque le curseur est libéré, mémorise sa position et n'en affiche qu'une à la fois ; les demandes suivantes restent dans une file bornée indiquée par un compteur.

## Construire

Depuis la racine du dépôt :

```powershell
.\gradlew.bat -p .\TropimonChatFilter build
```

Le JAR généré se trouve dans `TropimonChatFilter/build/libs`.

## Confidentialité et distribution

Chaque build prépare deux exemplaires identiques de la version courante dans
`build/delivery/<version>/share/` et `build/delivery/<version>/local/`.
Le premier est prêt à partager ; le second contient aussi `InstallWhenClosed.ps1` et
`ArmLocalUpdate.ps1`, deux outils externes absents du JAR public. Le build vérifie les deux JAR et
ces scripts. L'outil d'armement lance un unique processus masqué qui attend la fermeture de Minecraft,
installe, vérifie la copie puis s'arrête ; le launcher peut rester ouvert. L'ancien JAR est sauvegardé
hors des mods chargés. Ne jamais charger les deux exemplaires ensemble.

## Changement 0.1.38

Toute la surface visible du chat est désormais prioritaire sur les zones de clic et de
glisser-déposer de l'équipe Cobblemon. Un emplacement d'équipe masqué par les messages ne peut plus
ajouter une balise `<party:x>` ni bloquer un message cliquable. Le partage d'un Pokémon reste actif
sur les emplacements réellement accessibles au-dessus du chat.

## Changement 0.1.37

Les boutons et onglets du chat sont désormais prioritaires sur les zones de glisser-déposer de
l'équipe Cobblemon. Cliquer sur un onglet privé ne peut donc plus insérer accidentellement une
balise comme `<party:5>`, et il n'est plus nécessaire de masquer l'interface avec F1. Le clic et le
glisser-déposer des Pokémon restent disponibles en dehors des éléments interactifs du chat.

## Changement 0.1.36

Un clic sur l'un des six Pokémon du HUD insère désormais sa balise `<party:x>` à la position du
curseur dans le chat. Il est aussi possible de maintenir le clic et de déposer la balise sur le
champ de saisie ; une prévisualisation suit alors le curseur. Le calcul suit directement l'échelle
de l'interface et ne dépend d'aucune classe interne d'un autre mod Tropimon.

Le relais des balises résolues utilise maintenant un paquet de contrôle que les anciennes versions
consomment déjà silencieusement. Les membres à jour ne voient toujours qu'un message, et les membres
sur l'ancienne version ne reçoivent plus une seconde copie visible du Pokémon.

## Changement 0.1.35

Les références `<party:1>` à `<party:6>` sont maintenant résolues pour tous les membres du groupe.
Tropimon n'enrichissant que le retour reçu par l'auteur, le mod relaie ensuite le nom réellement
résolu aux autres membres. Les clients à jour n'affichent qu'un seul message ; si le retour serveur
n'arrive pas, le texte original réapparaît après cinq secondes afin de ne perdre aucun message.
Les messages de groupe ordinaires et les autres canaux ne changent pas.

## Changement 0.1.34

Les références `<party:1>` à `<party:6>` conservent désormais dans le groupe le composant enrichi
renvoyé par Tropimon, notamment ses informations de survol et interactions. Le premier accusé MP du
serveur fournit aussi ce rendu à l'auteur sans créer de doublon pour les autres membres. En cas
d'absence de réponse serveur, le texte réapparaît après cinq secondes. Les chats ALL, Ville, Staff
et MP continuent de transmettre la référence intacte au serveur.

## Changement 0.1.33

Le délai « Retour ALL » fonctionne désormais aussi après avoir cliqué sur un onglet privé. Envoyer
une réponse ne désactive plus le retour : cela redémarre le délai configuré. Tant que le chat est
ouvert avec `T`, aucun changement automatique n'a lieu ; dès sa fermeture, le passage vers ALL se
produit à l'expiration du délai.

## Changement 0.1.32

La fenêtre de création de groupe adapte désormais sa largeur, sa hauteur et le nombre de joueurs
visibles à la résolution et à l'échelle de l'interface. Elle reste entièrement dans l'écran. Son
ordre de rendu est placé après Chat Animation afin que les messages et annonces du HUD ne soient
plus dessinés par-dessus son champ de recherche ou sa liste.

## Changement 0.1.31

La pastille des MP masqués est désormais réellement collée à la bordure gauche et ne réserve plus
aucune largeur dans la rangée. Quand l'espace minimal le permet, les noms sont raccourcis pour garder
trois conversations visibles. Chaque cran de molette remplace un onglet par la conversation suivante
ou précédente tant qu'il en reste une à afficher.

## Changement 0.1.30

Correction du crash au démarrage provoqué par « Chat toujours visible » dans l'instance complète.
L'âge du message n'est plus recherché parmi les variables locales de `ChatHud`, que d'autres mods
peuvent réorganiser. Le mod intercepte directement la valeur d'horodatage concernée, ce qui conserve
le maintien permanent du chat et évite l'injection ambiguë.

## Changement 0.1.29

Le total des MP non lus masqués n'est plus présenté dans un bouton et n'est plus interactif. Il
utilise exactement la petite pastille rose des autres notifications d'onglets, fixée contre la
bordure gauche du chat. Les conversations continuent de défiler uniquement avec la molette.

## Changement 0.1.28

« Chat toujours visible » neutralise désormais directement l'âge utilisé par Minecraft avant le test
de disparition et le calcul d'opacité. Les lignes déjà reçues restent donc affichées sans attendre
un nouveau message. Cette interception ciblée reste compatible avec les animations et l'historique
étendu présents dans le launcher, sans considérer le chat comme ouvert ni afficher sa barre de défilement.

## Changement 0.1.27

Le compteur des conversations masquées affiche désormais uniquement le total des messages non lus
qu'elles contiennent. Il disparaît lorsque tous les MP hors écran sont lus ; les conversations lues
restent accessibles à la molette sans créer de notification. Un clic sur le compteur rejoint
directement une conversation masquée contenant un non-lu.

## Changement 0.1.26

L'option est renommée « Chat toujours visible » et empêche désormais réellement les messages du HUD
de s'effacer après quelques secondes pendant le jeu. La valeur choisie en 0.1.25 est migrée. Les
flèches des conversations sont retirées : un compteur compact indique à la place le nombre exact
d'onglets ouverts mais masqués par manque de place. Il clignote si au moins une de ces conversations
contient un non-lu et permet, au clic, de parcourir les pages d'onglets.

## Changement 0.1.25

Cette version introduisait des flèches pour les MP masqués et une première option de maintien du chat,
remplacées et corrigées dans la version suivante.

## Changement 0.1.24

Une option « Tous les chats dans ALL » est ajoutée au menu des filtres et reste désactivée par
défaut. Une fois activée, ALL réunit les messages globaux, ville, staff, groupe actif et privés.
Les filtres Système et grades continuent de s'appliquer aux messages globaux ; le routage des
réponses, les non-lus et les notifications des onglets spécialisés restent inchangés.

## Changement 0.1.23

Quand un MP non lu se trouve hors de la rangée visible, une flèche clignotante apparaît du côté de
l'onglet masqué. Un clic fait défiler directement la liste jusqu'à cette conversation sans la marquer
comme lue. Des emplacements fixes sont réservés aux flèches pour éviter tout chevauchement ou
décalage des onglets pendant le clignotement.

## Changement 0.1.22

Les commandes saisies manuellement avec `/` ne peuvent plus être annulées par un état Ville transitoire. Leurs réponses système reçues dans les cinq secondes restent visibles dans l'onglet courant, même hors de ALL ou avec le filtre Système désactivé, sans faire remonter les discussions des autres canaux. Le popup TP ne s'affiche et ne capte les clics dans aucun autre écran que le chat, afin de ne pas recouvrir les interfaces ouvertes par les outils de modération. Le rôle affiché reprend désormais la couleur exacte déjà fournie par le serveur pour le nom ou le préfixe du joueur ; cela respecte aussi les couleurs distinctes des types de Champions. Des couleurs Minecraft officielles servent uniquement de repli lorsque le serveur ne fournit aucun style.

## Changement 0.1.21

Lorsque le filtre Système est activé, il conserve explicitement les annonces officielles de captures ordinaires, shiny et légendaires, ainsi que les annonces, listes et expirations de boosts. Un joueur qui parle simplement de capture ou de boost dans son message reste classé selon son grade.

## Changement 0.1.20

Le popup TP affiche le rôle ou le grade public du joueur (Admin, Responsable, Modérateur, Guide, Staff, Master, Hyper, Super, Champion ou Sans grade) depuis les informations déjà reçues dans la liste des joueurs. Après un clic sur Accepter ou Refuser, le chat ouvert avec `T` se referme automatiquement ; les autres écrans restent ouverts.

## Changement 0.1.19

La tête du joueur dans le popup TP agrandit désormais uniquement les zones 8×8 du visage et de sa seconde couche. Les autres parties de la skin ne sont plus prélevées dans l'icône.

## Changement 0.1.18

Le popup TP apparaît désormais au centre de l'écran par défaut et utilise une copie autonome du cadre visuel de Catch Preview. La variante serveur « demande à se téléporter vers toi » est reconnue en plus des demandes qui envoient le joueur vers leur auteur. Chaque demande rapprochée est conservée séparément dans la file ; aucune fusion par pseudo n'altère plus le compteur.

## Changement 0.1.17

Ajout du popup autonome de demandes de téléportation : actions Accepter/Refuser extraites des composants cliquables du serveur, tête du joueur, déplacement et position persistante. Jusqu'à huit demandes sont conservées pendant leur délai serveur et présentées l'une après l'autre, sans superposition ni analyse répétée du même message. Les messages serveur d'origine restent présents dans le chat.

## Changement 0.1.16

Le sélecteur d'emojis et son bouton ont été entièrement retirés après le refus de ces caractères par
le chat Tropimon. Aucun remplacement texte ou conversion automatique n'est appliqué. La largeur native
de la saisie est rétablie ; le classement, les canaux, MP, groupes, notifications et filtres sont inchangés.

## Contrôles de confidentialité

La règle permanente se trouve dans `AGENTS.md`. Le build contrôle les fichiers publiables et les
JAR générés (métadonnées, constantes des classes, ressources et archives imbriquées). Une détection
fait échouer la vérification ; ses valeurs ne sont jamais recopiées dans le rapport.
Le contrôleur s'exécute uniquement pendant le build et n'est pas embarqué dans le mod.

```powershell
..\gradlew.bat verifyPrivacySources
..\gradlew.bat test build
```

`verifyPrivacyArtifacts` vérifie les JAR de production, de sources et intermédiaires de la version
courante. Il est aussi lancé à la fin de `remapJar` et `remapSourcesJar`, et avant une installation
explicitement demandée. Ces contrôles ne publient rien et n'installent rien avec `build`.

Les identités de la machine et des métadonnées Git locales servent de critères temporaires,
sans liste personnelle versionnée. Pour un contrôle CI ou des termes privés supplémentaires
(nom civil, employeur), `CHAT_FILTER_PRIVACY_TERMS_FILE` peut désigner un fichier UTF-8 extérieur
au dépôt, fourni confidentiellement, avec un terme par ligne. Aucun fichier réel n'est fourni
en exemple. Sans ce contexte, les identités inconnues ne sont pas toutes détectables automatiquement.

Les chemins personnels, e-mails à examiner et formats usuels de secrets sont également contrôlés.
Les URL publiques fonctionnelles et les crédits tiers restent préservés ; un nouveau crédit ou
un faux positif doit être examiné explicitement, pas masqué par une exclusion générale.
Les fixtures du contrôleur sont fictives. Le contrôle n'est pas une garantie d'anonymisation absolue
ni un détecteur exhaustif de secrets obfusqués ou encodés arbitrairement.

Ne partager que le JAR courant après vérification. `releases/`, `build/` (en tant que dossier complet),
`run/`, configurations, logs, captures et sauvegardes locales restent privés et sont exclus des
exports de sources. Leurs fichiers originaux sont conservés. Les anciens commits, anciennes
releases et copies déjà diffusées ne sont pas nettoyés par la création d'un nouveau JAR.

## Détection de ville (0.1.13)

Le profil reçu du serveur fournit directement `townInfo.cityId` et `townInfo.cityName`.
Le nom est appliqué dès réception, même chat fermé ; si seul l'identifiant est disponible,
le nom est retrouvé dans notre index des villes par cet identifiant, sans supposer l'absence de ville.
Les messages et l'historique ne peuvent plus écraser une appartenance confirmée par le profil.
Une liste régionale incomplète ne signifie pas « aucune ville ».

Le suivi repose sur les changements du profil reçu, sans délai de scrutation de dix secondes,
sans requête supplémentaire au serveur et sans dépendance au cache interne d'un autre mod.
L'observation réseau se fait avant consommation du tampon par les codecs, puis n'est publiée
qu'après décodage réussi du paquet correspondant ; le paquet du serveur reste inchangé.

Compilation uniquement pour cette révision : aucun test local lancé à la demande de l'utilisateur.
La validation sur le serveur Tropimon reste à effectuer avec ce JAR chargé.

## Optimisations et autonomie (0.1.11)

- Les faits d'un message sont analysés une fois puis partagés entre filtrage, notifications,
  groupes, aperçus et décoration. Le cache est limité à 4 096 objets texte originaux.
  La visibilité est toujours recalculée avec le canal, le correspondant, le groupe et les filtres actuels.
- Les dispositions des MP et les libellés sont réutilisés tant que leur état ne change pas.
  La hauteur et la position du chat restent calculées à chaque image. Les changements de largeur,
  de langue, de police, de liste, de sélection et de session invalident les caches concernés.
- Le formatage de l'heure et la mesure de son retrait ne sont plus répétés par ligne et par image.
  Les originaux et leurs événements clic/survol sont conservés. Les téléchargements de skins restent
  asynchrones, avec les mêmes caches, délais de nouvel essai et limite de quatre téléchargements.
- Aucun accès aux classes, champs, configurations ou services internes d'un autre mod Tropimon.
  Le nom de ville, les UUID des habitants et les rôles staff ont leurs propres modèles et index.
  Un observateur passif décode les données S2C déjà reçues, sans émettre, annuler, remplacer un paquet
  ni enregistrer les identifiants de paquets d'un autre mod. Le décodeur Zstandard est inclus et
  relocalisé dans le package privé du mod, avec sa licence : aucune bibliothèque commune à installer.
- La suppression précoce des seules confirmations `/chat`, la conservation des messages filtrés
  sans animation, les marqueurs de groupe et la compatibilité avec l'historique étendu sont conservés.

Le protocole de profil est décrit dans `VERIFICATION-0.1.11.md`. Si ses données sont absentes ou
invalides, les solutions de repli existantes (chat, liste des joueurs, cache de ville propre au mod)
restent utilisées ; aucun rôle staff n'est déduit d'un paquet illisible.

### Tests sans toucher au launcher

Depuis ce module, avec `JAVA_HOME` configuré sur Java 21. Les lancements Minecraft optionnels
nécessitent une autorisation distincte ; la commande de build ne les lance pas :

```powershell
..\gradlew.bat test build
..\gradlew.bat runSmoke -PsmokeJava="${env:JAVA_HOME}/bin/java.exe"
..\gradlew.bat runProductionSmoke
```

`runSmoke` crée un monde local jetable dans `build/smoke-run`, exerce le HUD et les mixins,
puis ferme cette instance. Il ne rejoint aucun serveur public. Le code de test n'est pas inclus dans le JAR.

`runProductionSmoke` vérifie le vrai JAR remappé, avec le décodeur privé inclus, dans
`build/production-smoke-isolated`. Pour tester aussi les autres mods Tropimon et leurs dépendances, ajouter
`-PcoexistModsDir="$env:TROPIMON_MODS_DIR" -Pfabric_loader_version=0.17.3 -Pfabric_api_version=0.116.6+1.21.1`,
où `TROPIMON_MODS_DIR` désigne le dossier local des mods, sans enregistrer son chemin dans le dépôt.
Ce paramètre charge leurs JAR en lecture seule dans `build/production-smoke-coexist`, jamais comme
dépendances de compilation ou ressources du JAR distribué. Le JAR Chat Filter déjà installé est exclu
de ce chargement. Les configurations de test restent dans ces répertoires séparés.

Pour le copier directement dans l'instance Tropimon locale, après avoir fermé le jeu :

```powershell
.\gradlew.bat -p .\TropimonChatFilter installTropimonChatFilterLocal
```


## Mises à jour avec consentement

Aucun téléchargement de mise à jour sans accord. Le premier écran propose uniquement d'autoriser la consultation des métadonnées GitHub (au démarrage, au plus toutes les six heures). Une seconde confirmation montre la version et demande explicitement le téléchargement du JAR et de son SHA-256. L'ancien réglage `enabled: true` ne donne aucune autorisation.

Après accord et vérification, un installateur local utilise le Java de Minecraft, attend la fermeture du jeu, sauvegarde l'ancien JAR hors des mods chargés et remplace uniquement ce mod. Aucun autre mod Tropimon ni changement de launcher n'est requis. Le dossier `mods` classique et le stockage géré Tropimon reconnu sont pris en charge ; une disposition inconnue, un fichier modifié/verrouillé ou une incompatibilité bloque l'installation sans forcer. Le nom du JAR installé est conservé pour rester enregistré par le launcher ; la version réelle se lit dans les métadonnées Fabric.

Pour modifier le choix en jeu : `/tropimonupdates tropimon_chat_filter`. Refuser laisse le mod utilisable. Les anciennes versions dont l'updater est défectueux nécessitent un premier remplacement manuel, jeu fermé. L'accord donné pour ce mod ne s'applique pas aux autres mods. Les tests automatisés sont exécutés sous Windows ; les autres systèmes doivent encore être validés en situation réelle.

Les versions à consentement utilisent un canal de releases distinct du lien GitHub « latest » historique : sélectionner la version par son tag. Cela évite de déclencher les anciens updaters sans accord.
