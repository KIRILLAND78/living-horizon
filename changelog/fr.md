# Living Horizon — Journal des modifications

## Prochaine version

### Client

#### Corrections
- **Joueurs déconnectés figés au loin** — les joueurs déconnectés ne restent plus immobiles quand tu t'éloignes : là où ils n'ont pas la place de marcher, ils font un pas ou deux, se retournent et s'assoient de temps en temps.

## 0.10.0 — 2026-10-20

### Serveur

#### Nouveautés
- **Le mod sur un serveur** — mets le jar dans le dossier `mods` du serveur, sur Fabric, Quilt, NeoForge ou Forge (plus la Fabric API sur Fabric et Quilt). Rien d'autre à régler. Il remplace le datapack.
- **Positions des joueurs** — le serveur envoie où est chaque joueur, cinq fois par seconde, aux joueurs qui ont le mod. Il envoie aussi leur skin, leur armure, ce qu'ils tiennent et leur monture.
- **Mobs** — le serveur envoie les mobs jusqu'à 2 048 blocs autour de chaque joueur, même ceux qu'il n'a jamais croisés : animaux, villageois, golems, allays et bateaux, avec leur selle, leur harnais, leur armure ou leur coffre.
- **Joueurs déconnectés** — le serveur garde l'endroit où chaque joueur s'est déconnecté, son apparence, sa monture et l'heure de son départ, et l'envoie à tout le monde, même aux joueurs qui arrivent après.
- **Joueurs d'avant le mod** — au premier démarrage, le serveur lit les joueurs que le monde avait déjà (le dossier `playerdata`, leurs noms dans `usercache.json`) et les affiche comme déconnectés, là où ils sont partis, avec ce qu'ils portaient.
- **Fichier du monde** — le serveur sauvegarde tout cela dans `livinghorizon-positions.dat`, dans le dossier du monde. Un mob est oublié quand son chunk se charge et qu'il n'y est plus.
- **Mondes LAN** — un monde ouvert au LAN partage tout par le jeu de l'hôte. Rien à installer.
- **Joueurs sans le mod** — ils peuvent toujours rejoindre. Le serveur ne leur envoie rien.
- **Même mod des deux côtés** — garde le mod à jour sur le serveur et chez les joueurs : un jeu qui ne sait pas lire les messages du serveur les ignore.
- **Positions partagées** — le serveur partage la position de chaque joueur avec tous les joueurs qui ont le mod. Préviens tes joueurs.
- **Fini le datapack** — le mod ne le lit plus : retire-le du dossier `datapacks` du monde. Plus rien ne passe par le scoreboard, donc les coordonnées ne sont plus lisibles dans les scores ni dans la barre latérale.

#### Corrections
- **Joueurs qui disparaissent à la fermeture d'un monde** — quand un serveur ou un monde LAN se ferme, les joueurs présents restent là où ils étaient, en joueurs déconnectés.

### Client

#### Nouveautés
- **Tous les joueurs, à n'importe quelle distance** — sur un serveur avec le mod, tu vois tous les autres joueurs, avec leur skin, leur armure, ce qu'ils tiennent et leur monture.
- **Mobs jamais croisés** — sur un serveur avec le mod, les mobs lointains apparaissent tout de suite, même ceux près desquels tu n'es jamais passé, avec leur selle, leur harnais, leur armure ou leur coffre.
- **Joueurs déconnectés, même avant ton arrivée** — tu les vois là où ils sont partis, avec leur apparence et ce qu'ils montaient.
- **Ton propre monde** — en solo, les joueurs déconnectés de ton monde apparaissent dès que tu l'ouvres, même ceux d'avant le mod.
- **Tout dès l'arrivée** — les mobs lointains et les joueurs déconnectés apparaissent dès que tu rejoins, et non quelques secondes plus tard.
- **Serveurs sans le mod** — tu peux toujours les rejoindre. Aucun joueur lointain n'est affiché, seulement les mobs que tu as croisés, et le journal le signale une fois.

#### Améliorations
- **Position des joueurs lointains** — elle vient uniquement de ce que partage le serveur. L'estimation par la barre de localisation disparaît, et le réglage *Délai joueur perdu* avec.

