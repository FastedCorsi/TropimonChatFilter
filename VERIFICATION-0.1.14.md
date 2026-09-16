# Confidentialité — version 0.1.14

By FastedCorsi

## Changements

- Attribution du mod : `"authors": ["By FastedCorsi"]` dans `fabric.mod.json`.
- Mentions personnelles remplacées par des données fictives dans les tests concernés.
- Exemples de chemins portables, consignes permanentes dans `AGENTS.md` et exclusions de
  publication pour les fichiers personnels, environnements locaux et anciennes releases.
- Contrôleur de confidentialité séparé du code du client, exécuté pendant le build.
  Les critères privés dérivés du contexte local restent uniquement en mémoire ; aucun nom,
  e-mail ou employeur réel n'est enregistré dans une liste versionnée ou dans ses fixtures.

## Vérifications

Depuis le dossier du module, avec Java 21 :

```powershell
..\gradlew.bat verifyPrivacySources test build --rerun-tasks --console=plain
```

- 61 tests unitaires réussis, aucun échec ni test ignoré : 49 tests fonctionnels existants
  (classification, routage, MP, groupes, caches, profils et interactions) et 12 tests du contrôleur.
- Contrôle des sources publiables et des quatre JAR courants : production, sources, développement
  et dépendance privée relocalisée. Les constantes compilées, ressources et archives imbriquées
  sont inspectées, sans afficher les valeurs privées détectées.
- Aucun résultat pour les identités privées connues du contexte local, chemins personnels,
  e-mails non examinés ou formats de secrets recherchés dans ces fichiers et artefacts.
- Comparaison du JAR de production avec la version 0.1.13 : les **109 classes sont identiques
  octet par octet**, sans entrée ajoutée. Seul `fabric.mod.json` change (version et attribution).
  Les fonctionnalités, la logique et l'indépendance du mod sont donc préservées par ce nettoyage.
- Licence, notice et métadonnées Maven d'Aircompressor inchangées, y compris sa référence Git publique.
- Aucun contrôleur de build ou client de test embarqué dans le JAR distribué.
- Vérification des espaces et erreurs de patch : `git diff --check` réussie.

JAR à partager : `build/libs/tropimon-chat-filter-0.1.14.jar`.
Les dossiers `build/` et `releases/` complets ne doivent pas être partagés.

## Traces antérieures et limites

- L'historique Git local contient un commit concernant ce module avec une identité d'auteur/committer
  non pseudonymisée, ainsi que trois fichiers historiques contenant une identité personnelle.
  L'historique n'a pas été réécrit ; aucun nouveau commit ni push n'a été réalisé.
- Quatorze anciens JAR présents dans `releases/`, ainsi que les anciens JAR conservés dans `build/`,
  restent des copies non nettoyées. Ils sont conservés sur disque, hors distribution actuelle.
- Le JAR 0.1.13 du launcher est inchangé. Les versions déjà diffusées ou publiées à distance ne sont
  pas effacées par ce travail ; les copies distantes n'ont pas été vérifiées.
- Aucun véritable secret n'a été identifié par cette inspection. Le contrôleur ne peut pas garantir
  l'absence de toute identité inconnue, de données personnelles dans une image, ni de secret encodé
  arbitrairement. Des critères privés complémentaires peuvent être fournis hors dépôt, comme décrit
  dans le README ; les résultats doivent rester examinés avant chaque livraison.
- Aucun jeu, serveur ou client de test Minecraft n'a été lancé. Les tests unitaires et la comparaison
  binaire ne constituent pas une nouvelle validation sur le serveur Tropimon.

Les fichiers personnels originaux et les autres mods n'ont pas été modifiés par ce nettoyage.
