# Confidentialité permanente — Tropimon Chat Filter

Ces règles complètent les consignes du dépôt et s'appliquent à toutes les futures versions,
corrections, optimisations et fonctionnalités de ce mod, y compris après extraction dans un dépôt autonome.

- Attribution publique du développeur : exactement **By FastedCorsi** ; dans `fabric.mod.json`,
  utiliser `"authors": ["By FastedCorsi"]`. Préserver les crédits et licences tiers.
- Ne jamais ajouter d'identité civile/professionnelle, employeur, compte système, chemin personnel,
  e-mail privé ou secret dans les sources, ressources, scripts, tests, documentation ou livrables.
  Employer des chemins portables et des fixtures fictives. Ne pas recopier les valeurs retirées
  dans les règles, rapports, commentaires, commits ou listes de détection versionnées.
- Préserver les identifiants techniques, pseudos de joueurs fonctionnels, URL publiques nécessaires,
  fonctionnalités et indépendance du mod. Aucun accès au code, état ou configuration interne d'un
  autre mod Tropimon ; toute protection nécessaire doit conserver un équivalent autonome.
- Avant livraison, exécuter le contrôle de confidentialité des sources et des JAR finaux,
  y compris constantes compilées, ressources et archives imbriquées. Corriger ou examiner explicitement
  chaque détection. Le contrôle de build ne doit pas être contourné ; utiliser des tests fictifs.
- Ne pas distribuer configurations personnelles, captures personnelles, logs, sauvegardes, fichiers
  `.env`, dossiers `.git`, anciennes releases non vérifiées ou environnements de test.
  Préserver leurs originaux locaux ; ne pas lancer `clean` pour les faire disparaître.
- Ne jamais tester ni révoquer automatiquement un secret : masquer sa valeur et signaler l'emplacement
  et les actions nécessaires. L'obfuscation n'est pas une protection.
- Aucun commit, push, publication, réécriture d'historique ou remplacement dans le launcher sans
  autorisation correspondante. Avant un commit autorisé, vérifier auteur et committer effectifs :
  **FastedCorsi** et une adresse GitHub noreply valide déjà vérifiée, jamais inventée ; ne pas modifier
  la configuration Git globale. Signaler séparément les traces historiques et copies déjà diffusées.
- Les jeux de tests locaux Minecraft ne doivent pas être lancés sans nouvelle autorisation.
  La compilation et les tests unitaires ne constituent pas une validation sur le serveur Tropimon.
- Team Hunt et Bid Maker restent hors périmètre tant que l'utilisateur ne demande pas leur reprise.

## Distribution via Tropimon Compagnion

- Chat Filter reste développé et validé dans ce dépôt, mais ses nouvelles évolutions sont distribuées uniquement dans le JAR unique du nouveau Tropimon Compagnion.
- Ne pas publier ni installer automatiquement un nouveau JAR individuel Chat Filter. Fournir à la tâche Compagnion une révision source commitée et validée ainsi que les preuves utiles.
- Les artefacts autonomes peuvent être produits pour les contrôles internes. Ils ne constituent pas une livraison joueur et ne doivent pas être chargés en parallèle de la fonctionnalité intégrée.
- Les outils privés de livraison locale restent séparés dans `tools/` et hors du JAR. Ne pas les armer ou les exécuter sans une autorisation distincte.
- Préserver les anciens dépôts, tags, releases, configurations et sauvegardes ; cette transition ne les efface pas et ne modifie pas rétroactivement les JAR déjà diffusés.

## Code simple, lisible et efficace

- Préserver strictement la logique, les fonctionnalités et les protections. Chercher les gains utiles de performance, mémoire et poids sans rendre le code difficile à comprendre.
- Choisir la solution la plus simple qui répond au besoin actuel. Éviter les classes, interfaces, factories, couches de services, méthodes relais et dépendances ajoutées sans utilité concrète ; ne pas bâtir un framework pour un cas isolé.
- Garder des classes cohérentes et des méthodes lisibles quand leur séparation aide réellement. Ne pas tout fusionner dans une classe géante ni compacter le code : moins de fichiers ou de lignes ne garantit pas de meilleures performances.
- Réutiliser ce qui existe dans le mod ; supprimer le code mort seulement après vérification des usages, y compris mixins, réflexion, événements, ressources et compatibilité. Pas de réécriture générale pour une optimisation locale.
- Cibler les coûts identifiés : travail répété par tick ou par frame, scans, allocations, entrées/sorties et caches sans limite. Justifier les gains et vérifier les comportements concernés ; ne pas ajouter de cache, de thread ou d'abstraction préventive sans besoin démontré.
- Chaque mod reste autonome : aucune dépendance aux classes, états ou services internes de nos autres mods. Recréer dans le mod concerné la petite implémentation nécessaire plutôt qu'imposer une bibliothèque commune ; préserver les dépendances officielles nécessaires.

## Absence d'auto-updater intégré

- Le code maintenu de Chat Filter ne contient aucun contrôle de mise à jour réseau, téléchargement ou remplacement de JAR, commande ou popup d'installation, helper joueur ni tâche planifiée associée.
- Ne pas recréer un vérificateur Modrinth autonome dans ce module destiné à la fusion. Tropimon Compagnion possède seul la notification de version de l'ensemble et ouvre uniquement la page HTTPS officielle.
- Les anciennes configurations de consentement et les fichiers préparés restent des données personnelles du joueur : ne pas les supprimer ou les migrer aveuglément.
- Une archive GitHub éventuelle ne doit pas recevoir le marqueur consommé par les anciens updaters. Ne pas déplacer volontairement le canal historique pour les déclencher.
- La suppression actuelle ne retire pas l'ancien comportement des JAR déjà distribués ; une première migration manuelle via le canal officiel peut rester nécessaire.

## Lisibilité aux quatre échelles GUI

- Gérer les échelles Minecraft 1, 2, 3 et 4 sans modifier le réglage global du joueur. Vérifier aussi les fenêtres réduites et le redimensionnement ; distinguer l'échelle demandée de celle réellement appliquée par Minecraft.
- Conserver des textes, valeurs, contrôles et infobulles lisibles. Adapter l'agencement et le défilement à l'espace disponible ; ne pas masquer une valeur essentielle ou remplacer sa lecture par une police minuscule.
- Rendu, clics, survol, glisser-déposer et découpe utilisent la même transformation. Contrôler les interactions et protections existantes, pas seulement une capture à l'échelle 2. Les mods restent indépendants, avec une implémentation locale simple.