#### Corrections
- **Joueurs sur un ghast joyeux** — un joueur lointain qui chevauche un ghast joyeux n'a plus de second ghast à côté de lui, et celui qui le chevauchait déjà quand tu arrives ne flotte plus dans le vide.
- **Ghast joyeux qui revient après la descente de son cavalier** — le ghast joyeux dont descend un joueur lointain ne réapparaît plus là où il a été monté pour revenir en volant : il reste là où il a été laissé.
- **Anciens skins au loin** — un joueur lointain qui change de skin ne garde plus l'ancien au loin.
- **Joueurs de travers dans les bateaux** — un joueur qui se déconnecte dans un bateau n'est plus assis tourné sur le côté : il regarde dans le sens du bateau.
- **Cavaliers assis dans leur cheval** — un joueur lointain à cheval n'est plus assis dans le cheval ou sur sa croupe, en modèle complet comme en image plate.
- **Cheval en double après la déconnexion de son cavalier** — un joueur qui se déconnecte à cheval ne laisse plus une seconde copie du cheval à côté de celui sur lequel il reste.
- **Boutons Website et Issues grisés** — les boutons Website et Issues de la page du mod dans Mod Menu ne sont plus grisés (Fabric).

## 0.9.0 — 2026-10-19

### Corrections
- **Joueurs déconnectés debout dans l'eau** — les joueurs partis en nageant ne restent plus debout dans l'eau : ils attendent sur le rivage le plus proche, ou rament dans un bateau quand le rivage est à plus de 10 blocs.
- **Eau qui disparaît autour des bateaux** — l'eau ne devient plus transparente autour des joueurs déconnectés qui rament, avec un pack de shaders comme Photon, et ne disparaît plus en un grand carré autour des bateaux au loin.
- **Rameurs qui perdent leur bateau au loin** — au loin, les joueurs déconnectés qui rament n'apparaissent plus debout sans leur bateau le temps que leur image soit faite.

## 0.8.0 — 2026-10-18

### Nouveautés
- **Réglage Joueurs simplifiés** — garde les joueurs lointains en modèles complets, tandis que les créatures lointaines deviennent toujours des images plates au loin.
- **12 nouvelles langues** — allemand, espagnol, italien, portugais du Brésil, polonais, turc, russe, ukrainien, chinois simplifié et traditionnel, japonais et coréen.
- **Réglages dans le menu de Sodium** — les réglages de Living Horizon apparaissent aussi dans les *Paramètres vidéo* de Sodium, sur des pages à eux (Sodium 0.8 et plus).
- **Les joueurs déconnectés gardent leur monture** — un joueur parti sur un cheval, un bateau ou un ghast joyeux continue de le monter autour de l'endroit où il s'est déconnecté.
- **Réglages de mémoire vidéo** — l'écran *Entités simplifiées...* montre la mémoire vidéo prise par les images des créatures et des joueurs, et *Mémoire des images de créatures* et *Mémoire des images de joueurs* fixent une limite pour chacun.

### Améliorations
- **Réglages plus clairs** — chaque réglage a un nom et une description plus clairs, dans le style des options de Minecraft, et les curseurs de distance prennent toute la largeur.
- **Joueurs déconnectés** — les joueurs déconnectés font une petite ronde autour de l'endroit où ils sont partis, et deviennent des images plates au loin comme les joueurs connectés, debout, assis, couchés ou dans un lit.
- **Cavaliers** — au loin, un joueur sur un cheval, dans un bateau, un wagonnet ou tout autre monture devient une seule image plate avec elle, bien plus légère que deux modèles complets.
- **Grandes foules de joueurs** — au-delà d'une centaine de joueurs lointains, les suivants deviennent eux aussi des images plates au lieu de rester en modèles complets.

### Corrections
- **Créatures visibles à travers les collines après avoir redimensionné la fenêtre** — les créatures lointaines ne traversent plus les collines, celles de Voxy et de Distant Horizons comprises, quand la fenêtre du jeu change de taille.
- **Joueurs et slimes qui disparaissent avec Distant Horizons** — les joueurs lointains, les joueurs déconnectés et les slimes ne disparaissent plus sous le terrain de Distant Horizons (Minecraft 26.1 et plus).
- **Terrain de Distant Horizons qui mord sur les créatures** — le terrain de Distant Horizons ne mord plus sur les créatures et les joueurs placés devant lui (Minecraft 26.1 et plus).
- **Créatures lointaines qui clignotent en marchant** — avec le balancement de la vue, les créatures lointaines ne clignotent plus et le terrain de Distant Horizons ne vacille plus devant elles à chaque pas (Minecraft 26.1 et plus).

## 0.7.0 — 2026-10-17

### Nouveautés
- **Les joueurs déconnectés prennent vie** — les joueurs déconnectés vivent autour de l'endroit où ils sont partis : ils se promènent, s'assoient, regardent les coffres et les blocs de métier, vont au lit la nuit, et lèvent un bouclier quand on les frappe ou que des monstres approchent. Ils restent au sol : casse le bloc sous eux et ils tombent, enterre-les et ils remontent.
- **Noms hors ligne** — les joueurs déconnectés affichent *(hors ligne)* après leur nom, et en viser un de près indique quand il a été vu pour la dernière fois. Des réglages masquent leurs noms, ou les joueurs eux-mêmes.
- **Oiseaux en 3D** — les oiseaux sont maintenant de petits modèles 3D comme les mobs de Minecraft, qui battent des ailes et se posent sur leurs pattes, et deviennent des images plates au loin. Le réglage *Style des oiseaux* remet les anciens oiseaux en 2D.
- **Hmm…** — j'ai cru voir quelque chose dans le ciel. 😉

