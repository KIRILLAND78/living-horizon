# Living Horizon — Journal des modifications

## Prochaine version

### Client

#### Nouveautés
- **Prise en charge du terrain de Lodistance** — Les créatures et joueurs lointains se cachent maintenant derrière le terrain lointain de Lodistance comme derrière n'importe quelle colline et se fondent dans son brouillard, et les créatures lointaines marchent sur son sol, comme avec Voxy et Distant Horizons. (Minecraft 26.3, avec OpenGL, sans shaders.)
- **Prise en charge du terrain de Far View** — Les créatures lointaines s'affichent et marchent maintenant sur le terrain lointain de Far View et se cachent derrière ses collines, au lieu d'être masquées au-delà de la distance d'affichage. (Forge et NeoForge, Minecraft 1.20.1 à 1.21.4.)
- **Prise en charge du terrain d'Ecstatic** — Les créatures lointaines apparaissent maintenant sur le terrain d'Ecstatic et marchent sur son sol, au lieu de disparaître au-delà de la distance d'affichage. Elles se cachent correctement derrière ses collines, restent visibles devant elles et se fondent dans son brouillard. (NeoForge, Minecraft 1.21.1, solo ; le brouillard sans shaders.)
- **Créatures simplifiées éclairées par les shaders** — Avec un pack de shaders qui lit les normal maps, comme Photon avec *Normal Mapping* activé, les images simplifiées des créatures lointaines sont maintenant éclairées par le pack comme leurs modèles 3D, un côté au soleil et l'autre à l'ombre, au lieu de paraître plates et plus sombres. Ces images prennent deux fois plus de mémoire vidéo ; le nouveau réglage *Éclairage des shaders* permet de le désactiver. (Minecraft 1.21.6 et plus.)

#### Améliorations
- **Navires lointains plus nets** — Les images simplifiées des bateaux et véhicules élevés, comme les voiliers de Shippy Ships, sont maintenant plus nettes. Les plus grands, comme le galion, en ont maintenant une aussi, au lieu d'être toujours affichés en modèle complet, plus coûteux en performances.
- **Passage plus doux aux images simplifiées** — Les créatures et les joueurs lointains se tournent maintenant peu à peu vers l'angle sous lequel leur image simplifiée les montre, à l'approche de la distance où elle les remplace, au lieu de pivoter d'un coup au moment du passage. La transition du modèle 3D à l'image se remarque ainsi moins.

#### Améliorations
- **Versions NeoForge 1.21.4 et 1.21.11 plus faciles à trouver** — Les versions NeoForge pour Minecraft 1.21.4 et 1.21.11 sont maintenant publiées en bêta. Elles apparaissent ainsi sur CurseForge et dans les launchers qui masquent les versions alpha.

#### Corrections
- **Créatures affichées une seule fois avec Far View** — Les créatures que Far View dessine en boîtes entre 128 blocs et la distance d'affichage ne sont maintenant affichées qu'une fois, au lieu de deux fois l'une sur l'autre. (Forge et NeoForge, Minecraft 1.20.1 à 1.21.4.)
- **Nom des joueurs déconnectés affiché sur Forge** — Viser un joueur déconnecté affiche maintenant son nom et quand il a été vu pour la dernière fois, au lieu de ne rien afficher. (Forge, Minecraft 1.20.1 à 1.20.4.)
- **Meilleure intégration avec Bobby** — Les créatures lointaines restent maintenant visibles juste au-delà de la distance jusqu'où le serveur les envoie, sur le terrain que Bobby conserve, au lieu de disparaître. Les joueurs lointains ne s'arrêtent plus net non plus en sortant de cette distance.
- **Galion de Shippy Ships visible au loin** — Le galion de Shippy Ships reste maintenant visible au loin, là où vous l'avez laissé, comme les autres navires du mod, au lieu de disparaître dès qu'il sort de la distance d'affichage.

## 0.15.0 — 2026-10-31

### Serveur

#### Nouveautés
- **Créatures dans les terres inexplorées** — Living Horizon montre maintenant aux joueurs d'un serveur les créatures que le monde placerait dans les régions que personne n'a encore explorées, jusqu'à 512 blocs autour de chacun par défaut. Leur position est estimée à partir de la seed : elles peuvent être différentes ou absentes une fois sur place. Cette fonctionnalité peut être désactivée ou sa portée modifiée dans les réglages du serveur.
- **Partage de la seed** — Les serveurs peuvent maintenant autoriser Living Horizon à partager la seed du monde avec les joueurs qui ont le mod, pour que leur jeu estime lui-même les créatures partout, à la longue-vue ou avec *Deviner les créatures*. Cette option est désactivée par défaut, car connaître la seed permet de tout savoir du monde.

#### Améliorations
- **Vol en élytres visible au loin** — Le serveur indique maintenant quels joueurs volent avec des élytres, afin qu'ils soient correctement affichés en train de planer à longue distance.

### Client

