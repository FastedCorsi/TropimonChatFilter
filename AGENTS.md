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

## Deux livraisons JAR à chaque version

- À chaque livraison d'une version ou d'un changement de code, fournir deux JAR clairement séparés : un JAR local accompagné du système de mise à jour différée de l'instance du launcher, et un JAR prêt à partager. Utiliser deux dossiers ou noms explicites ; ne jamais installer les deux exemplaires simultanément.
- Les deux JAR proviennent de la même version validée et offrent les mêmes fonctionnalités. Ils peuvent être identiques octet pour octet : privilégier un petit script externe pour l'installation locale, sans dupliquer le code du mod ni embarquer ce mécanisme dans le JAR public.
- Le launcher peut rester ouvert : seul le jeu Minecraft concerné doit être arrêté avant la mise à jour. La fermeture du launcher ne prouve pas l'arrêt du jeu. Ne jamais forcer leur arrêt, toucher aux autres mods ni remplacer un fichier utilisé ou verrouillé.
- Cette demande constitue l'autorisation permanente de préparer et d'armer cette installation différée lors d'une livraison, sauf consigne explicite contraire pour la tâche. Une demande de conseil, d'audit ou de mise à jour des règles ne déclenche ni compilation ni installation.
- Réutiliser et adapter les outils locaux existants. Vérifier la cible exacte, l'intégrité du JAR et le résultat de la copie ; conserver une sauvegarde de l'ancien JAR hors du dossier des mods chargés. En cas de cible ambiguë, d'accès impossible ou de verrouillage, conserver le fichier préparé et signaler le blocage sans forcer.
- Le JAR partageable ne contient ni chemin personnel, configuration locale, secret, donnée privée ni outil d'installation spécifique à la machine. Appliquer les contrôles de confidentialité aux deux JAR et aux éventuels fichiers qui les accompagnent. Conserver l'attribution « By FastedCorsi » et les crédits tiers.
- Dans la livraison, indiquer les deux JAR et leur version, les contrôles effectués et l'état réel de l'installation locale : préparée, en attente de fermeture ou installée après vérification. Ne pas annoncer une installation réussie parce qu'un script a seulement été lancé.

## Code simple, lisible et efficace

- Préserver strictement la logique, les fonctionnalités et les protections. Chercher les gains utiles de performance, mémoire et poids sans rendre le code difficile à comprendre.
- Choisir la solution la plus simple qui répond au besoin actuel. Éviter les classes, interfaces, factories, couches de services, méthodes relais et dépendances ajoutées sans utilité concrète ; ne pas bâtir un framework pour un cas isolé.
- Garder des classes cohérentes et des méthodes lisibles quand leur séparation aide réellement. Ne pas tout fusionner dans une classe géante ni compacter le code : moins de fichiers ou de lignes ne garantit pas de meilleures performances.
- Réutiliser ce qui existe dans le mod ; supprimer le code mort seulement après vérification des usages, y compris mixins, réflexion, événements, ressources et compatibilité. Pas de réécriture générale pour une optimisation locale.
- Cibler les coûts identifiés : travail répété par tick ou par frame, scans, allocations, entrées/sorties et caches sans limite. Justifier les gains et vérifier les comportements concernés ; ne pas ajouter de cache, de thread ou d'abstraction préventive sans besoin démontré.
- Chaque mod reste autonome : aucune dépendance aux classes, états ou services internes de nos autres mods. Recréer dans le mod concerné la petite implémentation nécessaire plutôt qu'imposer une bibliothèque commune ; préserver les dépendances officielles nécessaires.

## Publication et mise à jour autonome

- Chaque version livrée est poussée sur le dépôt GitHub public propre à ce mod, puis publiée dans une Release dont le tag correspond exactement à la version.
- La Release contient un seul JAR partageable vérifié et son fichier SHA-256. Les JAR LOCAL, configurations et scripts propres à une machine ne sont jamais publiés.
- Ce mod embarque sa propre implémentation de mise à jour. Elle ne dépend d'aucune classe, bibliothèque ou service interne d'un autre mod Tropimon.
- La mise à jour accepte uniquement la Release officielle de ce dépôt, exige le SHA-256, vérifie l'identifiant et la version de fabric.mod.json, prépare le fichier hors du dossier mods, puis remplace l'ancien JAR seulement après l'arrêt de Minecraft. Elle ne force jamais l'arrêt du jeu ou du launcher et conserve une sauvegarde hors des mods chargés.
- Une évolution de l'updater doit rester légère, asynchrone et sans travail répété par tick ou par frame.