### Améliorations
- **Créatures qui sautent** — les slimes, cubes de magma et lapins lointains se déplacent maintenant par bonds au lieu de glisser, les slimes s'écrasent en retombant, et les poules battent des ailes en descendant d'un bloc.
- **Canards** — les canards s'envolent maintenant ensemble quand tu approches et se reposent plus loin sur l'eau, au lieu de partir pour de bon.

### Corrections
- **Créatures figées à la longue-vue** — les créatures lointaines vues à la longue-vue comptent maintenant à leur taille zoomée, et continuent donc de marcher quand elles paraissent grosses (réglage *Taille min. animée*).
- **Créatures qui tournent sur place à l'ouverture d'un monde** — les créatures lointaines marchent et broutent maintenant dès le début.
- **Oiseaux posés au comportement étrange** — les mésanges, rouges-gorges et canards ne font plus de va-et-vient, ne flottent plus dans le vide, ne se posent plus dans les feuilles et ne nagent plus sur la terre ferme, et les canards n'apparaissent plus juste à côté de toi pour s'envoler aussitôt. Les canards nagent maintenant sur leur mare, et les rouges-gorges et mésanges sautillent plus et s'envolent moins.
- **Mauvaise licence dans la liste des mods** — Living Horizon n'affiche plus la mauvaise licence dans la liste des mods (Forge, NeoForge et Quilt).

## 0.6.0 — 2026-10-15

### Nouveautés
- **Images des créatures gardées entre les parties** — les images plates des créatures lointaines sont enregistrées sur le disque et réutilisées à la partie suivante ; elles ne sont refaites qu'après un changement de packs de ressources, de mods ou de version du jeu.

### Améliorations
- **Nouvelle icône du mod** — une nouvelle icône en pixel art : Steve au premier plan, et des créatures, des villageois, des joueurs et des oiseaux jusqu'aux montagnes derrière lui.
- **Créatures lointaines à ton retour** — en solo, les créatures lointaines se téléportent ou foncent bien moins souvent vers un autre endroit quand tu reviens : elles suivent les vraies créatures tant que le jeu les fait bouger, et disparaissent quand celles-ci meurent.

### Corrections
- **Clignotement dans l'écran Entités simplifiées** — deux créatures du même nom, comme des villageois aux tenues différentes, n'échangent plus leur place en clignotant.
- **Traits sombres sur les créatures lointaines** — les créatures lointaines dessinées en images plates n'ont plus de fins traits sombres ni de points noirs en haut ou en bas, et les créatures longues comme les chevaux et les renifleurs ne sont plus coupées au museau ou à la queue.
- **Plantage avec les créatures GeckoLib** — un monde ne plante plus quelques secondes après son chargement quand une créature d'un mod GeckoLib a été vue au loin (Minecraft 1.21.2 à 1.21.8). Ces créatures ne sont pas encore montrées au loin.
- **Jeu ralenti par les entités simplifiées** — le jeu ne ralentit plus quand les créatures lointaines sont dessinées en images plates : les mêmes créatures ne sont plus redessinées en boucle, et celles qui se ressemblent partagent une seule image.

## 0.5.0 — 2026-10-13

### Nouveautés
- **Entités simplifiées sur Minecraft 1.20.1 à 1.21.8** — les créatures et joueurs très lointains peuvent maintenant être dessinés en images plates sur ces versions aussi, ce qui est bien plus léger pour ton ordinateur.

### Corrections
- **Plantage au démarrage** — le jeu ne plante plus au démarrage avec le mod installé (Forge, Minecraft 1.20.1 à 1.20.4).
- **Plantage de l'écran Entités simplifiées** — l'ouvrir ne fait plus planter le jeu (Minecraft 1.21.8 et avant).
- **Créatures absentes en rejoignant un monde** — les créatures déjà autour de toi à ton arrivée apparaissent elles aussi à l'horizon, et plus seulement celles rencontrées ensuite.

## 0.4.2 — 2026-10-12

### Corrections
- **Créatures qui buguent en s'éloignant** — une créature qui sort de ta vue normale ne disparaît plus un instant et ne se tourne plus dans une autre direction.
- **Morceaux de créatures visibles à travers les collines** — les vêtements d'un villageois, un objet tenu en main ou l'enveloppe d'un slime ne traversent plus les collines de Voxy ou de Distant Horizons quand le reste de la créature est caché (Minecraft 1.21.9 et plus, avec Distant Horizons, ou avec Voxy et un pack de shaders).