#### Nouveautés
- **Accès rapide aux pages de Living Horizon** — Les réglages du mod proposent maintenant des raccourcis vers Modrinth, CurseForge, Nexus Mods et le site officiel, en plus de Discord.
- **Créatures dans les zones inexplorées** — Les régions que personne n'a encore explorées ne sont plus vides : Living Horizon estime à partir de la seed les créatures que le monde y placerait, jusqu'à la *Distance d'affichage des créatures*. Comme elles sont seulement estimées, elles peuvent être différentes ou absentes une fois sur place. Disponible en solo et sur les serveurs équipés du mod ; le réglage *Créatures devinées* permet de les masquer.
- **Créatures estimées à la longue-vue** — Regarder dans une longue-vue affiche maintenant les créatures estimées des zones inexplorées là où vous visez, quelle que soit la distance. Sur un serveur, celui-ci doit partager sa seed.
- **Estimation des créatures alentour** — Le nouveau bouton *Deviner les créatures* affiche d'un coup les créatures estimées jusqu'à 1024 chunks autour de vous, et les conserve pour vos prochaines sessions. Sur un serveur, celui-ci doit partager sa seed.
- **Saisie des valeurs au clavier** — Les curseurs des réglages du mod qui se terminent par *Illimité* (nombres maximum et distances d'affichage) acceptent maintenant une valeur tapée au clavier, même au-delà de leur dernier cran, et la page de Sodium l'affiche aussi. Faites un clic droit sur un curseur pour saisir une valeur précise ; les flèches du clavier ajustent aussi chaque curseur cran par cran.
- **Aperçu des distances dans le monde** — Lorsque vous survolez ou modifiez un réglage de distance, sa limite s'affiche maintenant directement dans le monde, derrière le menu. Vous voyez ainsi d'un coup d'œil jusqu'où porte la valeur choisie.

#### Améliorations
- **Distance de simplification facultative** — La *Distance de simplification* peut maintenant être désactivée. Les créatures, entités, joueurs et oiseaux ne deviennent alors des images simplifiées que lorsqu'ils paraissent assez petits à l'écran.
- **Jusqu'à 4000 créatures lointaines** — Le réglage *Créatures max* va maintenant jusqu'à 4000 avant *Illimité*.
- **Illimité en bout de curseur** — *Illimitée* se trouve maintenant tout à droite des curseurs de distance d'affichage, après 4000 blocs, au lieu de tout à gauche. Les curseurs se lisent ainsi de la plus courte à la plus longue distance.
- **Confirmation avant Illimité** — Le jeu demande maintenant confirmation avant de régler sur *Illimité* un nombre de créatures, d'entités ou de joueurs déconnectés, ou une distance d'affichage. Ces valeurs peuvent faire fortement chuter les FPS, voire faire planter le jeu. Sur la page de Sodium, la question s'affiche au moment d'*Appliquer*.
- **Limites par défaut plus raisonnables** — Plus aucun réglage n'est illimité par défaut, pour éviter les grosses chutes de performances : *Distance d'affichage des joueurs*, *Distance des animations* et *Distance d'affichage des entités* s'arrêtent à 4000 blocs. Les préréglages *Élevé* et *Ultra* affichent au plus 1500 et 3000 créatures, *Ultra* jusqu'à 2048 blocs, et les réglages restés sur les anciennes valeurs par défaut passent aux nouvelles.
- **Vol en élytres plus naturel** — Les joueurs lointains qui volent avec des élytres sont maintenant affichés ailes déployées, dans une vraie posture de vol, au lieu de courir debout dans les airs.
- **Changement de pack de ressources plus fluide** — Les images simplifiées des créatures ne sont plus recréées inutilement quand un pack de ressources est désactivé puis réactivé. Le chargement des ressources ne fige plus non plus le jeu pendant leur recherche.

#### Corrections
- **Créatures lointaines visibles sur le terrain lointain** — Les créatures lointaines, estimées comprises, restent maintenant visibles sur le terrain lointain, au lieu d'être masquées comme s'il n'y avait rien sous elles : un moment après l'ouverture d'un monde, parmi des milliers de créatures estimées, ou quand Voxy ou Distant Horizons tarde à répondre.
- **Brouillard de Distant Horizons appliqué aux créatures** — Les créatures lointaines reçoivent maintenant le même brouillard que le terrain de Distant Horizons et s'estompent avec lui, au lieu de ressortir nettement sur le paysage. (Sans shaders.)

#### Améliorations
- **Créatures volantes d'Ice and Fire vues sous tous les angles** — Les dragons, amphithères et hippogriffes d'Ice and Fire ont maintenant des images simplifiées vues d'en dessous et d'au-dessus, comme les phantoms et les avions, au lieu d'être vus seulement de côté. Ils restent ainsi fidèles quand ils vous survolent ou que vous les regardez d'en haut.
- **Créatures géantes plus nettes au loin** — Les très grandes créatures gardent maintenant leur modèle 3D complet tant qu'elles paraissent grandes à l'écran, au lieu de devenir une image simplifiée pixelisée.

#### Corrections
- **Créatures d'Ice and Fire correctement simplifiées** — Les dragons lointains d'Ice and Fire ont maintenant des images simplifiées entières à leur vraie taille, au lieu d'un gros plan de leurs pattes ou d'une aile. Des dragons d'âges différents ne partagent plus la même image, et les serpents de mer gardent leur modèle 3D au lieu d'une image coupée.
- **Les joueurs déconnectés gardent leur bateau** — Un joueur déconnecté au milieu de l'eau ne perd plus le bateau où il se repose quand vous vous éloignez : il le garde de près comme de loin, et la prochaine fois que vous jouez.
- **Les joueurs déconnectés ne flottent plus dans les airs** — Un joueur déconnecté en l'air, en vol plané avec des élytres ou en pleine chute, n'y remonte plus quand vous vous éloignez : il se repose sur le sol sous lui. Avec Voxy ou Distant Horizons, il est au sol avant même que vous vous approchiez.
- **Créatures lointaines au repos naturel** — Les créatures lointaines au-delà de la *Distance des animations*, ou plus petites que la *Taille min. animée*, restent maintenant à leur place, au lieu de glisser d'avant en arrière les pattes immobiles en tremblant. Elles reprennent leur marche là où elles s'étaient arrêtées une fois suffisamment proches.

## 0.14.0 — 2026-10-29

### Client

#### Nouveautés
- **Véhicules des autres mods visibles au loin** — Les véhicules ajoutés par d'autres mods, comme les avions d'Immersive Aircraft, restent maintenant visibles au-delà de la distance d'affichage, là où ils ont été laissés, comme les bateaux. Ceux qu'on monte sans les diriger, comme les wagonnets, sont désactivés par défaut et peuvent être activés dans *Créatures et entités...*.
- **Réglages dédiés aux entités** — Les bateaux, véhicules et mannequins ont maintenant leurs propres réglages, séparés de ceux des créatures : affichage, nombre maximum et distance d'affichage. Leur distance est illimitée par défaut.
- **Simplification séparée pour les créatures et les entités** — *Entités simplifiées* devient deux options : *Créatures simplifiées* et *Entités simplifiées*. Les bateaux, véhicules et mannequins peuvent ainsi devenir eux aussi des images simplifiées au loin, y compris vus d'au-dessus et d'en dessous. *Joueurs simplifiés* devient indépendant, et l'écran des images s'appelle désormais *Aperçu des images...*, avec un onglet pour les entités.
- **Journal des modifications en jeu** — Un nouveau bouton dans les réglages du mod affiche les nouveautés de chaque version, sans quitter le jeu. Il est aussi disponible sur la page de Sodium.

#### Améliorations
- **Message sur les serveurs incompatibles** — En rejoignant un serveur sans Living Horizon, ou avec une version trop ancienne ou trop récente, un message dans le tchat vous prévient maintenant que les joueurs lointains n'y seront pas affichés. Ce message n'apparaît pas si *Afficher les joueurs* est désactivé.
- **Créatures et entités en deux onglets** — L'écran *Créatures et entités...* sépare maintenant les créatures et les autres entités dans deux onglets, pour s'y retrouver plus facilement. Les bateaux et véhicules s'y affichent ou se masquent simplement.
- **Choix des créatures appliqués immédiatement** — Afficher ou masquer un type dans *Créatures et entités...* se voit maintenant aussitôt dans le monde derrière le menu, sans avoir à le fermer.
- **Réglages des créatures mieux organisés** — Les réglages *Créatures et entités lointaines* sont maintenant répartis en trois parties : *Général*, *Créatures* et *Entités*.
- **Joueurs montés plus fidèles au loin** — Avec *Joueurs simplifiés*, un joueur lointain en bateau, en wagonnet, en avion ou sur un ghast joyeux est maintenant correctement représenté vu d'en haut et d'en bas, au lieu d'apparaître toujours de profil.
- **Avions qui restent en vol** — Un joueur qui se déconnecte en plein vol reste maintenant en l'air dans son avion, qui fait du surplace ou tourne en rond selon qu'il était à l'arrêt ou en mouvement.
- **Origine des images visible** — L'écran *Aperçu des images* indique maintenant d'où vient chaque image (partagée, reprise d'une session précédente, nouvelle ou non sauvegardée), ainsi que la date et le monde où elle a été créée.
- **Animaux assis qui restent assis** — Les chats, loups et perroquets à qui vous avez ordonné de s'asseoir restent maintenant assis au loin, au lieu de se lever et de partir se promener.

#### Corrections
- **Plantage de Créatures et entités corrigé** — Ouvrir *Créatures et entités...* depuis l'écran titre ne fait plus planter le jeu. (Minecraft 26.x.)
- **Véhicules moddés qui gardent leurs couleurs** — Les véhicules et créatures lointains de certains mods, comme les avions d'Immersive Aircraft, gardent maintenant leurs couleurs quand vous vous en approchez, au lieu de devenir noirs un instant.
- **Montures affichées une seule fois** — La monture ou le véhicule d'un joueur lointain, comme un avion moddé, n'apparaît plus en double à côté de lui.

## 0.13.0 — 2026-10-28

### Client

#### Améliorations
- **Jeu plus fluide avec les créatures lointaines** — Living Horizon calcule maintenant en arrière-plan où les créatures lointaines et les joueurs déconnectés peuvent marcher, sans ralentir le jeu.
- **Rendu plus fluide avec Voxy et Distant Horizons** — Le jeu attend moins le pilote graphique à chaque image quand des créatures lointaines sont affichées sur le terrain de Voxy ou de Distant Horizons. Le gain est surtout visible sur les cartes NVIDIA avec l'*Optimisation threadée* activée.
- **Phantoms et ghasts qui volent au loin** — Les phantoms lointains tournent maintenant en rond dans le ciel et les ghasts dérivent lentement, au lieu de rester figés en l'air.
- **Phantoms et ghasts vus d'en dessous** — En images simplifiées, les phantoms et les ghasts sont maintenant aussi représentés vus d'en dessous et d'au-dessus, comme les oiseaux, et plus seulement de profil.
- **Plus fluide avec beaucoup de créatures lointaines** — Le jeu ne perd plus de temps sur chaque créature lointaine à chaque image. (Vulkan sur Minecraft 26.2 et plus, Distant Horizons sur Minecraft 26.3.)
- **Toutes les créatures visibles au loin** — Les monstres, chauves-souris, poissons et toutes les autres créatures sont maintenant affichés au loin par défaut et s'y promènent comme les animaux. Les endermen restent ainsi visibles à travers l'End. Les animaux que vous aviez masqués le restent.

#### Corrections
- **Boutons des réglages remis en ordre** — Les boutons en bas de *Créatures et entités lointaines* ne se chevauchent plus, et *Réinitialiser* retrouve sa place à gauche de *Terminé*. (Minecraft 1.20.1.)
- **Blocage en changeant de dimension corrigé** — Le jeu ne se bloque plus définitivement lorsque vous traversez un portail ou quittez un monde. (Avec Distant Horizons.)

## 0.12.1 — 2026-10-27

### Client

#### Corrections
- **Commande de benchmark réservée aux tests** — `/livinghorizon benchmark` n'est plus proposée aux joueurs : elle sert uniquement à tester le mod.

## 0.12.0 — 2026-10-23

### Serveur

#### Nouveautés
- **Réglages du serveur** — Les propriétaires de serveur peuvent maintenant choisir ce que Living Horizon partage : joueurs connectés, joueurs déconnectés et créatures, jusqu'à quelle distance, uniquement entre coéquipiers si vous le souhaitez, et quels joueurs ou quelles dimensions ne sont jamais partagés. Les réglages se trouvent dans `config/livinghorizon-server.json` et s'appliquent en quelques secondes, sans redémarrage.

#### Corrections
- **Connexion aux serveurs Forge corrigée** — Les joueurs qui ont le mod ne sont plus éjectés d'un serveur Forge qui l'a aussi, avec le message « mismatched mod channel list ». (Forge 1.20.2 et plus.)
- **Spectateurs et joueurs invisibles bien cachés** — Les joueurs en mode spectateur ou invisibles ne sont plus visibles au loin par les autres joueurs, ni à l'endroit où ils se sont déconnectés.

### Client

#### Nouveautés
- **Bouton Discord** — Les réglages du mod permettent maintenant de rejoindre le Discord de Living Horizon en un clic, pour poser une question ou signaler un problème. Le bouton est aussi disponible sur la page de Sodium.
- **Version du mod affichée** — Les écrans de réglages et le panneau de debug indiquent maintenant la version de Living Horizon, pratique à préciser dans un signalement de bug.
- **Taille de simplification** — Un nouveau réglage transforme les créatures, joueurs et oiseaux lointains en images simplifiées dès qu'ils paraissent plus petits qu'une certaine taille à l'écran (10 pixels par défaut), même avant la *Distance de simplification*. Ce qui est à peine visible coûte ainsi beaucoup moins cher à afficher.

#### Améliorations
- **Plus fluide avec des centaines de créatures lointaines** — *Ignorer cachés* coûte beaucoup moins cher quand de nombreuses créatures et de nombreux joueurs lointains sont à l'écran, et Living Horizon effectue moins de travail en coulisses.
- **Versions pour Minecraft 1.21.1 plus faciles à trouver** — Les versions Fabric, Quilt et NeoForge pour Minecraft 1.21 et 1.21.1 sont maintenant publiées en bêta. Elles apparaissent ainsi sur CurseForge et dans les launchers qui masquent les versions alpha.
- **Moins de saccades avec beaucoup de monde au loin** — Les créatures, joueurs et oiseaux lointains demandent beaucoup moins de mémoire au jeu à chaque image, ce qui réduit les saccades.
- **Plus fluide avec beaucoup d'oiseaux** — Les oiseaux situés derrière vous ou trop petits pour être vus ne sont plus dessinés.

#### Corrections
- **Créatures toujours posées sur le terrain** — Les créatures lointaines ne sont plus affichées là où aucun terrain n'est encore visible sous elles, qu'il s'agisse de celui du jeu, de Voxy ou de Distant Horizons. Elles ne flottent donc plus dans le vide.
- **Mondes solo qui s'ouvrent de nouveau sur Forge** — Un monde solo ne se ferme plus dès son ouverture avec le message « mismatched mod channel list ». (Forge 1.20.2 et plus.)
- **Créatures stables sur le terrain de Distant Horizons** — En solo, les créatures lointaines ne disparaissent plus et continuent de marcher sur les collines et les montagnes de Distant Horizons.

## 0.11.0 — 2026-10-22

### Serveur

#### Améliorations
- **Plus léger sur les gros serveurs** — Le serveur passe moins de temps à choisir les skins et montures à envoyer à chaque joueur. Les longues listes de joueurs déconnectés sont maintenant envoyées en plusieurs fois, au lieu d'un seul message trop volumineux pour le jeu.
- **Mise à jour requise pour les joueurs** — Sur un serveur qui utilise cette version, les joueurs doivent aussi l'installer pour voir les joueurs lointains.

#### Corrections
- **Fichier des joueurs déconnectés protégé** — Si le serveur ne parvient pas à lire `livinghorizon-positions.dat`, le fichier n'est plus écrasé : il est conservé sous le nom `livinghorizon-positions.dat.bad` et la console le signale.

### Client

#### Nouveautés
- **Benchmark** — La commande `/livinghorizon benchmark` mesure ce que coûte chaque partie du mod dans votre monde et produit un rapport à joindre à un signalement de bug.
- **Monde de benchmark** — `/livinghorizon benchmark world` crée un monde de test toujours identique, peuplé de créatures et de joueurs déconnectés, pour que les mesures puissent être comparées d'une fois sur l'autre. Votre propre monde n'est pas modifié.
- **Déconnectés max** — Un nouveau réglage limite le nombre de joueurs déconnectés affichés en même temps, en commençant par les plus proches (256 par défaut). Les autres réapparaissent quand vous vous en approchez.

#### Améliorations
- **Message plus clair quand les versions diffèrent** — Quand le mod du serveur est trop ancien ou trop récent par rapport au vôtre, le journal indique maintenant lequel mettre à jour, au lieu d'affirmer que le serveur n'a pas le mod.
- **Plus fluide avec beaucoup de joueurs déconnectés** — Les joueurs déconnectés coûtent moins cher à afficher au loin, et ceux cachés derrière les collines ne sont plus dessinés.

#### Corrections
- **Distance d'affichage des joueurs respectée** — Les joueurs déconnectés ne sont plus affichés au-delà de la *Distance d'affichage des joueurs*.
- **Mannequins lointains avec leur vrai skin** — Les mannequins lointains gardent maintenant le skin qu'ils portent de près, qu'il vienne d'un joueur ou d'un pack de ressources, au lieu d'apparaître en Alex. (Minecraft 1.21.9 et plus.)
- **Eau préservée sous Photon avec Distant Horizons** — Avec *Masquer par le relief* activé, l'eau reste maintenant normale quand une créature ou un joueur lointain est en vue, au lieu de laisser voir son fond nu et ombré. (Minecraft 26.3.)
- **Capes des mannequins qui pendent normalement** — Les capes des mannequins lointains pendent maintenant comme de près, au lieu de se dresser en l'air. (Minecraft 1.21.9 et plus.)
- **Fichier de réglages protégé** — Une erreur en modifiant `livinghorizon.json` à la main n'efface plus le fichier : il est conservé sous le nom `livinghorizon.json.bad` et le journal le signale.
- **Sauvegardes protégées** — Si le jeu se ferme pendant une sauvegarde, les fichiers des créatures mémorisées (celles que Living Horizon continue d'afficher au loin) et des joueurs déconnectés ne sont plus abîmés. Un fichier abîmé est conservé en `.bad` au lieu d'être vidé.
- **Connexion plus fluide aux gros serveurs** — Rejoindre un serveur avec beaucoup de joueurs déconnectés ne fait plus saccader le jeu.
- **Images abîmées recréées automatiquement** — Une image simplifiée sauvegardée et abîmée ne peut plus faire manquer de mémoire au jeu : elle est simplement recréée.
- **Créatures visibles devant le terrain de Distant Horizons avec Oculus** — Avec Oculus et un pack de shaders, les créatures lointaines ne disparaissent plus derrière le terrain de Distant Horizons, un problème qui s'aggravait avec la distance d'affichage. (Forge.)
- **Mouvements de tête naturels avec Entity Culling** — Les créatures et joueurs lointains ne secouent plus la tête à toute vitesse avec Entity Culling, seul ou avec Chloride.
- **Joueurs dans la bonne dimension moddée** — Les joueurs, connectés ou déconnectés, d'une dimension moddée comme l'Aether ne sont plus affichés dans une autre, comme la Twilight Forest. Sur un serveur, celui-ci doit aussi utiliser cette version.
- **Brouillard de Voxy appliqué aux créatures** — Les créatures lointaines sur le terrain de Voxy reçoivent maintenant le même brouillard que le paysage autour d'elles, au lieu de pâlir jusqu'à la couleur du brouillard au-delà d'une certaine distance. (Sans shaders.)

## 0.10.0 — 2026-10-20

### Serveur

#### Nouveautés
- **Living Horizon sur les serveurs** — Le mod s'installe maintenant aussi sur le serveur : placez le jar dans son dossier `mods` (Fabric, Quilt, NeoForge ou Forge, avec la Fabric API sur Fabric et Quilt). Aucune configuration n'est nécessaire, et il remplace le data pack.
- **Joueurs partagés en temps réel** — Le serveur envoie la position de chaque joueur cinq fois par seconde aux joueurs qui ont le mod, avec son skin, son armure, ses objets en main et sa monture.
- **Créatures partagées** — Le serveur envoie les créatures situées jusqu'à 2048 blocs autour de chaque joueur, même celles qu'il n'a jamais croisées : animaux, villageois, golems, allays et bateaux, avec leur selle, harnais, armure ou coffre.
- **Joueurs déconnectés partagés** — Le serveur retient où chaque joueur s'est déconnecté, son apparence, sa monture et l'heure de son départ, et les transmet à tous, y compris aux joueurs qui arrivent plus tard.
- **Joueurs d'avant l'installation** — Au premier démarrage, le serveur retrouve les joueurs que le monde connaissait déjà et les affiche comme joueurs déconnectés, là où ils sont partis, avec leur équipement.
- **Sauvegarde dans le monde** — Tout est enregistré dans `livinghorizon-positions.dat`, dans le dossier du monde. Une créature est oubliée lorsque son chunk se charge et qu'elle n'y est plus.
- **Mondes en LAN** — Un monde ouvert en LAN partage tout depuis le jeu de l'hôte, sans rien installer de plus.
- **Joueurs sans le mod bienvenus** — Les joueurs qui n'ont pas le mod peuvent toujours se connecter ; le serveur ne leur envoie simplement rien.
- **Même version des deux côtés** — Gardez le mod à jour sur le serveur comme chez les joueurs : un jeu qui ne comprend pas les messages du serveur les ignore.
- **Confidentialité : positions partagées** — Le serveur partage la position de chaque joueur avec tous les joueurs qui ont le mod, quelle que soit la distance. Pensez à prévenir vos joueurs.
- **Fin du data pack** — Le data pack n'est plus utilisé : retirez-le du dossier `datapacks` du monde. Les coordonnées ne passent plus par le tableau des scores et n'apparaissent donc plus dans les scores ni dans la barre latérale.

#### Corrections
- **Joueurs conservés à la fermeture d'un monde** — Quand un serveur ou un monde en LAN se ferme, les joueurs encore présents restent maintenant là où ils étaient, en tant que joueurs déconnectés, au lieu de disparaître.

### Client

#### Nouveautés
- **Tous les joueurs, à n'importe quelle distance** — Sur un serveur équipé du mod, vous voyez maintenant tous les autres joueurs, où qu'ils soient, avec leur skin, leur armure, leurs objets en main et leur monture.
- **Créatures jamais croisées** — Sur un serveur équipé du mod, les créatures lointaines apparaissent immédiatement, même celles dont vous ne vous êtes jamais approché, avec leur selle, harnais, armure ou coffre.
- **Joueurs déconnectés visibles dès votre arrivée** — Vous voyez les joueurs déconnectés là où ils sont partis, avec leur apparence et leur monture, même s'ils sont partis avant votre arrivée.
- **Joueurs déconnectés en solo** — En solo, les joueurs déconnectés de votre monde apparaissent dès son ouverture, y compris ceux d'avant l'installation du mod.
- **Horizon vivant dès la connexion** — Les créatures lointaines et les joueurs déconnectés apparaissent dès que vous rejoignez, et non quelques secondes plus tard.
- **Serveurs sans le mod** — Vous pouvez toujours les rejoindre. Seules les créatures que vous avez déjà croisées y sont affichées, sans joueurs lointains, et le journal le signale une fois.

#### Améliorations
- **Position des joueurs lointains plus fiable** — Les joueurs lointains sont maintenant placés uniquement d'après les positions partagées par le serveur. L'estimation par la barre de localisation disparaît, ainsi que le réglage *Délai joueur perdu*.

#### Corrections
- **Ghast joyeux affiché correctement** — Un joueur lointain sur un ghast joyeux n'a plus de second ghast à côté de lui, et celui qui le chevauchait déjà à votre arrivée ne flotte plus dans le vide.
- **Ghast joyeux qui reste où il a été laissé** — Quand un joueur lointain descend de son ghast joyeux, celui-ci reste maintenant là où il a été laissé, au lieu de réapparaître à son point de départ pour revenir en volant.
- **Nouveau skin visible au loin** — Un joueur lointain qui change de skin apparaît maintenant avec le nouveau, au lieu de garder l'ancien au loin.
- **Joueurs bien assis dans les bateaux** — Un joueur qui se déconnecte dans un bateau regarde maintenant dans le sens du bateau, au lieu d'être assis de travers.
- **Cavaliers bien en selle** — Un joueur lointain à cheval est maintenant assis correctement, en modèle complet comme en image simplifiée, au lieu de s'enfoncer dans le cheval ou de s'asseoir sur sa croupe.
- **Un seul cheval après la déconnexion** — Un joueur qui se déconnecte à cheval ne laisse plus une copie du cheval à côté de celui sur lequel il reste.
- **Boutons Website et Issues actifs** — Les boutons Website et Issues de la page du mod dans Mod Menu ne sont plus grisés. (Fabric.)

## 0.9.0 — 2026-10-19

### Corrections
- **Joueurs déconnectés sortis de l'eau** — Les joueurs qui se sont déconnectés en nageant ne restent plus debout dans l'eau : ils attendent sur le rivage le plus proche, ou rament dans un bateau si celui-ci est à plus de 10 blocs.
- **Eau intacte autour des bateaux** — L'eau ne devient plus transparente autour des joueurs déconnectés qui rament avec un pack de shaders comme Photon, et ne disparaît plus en un grand carré autour des bateaux lointains.
- **Rameurs toujours dans leur bateau** — Au loin, les joueurs déconnectés qui rament gardent maintenant leur bateau pendant que leur image simplifiée est créée, au lieu d'apparaître debout sans lui.

## 0.8.0 — 2026-10-18

### Nouveautés
- **Joueurs simplifiés** — Un nouveau réglage permet de garder les joueurs lointains en modèles 3D complets, tandis que les créatures lointaines continuent de devenir des images simplifiées.
- **12 nouvelles langues** — Living Horizon est maintenant disponible en allemand, espagnol, italien, portugais du Brésil, polonais, turc, russe, ukrainien, chinois simplifié et traditionnel, japonais et coréen.
- **Réglages dans Sodium** — Les réglages de Living Horizon sont maintenant accessibles depuis les *Paramètres vidéo* de Sodium, sur leurs propres pages. (Sodium 0.8 et plus.)
- **Joueurs déconnectés avec leur monture** — Un joueur parti à cheval, en bateau ou sur un ghast joyeux continue de le monter autour de l'endroit où il s'est déconnecté.
- **Contrôle de la mémoire vidéo** — L'écran *Entités simplifiées...* indique la mémoire vidéo utilisée par les images des créatures et des joueurs, et deux nouveaux réglages fixent une limite pour chacun.

### Améliorations
- **Réglages plus clairs** — Chaque réglage a maintenant un nom et une description plus clairs, dans le style des options de Minecraft, et les curseurs de distance occupent toute la largeur de l'écran.
- **Joueurs déconnectés plus vivants** — Les joueurs déconnectés font maintenant une petite ronde autour de l'endroit où ils sont partis. Au loin, ils deviennent des images simplifiées comme les joueurs connectés, qu'ils soient debout, assis, allongés ou dans un lit.
- **Cavaliers plus légers à afficher** — Au loin, un joueur sur un cheval, dans un bateau, un wagonnet ou toute autre monture forme maintenant une seule image simplifiée avec elle, bien plus légère que deux modèles complets.
- **Grandes foules de joueurs plus fluides** — Au-delà d'une centaine de joueurs lointains, les suivants deviennent eux aussi des images simplifiées au lieu de rester en modèles complets.

### Corrections
- **Créatures bien cachées après un redimensionnement** — Les créatures lointaines ne traversent plus les collines, y compris celles de Voxy et de Distant Horizons, après un changement de taille de la fenêtre du jeu.
- **Joueurs et slimes visibles avec Distant Horizons** — Les joueurs lointains, les joueurs déconnectés et les slimes ne disparaissent plus sous le terrain de Distant Horizons. (Minecraft 26.1 et plus.)
- **Créatures entières devant le terrain de Distant Horizons** — Le terrain de Distant Horizons ne mord plus sur les créatures et les joueurs placés devant lui. (Minecraft 26.1 et plus.)
- **Créatures stables avec le balancement de la vue** — Avec le balancement de la vue activé, les créatures lointaines ne clignotent plus et le terrain de Distant Horizons ne vacille plus devant elles à chaque pas. (Minecraft 26.1 et plus.)

## 0.7.0 — 2026-10-17

### Nouveautés
- **Les joueurs déconnectés prennent vie** — Les joueurs déconnectés vivent maintenant autour de l'endroit où ils sont partis : ils se promènent, s'assoient, regardent les coffres et les blocs de métier, vont se coucher la nuit et lèvent leur bouclier quand on les frappe ou que des monstres approchent. Ils restent toujours au sol : ils tombent si vous cassez le bloc sous eux, et remontent si vous les enterrez.
- **Mention « hors ligne »** — Les joueurs déconnectés affichent *(hors ligne)* après leur pseudo, et en viser un de près indique quand il a été vu pour la dernière fois. Leurs pseudos, ou les joueurs déconnectés eux-mêmes, peuvent être masqués dans les réglages.
- **Oiseaux en 3D** — Les oiseaux sont maintenant de petits modèles 3D, comme les créatures de Minecraft : ils battent des ailes, se posent sur leurs pattes et deviennent des images simplifiées au loin. Le réglage *Style des oiseaux* permet de retrouver les anciens oiseaux en 2D.
- **Hmm…** — J'ai cru voir quelque chose dans le ciel. 😉

### Améliorations
- **Créatures qui sautent** — Les slimes, cubes de magma et lapins lointains se déplacent maintenant par bonds au lieu de glisser. Les slimes s'écrasent en retombant, et les poules battent des ailes en descendant d'un bloc.
- **Canards plus naturels** — Les canards s'envolent maintenant ensemble à votre approche et se posent plus loin sur l'eau, au lieu de partir pour de bon.

### Corrections
- **Créatures animées à la longue-vue** — Les créatures lointaines vues à la longue-vue sont maintenant prises en compte à leur taille zoomée et continuent de marcher quand elles paraissent grandes, au lieu de se figer. (Réglage *Taille min. animée*.)
- **Créatures animées dès l'ouverture d'un monde** — Les créatures lointaines marchent et broutent maintenant dès le début, au lieu de tourner sur place.
- **Oiseaux posés plus naturels** — Les mésanges, rouges-gorges et canards ne font plus de va-et-vient, ne flottent plus dans le vide, ne se posent plus dans les feuilles et ne nagent plus sur la terre ferme. Les canards n'apparaissent plus juste à côté de vous pour s'envoler aussitôt et nagent maintenant sur leur mare, et les rouges-gorges et mésanges sautillent davantage et s'envolent moins.
- **Bonne licence dans la liste des mods** — Living Horizon affiche maintenant la bonne licence dans la liste des mods. (Forge, NeoForge et Quilt.)

## 0.6.0 — 2026-10-15

### Nouveautés
- **Images des créatures conservées** — Les images simplifiées des créatures lointaines sont maintenant enregistrées sur le disque et réutilisées d'une session à l'autre. Elles ne sont recréées qu'après un changement de packs de ressources, de mods ou de version du jeu.

### Améliorations
- **Nouvelle icône** — Living Horizon a une nouvelle icône en pixel art : Steve au premier plan, entouré de créatures, de villageois, de joueurs et d'oiseaux jusqu'aux montagnes.
- **Créatures lointaines plus cohérentes à votre retour** — En solo, les créatures lointaines se téléportent ou foncent beaucoup moins souvent vers un autre endroit quand vous revenez : elles suivent les vraies créatures tant que le jeu les fait bouger, et disparaissent quand celles-ci meurent.

### Corrections
- **Écran Entités simplifiées sans clignotement** — Deux créatures du même nom, comme des villageois aux tenues différentes, n'échangent plus leur place en clignotant.
- **Images des créatures plus propres** — Les créatures lointaines en images simplifiées n'ont plus de fins traits sombres ni de points noirs en haut ou en bas. Les créatures longues comme les chevaux et les renifleurs ne sont plus coupées au museau ou à la queue.
- **Plantage avec les créatures GeckoLib corrigé** — Un monde ne plante plus quelques secondes après son chargement quand une créature d'un mod utilisant GeckoLib a été vue au loin. Ces créatures ne sont pas encore affichées au loin. (Minecraft 1.21.2 à 1.21.8.)
- **Entités simplifiées plus rapides** — Le jeu ne ralentit plus avec les images simplifiées : les mêmes créatures ne sont plus redessinées en boucle, et celles qui se ressemblent partagent une seule image.

## 0.5.0 — 2026-10-13

### Nouveautés
- **Entités simplifiées sur Minecraft 1.20.1 à 1.21.8** — Les créatures et joueurs très lointains peuvent maintenant être affichés en images simplifiées sur ces versions aussi, ce qui allège beaucoup le travail de votre ordinateur.

### Corrections
- **Plantage au démarrage corrigé** — Le jeu ne plante plus au démarrage avec le mod installé. (Forge, Minecraft 1.20.1 à 1.20.4.)
- **Plantage de l'écran Entités simplifiées corrigé** — Ouvrir l'écran *Entités simplifiées* ne fait plus planter le jeu. (Minecraft 1.21.8 et antérieures.)
- **Créatures présentes dès votre arrivée** — Les créatures déjà autour de vous quand vous rejoignez un monde apparaissent maintenant elles aussi à l'horizon, et plus seulement celles rencontrées ensuite.

## 0.4.2 — 2026-10-12

### Corrections
- **Créatures stables en s'éloignant** — Une créature qui sort de votre distance d'affichage ne disparaît plus un instant et ne se tourne plus dans une autre direction.
- **Créatures entièrement cachées par les collines** — Les vêtements d'un villageois, un objet tenu en main ou l'enveloppe d'un slime ne traversent plus les collines de Voxy ou de Distant Horizons quand le reste de la créature est caché. (Minecraft 1.21.9 et plus, avec Distant Horizons, ou avec Voxy et des shaders.)

## 0.4.1 — 2026-10-11

### Corrections
- **Créatures bien éclairées avec les shaders** — Les créatures lointaines en images simplifiées sont maintenant éclairées par le soleil comme les modèles complets, au lieu d'être trop sombres avec les packs de shaders d'Iris comme Photon ou Complementary.
- **Ombres du terrain corrigées** — Les créatures lointaines ne portent plus l'ombre du terrain situé derrière elles, et l'eau de Voxy s'affiche normalement. (Photon avec Voxy.)
- **Créatures bien cachées par les collines** — Les créatures lointaines ne traversent plus les collines de Voxy ou de Distant Horizons. (Minecraft 26.1 ; Minecraft 26.2 et plus avec des shaders, anciennes versions d'Iris comprises ; Minecraft 26.3 avec Iris installé.)
- **Compatibilité avec Voxy 0.2.18** — Le mod fonctionne de nouveau avec Voxy 0.2.18 : les créatures lointaines se tiennent sur son terrain et ne traversent plus ses collines.
- **Créatures cachées par les bonnes collines avec Distant Horizons** — Les créatures lointaines ne sont plus cachées par des collines qui ne se trouvent pas devant elles. (Distant Horizons avec l'upscaling TAAU de Photon.)

## 0.4.0 — 2026-10-10

### Nouveautés
- **Premier scan automatique** — La première fois que vous entrez dans un monde solo, Living Horizon recherche aussitôt les créatures dans un rayon de 32 chunks, pour que l'horizon soit vivant dès le départ. Le rayon du scan se règle maintenant en chunks, comme la distance d'affichage.
- **Réglages d'animation** — Deux nouveaux curseurs arrêtent l'animation des créatures lointaines trop petites à l'écran ou trop éloignées, pour gagner en performances.
- **Préréglages de qualité** — Bas, Normal, Élevé et Ultra règlent en un clic combien de créatures lointaines et d'oiseaux vous voyez, et jusqu'où. Élevé et Ultra sont destinés aux ordinateurs puissants.

### Corrections
- **Créatures qui gardent leurs couleurs** — Sans shaders, les créatures lointaines ne deviennent plus noires par moments, notamment en vol.

## 0.3.2 — 2026-10-09

### Corrections
- **Créatures cachées par les bonnes collines avec Photon** — Avec Voxy et l'upscaling TAAU de Photon, les créatures lointaines sont de nouveau cachées par les bonnes collines.

## 0.3.1 — 2026-10-08

### Corrections
- **Collines prises en compte sur les anciennes versions** — Les créatures lointaines se cachent maintenant derrière les collines de Voxy et de Distant Horizons, comme sur les versions récentes, au lieu de les traverser. (Minecraft 1.20 à 1.21.4.)
- **Anciennes versions de Distant Horizons prises en charge** — Le mod fonctionne maintenant avec Distant Horizons 2.3 et plus, et plus seulement avec la dernière version.

## 0.3.0 — 2026-10-07

### Nouveautés
- **Compatibilité Vulkan** — Avec le moteur de rendu Vulkan du jeu, les créatures lointaines se cachent aussi derrière les collines, y compris celles de Distant Horizons.

### Corrections
- **Créatures visibles devant le terrain de Distant Horizons** — Sans shaders, les créatures lointaines ne disparaissent plus dans le terrain de Distant Horizons.
- **Plus de traînées en bordure de la distance d'affichage** — Les traînées qui apparaissaient là où s'arrête la distance d'affichage normale ont disparu. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Nouveautés
- **Créatures lointaines** — Les animaux, villageois et golems restent vivants à l'horizon, au-delà de votre distance d'affichage : ils broutent et se promènent.
- **Joueurs lointains** — Les autres joueurs continuent de marcher au loin, avec leur skin, leur armure et leur monture. Les joueurs qui se déconnectent restent endormis là où ils sont partis.
- **Oiseaux** — Des nuées d'oiseaux, des oies en V, des goélands sur la côte et des chauves-souris la nuit animent le ciel.
- **Compatibilité Distant Horizons** — Living Horizon fonctionne avec Distant Horizons comme avec Voxy.
- **Créatures cachées derrière les collines** — Les créatures lointaines situées derrière une colline ne sont pas dessinées, pour garder le jeu fluide.
- **Images simplifiées au loin** — Très loin, les créatures et les joueurs peuvent être affichés en images simplifiées, bien plus légères à afficher quand ils sont nombreux. Cette option est désactivée par défaut.
- **Scan des alentours** — En solo, un scan lit les créatures des chunks autour de vous pour les faire apparaître tout de suite à l'horizon, sans avoir à les croiser d'abord.
- **Écran de réglages** — Un bouton *Living Horizon...* dans le menu Options ouvre les réglages du mod, avec le monde toujours visible derrière, et un bouton permet de revenir aux valeurs par défaut.
- **Quilt, NeoForge et Forge** — Living Horizon fonctionne aussi sur Quilt, NeoForge et Forge, de Minecraft 1.20 à 26.3.
- **Logo du mod** — Living Horizon a maintenant un logo dans la liste des mods et dans celle des data packs.