## 0.4.1 — 2026-10-11

### Corrections
- **Créatures lointaines trop sombres avec les shaders** — les créatures lointaines dessinées en images plates ne sont plus trop sombres avec les packs de shaders d'Iris comme Photon ou Complementary : le soleil les éclaire maintenant comme les vrais modèles.
- **Ombres du terrain sur les créatures lointaines** — les créatures lointaines ne portent plus l'ombre du terrain situé derrière elles, et l'eau de Voxy reste normale (Photon avec Voxy).
- **Créatures lointaines visibles à travers les collines** — les créatures lointaines ne traversent plus les collines de Voxy ou de Distant Horizons (Minecraft 26.1 ; Minecraft 26.2 et plus avec un pack de shaders, anciennes versions d'Iris comprises ; Minecraft 26.3 avec Iris installé).
- **Voxy 0.2.18 non reconnu** — le mod fonctionne de nouveau avec Voxy 0.2.18 : les créatures lointaines se tiennent sur son terrain et ne traversent plus ses collines.
- **Créatures lointaines cachées par les mauvaises collines** — les créatures lointaines ne sont plus cachées par des collines qui ne sont pas devant elles (Distant Horizons avec l'upscaling de Photon, TAAU).

## 0.4.0 — 2026-10-10

### Nouveautés
- **Premier scan automatique** — la première fois que tu entres dans un monde solo, les créatures dans un rayon de 32 chunks sont trouvées tout de suite, pour que l'horizon soit vivant dès le départ. Le rayon du scan se règle maintenant en chunks, comme la distance d'affichage.
- **Réglages d'animation** — deux nouveaux curseurs arrêtent les animations des créatures lointaines trop petites à l'écran ou trop loin, pour gagner en performances.
- **Préréglages de qualité** — Bas, Normal, Élevé et Ultra choisissent en un clic combien de créatures lointaines et d'oiseaux tu vois, et jusqu'où. Élevé et Ultra sont faits pour les ordinateurs puissants.

### Corrections
- **Créatures lointaines qui deviennent noires** — sans shaders, les créatures lointaines ne deviennent plus noires par moments, surtout en vol.

## 0.3.2 — 2026-10-09

### Corrections
- **Photon** — avec Voxy et l'upscaling de Photon (TAAU), les créatures lointaines se cachent de nouveau derrière les bonnes collines.

## 0.3.1 — 2026-10-08

### Corrections
- **Minecraft 1.20 à 1.21.4** — les créatures lointaines se cachent désormais derrière les collines de Voxy et de Distant Horizons, comme sur les versions récentes.
- **Anciennes versions de Distant Horizons** — fonctionne avec Distant Horizons 2.3 et plus, pas seulement la dernière version.

## 0.3.0 — 2026-10-07

### Nouveautés
- **Vulkan** — avec le moteur Vulkan du jeu, les créatures lointaines se cachent aussi derrière les collines, Distant Horizons compris.

### Corrections
- **Distant Horizons sans shaders** — les créatures lointaines ne disparaissent plus dans le terrain.
- **Distant Horizons** — plus de traînées là où s'arrête la distance d'affichage normale.

## 0.2.0 — 2026-10-07

### Nouveautés
- **Créatures lointaines** — animaux, villageois et golems restent vivants à l'horizon, au-delà de la distance d'affichage, à brouter et à se promener.
- **Joueurs lointains** — les autres joueurs continuent de marcher au loin, avec leur skin, leur armure et leur monture. Les joueurs déconnectés restent endormis là où ils sont partis.
- **Oiseaux** — nuées, oies en V, goélands sur la côte, chauves-souris la nuit.
- **Compatibilité Distant Horizons** — fonctionne avec Distant Horizons comme avec Voxy.
- **Cachées derrière les collines** — les créatures lointaines derrière une colline ne sont pas dessinées, pour garder le jeu fluide.
- **Imposteurs** (désactivés par défaut) — très loin, créatures et joueurs sont dessinés comme des images plates, bien plus légères quand ils sont nombreux.
- **Scanner les chunks autour** — en solo, les créatures des chunks autour de toi apparaissent tout de suite à l'horizon.
- **Réglages** — un bouton « Living Horizon... » dans le menu Options, avec le monde visible derrière, et un bouton pour revenir aux valeurs par défaut.
- **Quilt, NeoForge et Forge** — le mod fonctionne aussi sur Quilt, NeoForge et Forge, de Minecraft 1.20 à 26.3.
- **Un logo** dans la liste des mods et celle des data packs.
