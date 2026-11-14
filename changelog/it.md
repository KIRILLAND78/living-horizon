# Living Horizon — Registro delle modifiche

## Prossima versione

## 0.16.0 — 2026-11-05

### Client

#### Novità
- **Supporto per il terreno di Lodistance** — Le creature e i giocatori lontani ora si nascondono dietro il terreno lontano di Lodistance come dietro qualsiasi collina e si fondono nella sua nebbia, e le creature lontane camminano sul suo suolo, come avviene con Voxy e Distant Horizons. (Minecraft 26.3, con OpenGL, senza shader.)
- **Supporto per il terreno di Far View** — Le creature lontane ora compaiono e camminano sul terreno lontano di Far View e si nascondono dietro le sue colline, invece di sparire oltre la distanza di visibilità. (Forge e NeoForge, Minecraft da 1.20.1 a 1.21.4.)
- **Supporto per il terreno di Ecstatic** — Le creature lontane ora compaiono sul terreno di Ecstatic e camminano sul suo suolo, invece di sparire oltre la distanza di visibilità. Si nascondono correttamente dietro le sue colline, restano visibili davanti a esse e si fondono nella sua nebbia. (NeoForge, Minecraft 1.21.1, giocatore singolo; la nebbia senza shader.)
- **Creature semplificate illuminate dagli shader** — Con un pacchetto di shader che legge le normal map, come Photon con *Normal Mapping* attivo, le immagini semplificate delle creature lontane ora sono illuminate dal pacchetto come i loro modelli 3D, con un lato al sole e l'altro in ombra, invece di apparire piatte e più scure. Queste immagini occupano il doppio della memoria video; la nuova opzione *Illuminazione shader* permette di disattivarle. (Minecraft 1.21.6 e successive.)
- **Spingi da parte i giocatori disconnessi** — Ora puoi spingere via i giocatori disconnessi che ti sbarrano la strada camminandoci contro. Non ti spingono mai a loro volta, perché esistono solo nel tuo gioco.
- **I giocatori disconnessi vanno a pescare** — I giocatori disconnessi vicini all'acqua ora possono fermarsi sulla riva a pescare per un po'. Lungo uno stesso tratto di riva ne pesca solo uno alla volta.
- **I giocatori disconnessi si riparano dalla pioggia** — Quando piove, alcuni giocatori disconnessi ora vanno a mettersi sotto un tetto o un albero che riescono a raggiungere e aspettano lì finché non smette. Gli altri continuano come al solito.
- **I giocatori disconnessi si radunano accanto al fuoco** — La sera, i giocatori disconnessi ora vanno a mettersi davanti a un fuoco da campo o a una fornace che riescono a raggiungere, ciascuno al proprio posto.
- **I giocatori disconnessi chiacchierano tra loro** — Due giocatori disconnessi vicini ora possono avvicinarsi l'uno all'altro e chiacchierare per un po', faccia a faccia. Mai più di due alla volta, e una sola conversazione nella stessa zona.
- **I giocatori disconnessi ricambiano il tuo sguardo** — I giocatori disconnessi ora si girano a guardarti quando li osservi da vicino. Se uno è rivolto verso di te e ti accovacci, si accovaccia anche lui.

#### Miglioramenti
- **Notti più naturali per i giocatori disconnessi** — I giocatori disconnessi ora dormono in un letto che riescono a raggiungere, oppure restano in piedi, invece di sdraiarsi per terra.
- **Navi lontane più nitide** — Le immagini semplificate delle barche e dei veicoli alti, come i velieri di Shippy Ships, ora sono più nitide. Anche i più alti, come il galeone, ora ne hanno una, invece di essere sempre disegnati come modello completo, più pesante per le prestazioni.
- **Passaggio più fluido alle immagini semplificate** — Le creature e i giocatori lontani ora ruotano a poco a poco verso l'angolazione in cui li mostra la loro immagine semplificata, man mano che si avvicinano alla distanza in cui questa li sostituisce, invece di girarsi di scatto al momento del passaggio. Il cambio dal modello 3D all'immagine si nota meno.
- **Versioni NeoForge per 1.21.4 e 1.21.11 più facili da trovare** — Le versioni NeoForge per Minecraft 1.21.4 e 1.21.11 ora sono pubblicate come beta. Ora compaiono su CurseForge e nei launcher che nascondono le versioni alpha.

#### Correzioni
- **Creature mostrate una sola volta con Far View** — Le creature che Far View disegna come scatole tra 128 blocchi e la distanza di visibilità ora sono mostrate una sola volta, invece di due volte sovrapposte. (Forge e NeoForge, Minecraft da 1.20.1 a 1.21.4.)
- **Nomi dei giocatori disconnessi visibili su Forge** — Mirando un giocatore disconnesso ora ne vedi il nome e quando è stato visto l'ultima volta, invece di non vedere nulla. (Forge, Minecraft da 1.20.1 a 1.20.4.)
- **Migliore integrazione con Bobby** — Le creature lontane ora restano visibili appena oltre la distanza fino a cui il server le invia, sul terreno che Bobby conserva, invece di sparire. Anche i giocatori lontani non si fermano più di colpo quando escono da quella distanza.
- **Il galeone di Shippy Ships visibile da lontano** — Il galeone di Shippy Ships ora resta visibile in lontananza dove l'hai lasciato, come le altre navi della mod, invece di sparire appena esce dalla distanza di visibilità.
- **I giocatori disconnessi continuano a muoversi in lontananza** — Quando ti allontani, i giocatori disconnessi ora continuano a passeggiare proprio dove si trovano, invece di tornare dove si sono disconnessi o di restare perfettamente immobili. Dove non c'è spazio per una passeggiata, fanno qualche passo, si girano o si siedono per un po'.

## 0.15.0 — 2026-10-31

### Server

#### Novità
- **Creature nelle terre inesplorate** — Living Horizon ora mostra ai giocatori di un server le creature che il mondo genererebbe nelle regioni che nessuno ha ancora esplorato, fino a 512 blocchi attorno a ciascuno di loro per impostazione predefinita. Le loro posizioni sono stimate dal seme: potrebbero essere diverse, o non esserci più, quando ci arrivi. Questa funzione può essere disattivata, o il suo raggio modificato, nelle impostazioni del server.
- **Condivisione del seme** — I server ora possono lasciare che Living Horizon condivida il seme del mondo con i giocatori che usano la mod, così il loro gioco può stimare da solo le creature ovunque, con un cannocchiale o con *Indovina creature*. Questa opzione è disattivata per impostazione predefinita, perché conoscere il seme rivela tutto del mondo.

#### Miglioramenti
- **Volo con le elitre visibile da lontano** — Il server ora condivide quali giocatori stanno volando con le elitre, così vengono mostrati correttamente in planata anche a grande distanza.

### Client

#### Novità
- **Collegamenti rapidi a Living Horizon** — Le impostazioni della mod ora offrono scorciatoie verso Modrinth, CurseForge, Nexus Mods e il sito ufficiale, accanto a Discord.
- **Creature nelle zone inesplorate** — Le regioni che nessuno ha ancora esplorato non sono più vuote: Living Horizon stima dal seme quali creature il mondo vi genererebbe, fin dove arriva *Visibilità creature*. Trattandosi solo di stime, potrebbero essere diverse o non esserci più quando ci arrivi. Disponibile in giocatore singolo e sui server con la mod; *Creature indovinate* permette di nasconderle.
- **Creature stimate con il cannocchiale** — Guardando attraverso un cannocchiale ora vedi le creature stimate delle zone inesplorate ovunque punti, a qualsiasi distanza. Su un server, il server deve condividere il suo seme.
- **Stima delle creature attorno a te** — Il nuovo pulsante *Indovina creature* mostra subito le creature stimate fino a 1.024 chunk attorno a te e le conserva per le sessioni successive. Su un server, il server deve condividere il suo seme.
- **Valori digitati** — I cursori delle impostazioni della mod che terminano con *Illimitato* (numeri massimi e distanze di visibilità) ora accettano un valore digitato da tastiera, anche oltre il loro ultimo passo, e la pagina di Sodium lo mostra a sua volta. Fai clic destro su un cursore per inserire un valore esatto; anche i tasti freccia spostano ogni cursore passo dopo passo.
- **Anteprima delle distanze nel mondo** — Quando passi sopra un'opzione di distanza o la modifichi, il suo limite ora viene mostrato direttamente nel mondo, dietro il menu. Puoi vedere a colpo d'occhio fin dove arriva il valore scelto.

#### Miglioramenti
- **Distanza di semplificazione facoltativa** — *Distanza di semplificazione* ora può essere disattivata. Creature, entità, giocatori e uccelli diventano allora immagini semplificate solo quando appaiono abbastanza piccoli sullo schermo.
- **Fino a 4.000 creature lontane** — *Limite creature* ora arriva fino a 4.000 prima di *Illimitato*.
- **Illimitata alla fine del cursore** — *Illimitata* ora si trova all'estremità destra dei cursori della distanza di visibilità, dopo 4.000 blocchi, invece che a sinistra. I cursori ora vanno dalla distanza più corta alla più lunga.
- **Conferma prima di Illimitato** — Il gioco ora chiede una conferma prima di impostare su *Illimitato* un numero di creature, entità o giocatori disconnessi, o una distanza di visibilità. Questi valori possono abbassare di molto il frame rate, o persino far arrestare il gioco. Nella pagina di Sodium, la domanda arriva con *Applica*.
- **Limiti predefiniti più ragionevoli** — Nessuna opzione è più illimitata per impostazione predefinita, per evitare forti cali di prestazioni: *Visibilità giocatori*, *Distanza animazioni* e *Visibilità entità* si fermano a 4.000 blocchi. I preset *Alta* e *Ultra* mostrano al massimo 1.500 e 3.000 creature, *Ultra* fino a 2.048 blocchi, e le opzioni rimaste sui vecchi valori predefiniti passano ai nuovi.
- **Volo con le elitre più naturale** — I giocatori lontani che volano con le elitre ora sono mostrati con le ali spiegate in una vera posa di volo, invece di correre in piedi nell'aria.
- **Cambi di pacchetti di risorse più fluidi** — Le immagini semplificate delle creature non vengono più ricreate inutilmente quando un pacchetto di risorse viene disattivato e riattivato. Inoltre il caricamento delle risorse non blocca più il gioco mentre vengono cercate.
- **Creature volanti di Ice and Fire visibili da ogni angolazione** — I draghi, gli anfiteri e gli ippogrifi di Ice and Fire ora hanno immagini semplificate viste dal basso e dall'alto, come i fantasmi e gli aerei, invece che solo di lato. Appaiono corretti quando volano sopra di te o quando li guardi dall'alto.
- **Creature enormi più nitide in lontananza** — Le creature molto grandi ora mantengono il loro modello 3D completo finché appaiono ancora grandi sullo schermo, invece di trasformarsi in un'immagine semplificata squadrata.

#### Correzioni
- **Creature lontane visibili sul terreno lontano** — Le creature lontane, comprese quelle stimate, ora restano visibili sul terreno lontano, invece di essere nascoste come se sotto di loro non ci fosse nulla: per un po' dopo l'apertura di un mondo, tra migliaia di creature stimate, o quando Voxy o Distant Horizons tardano a rispondere.
- **Nebbia di Distant Horizons sulle creature** — Le creature lontane ora ricevono la stessa nebbia del terreno di Distant Horizons e sfumano con esso, invece di risaltare nettamente sul paesaggio. (Senza shader.)
- **Creature di Ice and Fire semplificate correttamente** — I draghi lontani di Ice and Fire ora hanno immagini semplificate intere e alla loro vera dimensione, invece di un primo piano delle zampe o di un'ala. I draghi di età diverse non condividono più un'unica immagine, e i serpenti marini mantengono il loro modello 3D invece di un'immagine tagliata.
- **I giocatori disconnessi conservano la loro barca** — Un giocatore che si è disconnesso in mezzo all'acqua non perde più la barca in cui riposa quando ti allontani: la conserva da vicino e da lontano, e anche la prossima volta che giochi.
- **I giocatori disconnessi non fluttuano più in aria** — Un giocatore che si è disconnesso in aria, planando con le elitre o cadendo, non torna più lassù quando ti allontani: riposa sul terreno sotto di lui. Con Voxy o Distant Horizons è a terra ancora prima che ti avvicini.
- **Le creature lontane riposano in modo naturale** — Le creature lontane oltre la *Distanza animazioni*, o più piccole della *Dim. min. animazione*, ora restano ferme dove sono, invece di scivolare avanti e indietro con le zampe immobili e tremolando. Riprendono a camminare da dove si erano fermate quando sono di nuovo abbastanza vicine.

## 0.14.0 — 2026-10-29

### Client

#### Novità
- **Veicoli di altre mod visibili da lontano** — I veicoli di altre mod, come gli aerei di Immersive Aircraft, ora restano visibili oltre la distanza di visibilità dove sono stati lasciati, proprio come le barche. I veicoli su cui viaggi senza guidarli, come i vagonetti, sono disattivati per impostazione predefinita e possono essere attivati in *Creature ed entità...*.
- **Impostazioni dedicate alle entità** — Barche, veicoli e manichini ora hanno impostazioni proprie, separate da quelle delle creature: se vengono mostrati, quanti e fino a che distanza. La loro distanza di visibilità è illimitata per impostazione predefinita.
- **Semplificazione separata per creature ed entità** — *Entità semplificate* ora è diventata due opzioni: *Creature semplificate* ed *Entità semplificate*. Anche barche, veicoli e manichini ora possono diventare immagini semplificate in lontananza, anche quando visti dall'alto o dal basso. *Giocatori semplificati* ora è indipendente, e la schermata delle immagini ora si chiama *Anteprima immagini...*, con una scheda per le entità.
- **Changelog nel gioco** — Un nuovo pulsante nelle impostazioni della mod mostra cosa è cambiato in ogni versione, senza uscire dal gioco. È disponibile anche nella pagina di Sodium.

#### Miglioramenti
- **Avviso sui server incompatibili** — Quando entri in un server senza Living Horizon, o con una versione troppo vecchia o troppo recente, un messaggio in chat ora ti avvisa che lì i giocatori lontani non saranno mostrati. Il messaggio non compare se *Mostra giocatori* è disattivato.
- **Creature ed entità in due schede** — La schermata *Creature ed entità...* ora divide le creature e le altre entità in due schede, per trovarle più facilmente. Lì barche e veicoli sono semplicemente mostrati o nascosti.
- **Scelte sulle creature applicate subito** — Mostrare o nascondere un tipo in *Creature ed entità...* ora ha effetto immediato nel mondo dietro il menu, senza doverlo chiudere.
- **Impostazioni delle creature meglio organizzate** — Le impostazioni di *Creature ed entità lontane* ora sono divise in tre parti: *Generali*, *Creature* ed *Entità*.
- **Cavalieri mostrati più fedelmente da lontano** — Con *Giocatori semplificati*, un giocatore lontano in barca, in vagonetto o in aereo, o in groppa a un ghast allegro, ora viene mostrato correttamente dall'alto e dal basso, invece di apparire sempre di profilo.
- **Aerei che restano in volo** — Un giocatore che si disconnette in pieno volo ora resta in aria nel suo aereo, che si libra sul posto o vola in cerchio a seconda che fosse fermo o in movimento.
- **Origine delle immagini indicata** — La schermata *Anteprima immagini* ora indica da dove proviene ogni immagine (condivisa, riutilizzata da una sessione precedente, nuova o non salvata), insieme a quando e in quale mondo è stata creata.
- **Gli animali domestici restano seduti** — Gatti, lupi e pappagalli a cui è stato ordinato di sedersi ora restano seduti in lontananza, invece di alzarsi e andarsene in giro.

#### Correzioni
- **Arresto anomalo di Creature ed entità risolto** — Aprire *Creature ed entità...* dalla schermata del titolo non fa più arrestare il gioco. (Minecraft 26.x.)
- **I veicoli di altre mod conservano i loro colori** — I veicoli e le creature lontani di alcune mod, come gli aerei di Immersive Aircraft, ora conservano i loro colori mentre ti avvicini, invece di diventare neri per un istante.
- **Cavalcature mostrate una sola volta** — La cavalcatura o il veicolo di un giocatore lontano, come un aereo di un'altra mod, non compare più una seconda volta accanto a lui.

## 0.13.0 — 2026-10-28

### Client

#### Miglioramenti
- **Gioco più fluido con le creature lontane** — Living Horizon ora calcola in background dove possono camminare le creature lontane e i giocatori disconnessi, senza rallentare il gioco.
- **Rendering più fluido con Voxy e Distant Horizons** — A ogni fotogramma il gioco attende meno il driver grafico quando le creature lontane sono mostrate sul terreno di Voxy o di Distant Horizons. Il guadagno è maggiore sulle schede NVIDIA con *Ottimizzazione threaded* attiva.
- **Fantasmi e ghast in volo in lontananza** — I fantasmi lontani ora volano in cerchio nel cielo e i ghast fluttuano lentamente, invece di restare sospesi immobili nell'aria.
- **Fantasmi e ghast visti dal basso** — Come immagini semplificate, i fantasmi e i ghast ora sono mostrati anche dal basso e dall'alto, come gli uccelli, e non solo di lato.
- **Più fluido con molte creature lontane** — Il gioco non spreca più tempo su ogni creatura lontana a ogni fotogramma. (Vulkan su Minecraft 26.2 e successive, Distant Horizons su Minecraft 26.3.)
- **Ogni creatura visibile in lontananza** — Mostri, pipistrelli, pesci e tutte le altre creature ora sono mostrati in lontananza per impostazione predefinita e vagano come gli animali. Gli Enderman, per esempio, restano visibili in tutto l'End. Gli animali che avevi nascosto restano nascosti.

#### Correzioni
- **Pulsanti delle impostazioni di nuovo in ordine** — I pulsanti in fondo a *Creature ed entità lontane* non si sovrappongono più, e *Ripristina predefiniti* è tornato a sinistra di *Fatto*. (Minecraft 1.20.1.)
- **Blocco al cambio di dimensione risolto** — Il gioco non si blocca più definitivamente quando attraversi un portale o esci da un mondo. (Con Distant Horizons.)

## 0.12.1 — 2026-10-27

### Client

#### Correzioni
- **Comando di benchmark riservato ai test** — `/livinghorizon benchmark` non viene più proposto ai giocatori: serve solo per testare la mod.

## 0.12.0 — 2026-10-23

### Server

#### Novità
- **Impostazioni del server** — I proprietari dei server ora possono scegliere cosa condivide Living Horizon: giocatori online, giocatori disconnessi e creature, fino a che distanza, solo tra compagni di squadra se lo desiderano, e quali giocatori o dimensioni non vengono mai condivisi. Le impostazioni si trovano in `config/livinghorizon-server.json` e si applicano in pochi secondi, senza riavvio.

#### Correzioni
- **Accesso ai server Forge risolto** — I giocatori con la mod non vengono più espulsi da un server Forge che la usa a sua volta, con il messaggio "mismatched mod channel list". (Forge 1.20.2 e successive.)
- **Spettatori e giocatori invisibili restano nascosti** — I giocatori in modalità spettatore o sotto l'effetto dell'invisibilità non sono più visibili agli altri in lontananza, né dove si sono disconnessi.

### Client

#### Novità
- **Pulsante Discord** — Le impostazioni della mod ora ti permettono di unirti al Discord di Living Horizon con un clic, per fare una domanda o segnalare un problema. Il pulsante è disponibile anche nella pagina di Sodium.
- **Versione della mod visibile** — Le schermate delle impostazioni e il pannello di debug ora mostrano la versione di Living Horizon, utile da indicare in una segnalazione di bug.
- **Dimensione di semplificazione** — Una nuova opzione trasforma le creature, i giocatori e gli uccelli lontani in immagini semplificate non appena appaiono sullo schermo più piccoli di una certa dimensione (10 pixel per impostazione predefinita), anche prima della *Distanza di semplificazione*. Ciò che si vede appena diventa molto più leggero da disegnare.

#### Miglioramenti
- **Più fluido con centinaia di creature lontane** — *Salta creature nascoste* costa molto meno con molte creature e molti giocatori lontani sullo schermo, e Living Horizon lavora meno dietro le quinte.
- **Versioni per Minecraft 1.21.1 più facili da trovare** — Le versioni Fabric, Quilt e NeoForge per Minecraft 1.21 e 1.21.1 ora sono pubblicate come beta. Ora compaiono su CurseForge e nei launcher che nascondono le versioni alpha.
- **Meno scatti con le folle in lontananza** — Le creature, i giocatori e gli uccelli lontani ora chiedono al gioco molta meno memoria a ogni fotogramma, il che riduce gli scatti.
- **Più fluido con molti uccelli** — Gli uccelli alle tue spalle o troppo piccoli per essere visti non vengono più disegnati.

#### Correzioni
- **Creature lontane sempre su un terreno solido** — Le creature lontane non vengono più disegnate dove sotto di loro non è ancora visibile alcun terreno, che sia quello del gioco, di Voxy o di Distant Horizons. Non fluttuano più nel vuoto.
- **I mondi in giocatore singolo si aprono di nuovo su Forge** — Un mondo in giocatore singolo non si chiude più appena aperto con il messaggio "mismatched mod channel list". (Forge 1.20.2 e successive.)
- **Le creature lontane restano sul terreno di Distant Horizons** — In giocatore singolo, le creature lontane non spariscono più e non smettono più di camminare sulle colline e sulle montagne di Distant Horizons.

## 0.11.0 — 2026-10-22

### Server

#### Miglioramenti
- **Più leggero sui server grandi** — Il server impiega meno tempo a scegliere quali skin e cavalcature inviare a ciascun giocatore. Le lunghe liste di giocatori disconnessi ora vengono inviate in più parti, invece che in un unico messaggio troppo grande per il gioco.
- **Aggiornamento necessario per i giocatori** — Su un server con questa versione, anche i giocatori ne hanno bisogno per vedere i giocatori lontani.

#### Correzioni
- **File dei giocatori disconnessi protetto** — Se il server non riesce a leggere `livinghorizon-positions.dat`, il file non viene più sovrascritto: viene conservato come `livinghorizon-positions.dat.bad` e la console lo segnala.

### Client

#### Novità
- **Benchmark** — Il comando `/livinghorizon benchmark` misura quanto costa ogni parte della mod nel tuo mondo e produce un resoconto da allegare a una segnalazione di bug.
- **Mondo di benchmark** — `/livinghorizon benchmark world` crea un mondo di prova sempre identico, pieno di creature e giocatori disconnessi, così le misurazioni possono essere confrontate da un'esecuzione all'altra. Il tuo mondo resta intatto.
- **Limite disconnessi** — Una nuova opzione limita quanti giocatori disconnessi vengono mostrati contemporaneamente, a partire dai più vicini (256 per impostazione predefinita). Gli altri ricompaiono man mano che ti avvicini.

#### Miglioramenti
- **Messaggio più chiaro quando le versioni differiscono** — Quando la mod sul server è troppo vecchia o troppo recente rispetto alla tua, il registro ora indica quale aggiornare, invece di affermare che il server non ha la mod.
- **Più fluido con molti giocatori disconnessi** — I giocatori disconnessi costano meno da mostrare in lontananza, e quelli nascosti dietro le colline non vengono più disegnati.

#### Correzioni
- **Visibilità giocatori rispettata** — I giocatori disconnessi non vengono più mostrati oltre la *Visibilità giocatori*.
- **Manichini lontani con la loro vera skin** — I manichini lontani ora mantengono la skin che indossano da vicino, che sia quella di un giocatore o di un pacchetto di risorse, invece di trasformarsi in Alex. (Minecraft 1.21.9 e successive.)
- **Acqua preservata sotto Photon con Distant Horizons** — Con *Nascondi dietro i rilievi* attivo, l'acqua ora resta normale quando una creatura o un giocatore lontano è in vista, invece di rivelarne il fondale nudo e in ombra. (Minecraft 26.3.)
- **I mantelli dei manichini ricadono in modo naturale** — I mantelli dei manichini lontani ora ricadono verso il basso come da vicino, invece di restare dritti in aria. (Minecraft 1.21.9 e successive.)
- **File delle impostazioni protetto** — Un errore commesso modificando a mano `livinghorizon.json` non cancella più il file: viene conservato come `livinghorizon.json.bad` e il registro lo segnala.
- **Salvataggi protetti** — Se il gioco si chiude durante un salvataggio, i file delle creature ricordate (quelle che Living Horizon continua a mostrare in lontananza) e dei giocatori disconnessi non vengono più danneggiati. Un file danneggiato viene conservato come `.bad` invece di essere svuotato.
- **Accesso più fluido ai server grandi** — Entrare in un server con molti giocatori disconnessi non provoca più scatti nel gioco.
- **Immagini danneggiate ricreate automaticamente** — Un'immagine semplificata salvata e danneggiata non può più far esaurire la memoria al gioco: viene semplicemente ricreata.
- **Creature lontane visibili davanti al terreno di Distant Horizons con Oculus** — Con Oculus e un pacchetto di shader, le creature lontane non spariscono più dietro il terreno di Distant Horizons, un problema che peggiorava con l'aumentare della distanza di visibilità. (Forge.)
- **Movimenti della testa naturali con Entity Culling** — Le creature e i giocatori lontani non scuotono più la testa velocissimamente con Entity Culling, da solo o con Chloride.
- **Giocatori nella giusta dimensione delle mod** — I giocatori online e disconnessi in una dimensione aggiunta da una mod, come l'Aether, non vengono più mostrati in un'altra, come la Twilight Forest. Su un server, anche il server ha bisogno di questa versione.
- **Nebbia di Voxy sulle creature** — Le creature lontane sul terreno di Voxy ora ricevono la stessa nebbia del paesaggio circostante, invece di sfumare nel colore della nebbia oltre una certa distanza. (Senza shader.)

## 0.10.0 — 2026-10-20

### Server

#### Novità
- **Living Horizon sui server** — Ora la mod può essere installata anche sul server: metti il jar nella sua cartella `mods` (Fabric, Quilt, NeoForge o Forge, più la Fabric API su Fabric e Quilt). Non serve alcuna configurazione, e sostituisce il pacchetto di dati.
- **Giocatori condivisi in tempo reale** — Il server invia la posizione di ogni giocatore cinque volte al secondo ai giocatori che usano la mod, con la loro skin, l'armatura, gli oggetti in mano e la cavalcatura.
- **Creature condivise** — Il server invia le creature entro 2.048 blocchi da ogni giocatore, anche quelle che quel giocatore non ha mai incontrato: animali, villici, golem, allevianti e barche, con la loro sella, imbracatura, armatura o baule.
- **Giocatori disconnessi condivisi** — Il server ricorda dove ogni giocatore si è disconnesso, il suo aspetto, la sua cavalcatura e quando se n'è andato, e lo trasmette a tutti, compresi i giocatori che entrano dopo.
- **Giocatori di prima dell'installazione** — Al primo avvio, il server trova i giocatori che il mondo conosceva già e li mostra come giocatori disconnessi dove se ne sono andati, con il loro equipaggiamento.
- **Salvato con il mondo** — Tutto viene salvato in `livinghorizon-positions.dat`, nella cartella del mondo. Una creatura viene dimenticata quando il suo chunk viene caricato e lei non c'è più.
- **Mondi in LAN** — Un mondo aperto in LAN condivide tutto attraverso il gioco di chi lo ospita, senza nient'altro da installare.
- **Giocatori senza la mod benvenuti** — I giocatori senza la mod possono comunque entrare; il server semplicemente non invia loro nulla.
- **Stessa versione da entrambe le parti** — Tieni la mod aggiornata sia sul server sia nel gioco dei giocatori: un gioco che non riesce a capire i messaggi del server li ignora.
- **Privacy: le posizioni sono condivise** — Il server condivide la posizione di ogni giocatore con tutti i giocatori che usano la mod, a qualsiasi distanza. Assicurati di informarne i tuoi giocatori.
- **Niente più pacchetto di dati** — Il pacchetto di dati non viene più usato: rimuovilo dalla cartella `datapacks` del mondo. Le coordinate non passano più per il tabellone dei punteggi, quindi non compaiono più nei punteggi né nella barra laterale.

#### Correzioni
- **Giocatori conservati alla chiusura di un mondo** — Quando un server o un mondo in LAN si chiude, i giocatori ancora presenti ora restano dov'erano come giocatori disconnessi, invece di sparire.

### Client

#### Novità
- **Ogni giocatore, a qualsiasi distanza** — Su un server con la mod, ora vedi tutti gli altri giocatori, ovunque si trovino, con la loro skin, l'armatura, gli oggetti in mano e la cavalcatura.
- **Creature mai incontrate** — Su un server con la mod, le creature lontane compaiono subito, anche quelle a cui non ti sei mai avvicinato, con la loro sella, imbracatura, armatura o baule.
- **Giocatori disconnessi visibili appena entri** — Vedi i giocatori disconnessi dove se ne sono andati, con il loro aspetto e la loro cavalcatura, anche se se ne sono andati prima del tuo arrivo.
- **Giocatori disconnessi in giocatore singolo** — In giocatore singolo, i giocatori disconnessi del tuo mondo compaiono appena lo apri, compresi quelli di prima dell'installazione della mod.
- **Un orizzonte vivo fin dall'inizio** — Le creature lontane e i giocatori disconnessi compaiono appena entri, non qualche secondo dopo.
- **Server senza la mod** — Puoi comunque entrarci. Lì vengono mostrate solo le creature che hai già incontrato, senza giocatori lontani, e il registro lo segnala una volta.

#### Miglioramenti
- **Posizioni dei giocatori lontani più affidabili** — I giocatori lontani ora vengono posizionati solo in base alle posizioni condivise dal server. La stima tramite la barra localizzatrice non c'è più, così come l'opzione *Tempo giocatore perso*.

#### Correzioni
- **Ghast allegri mostrati correttamente** — Un giocatore lontano in groppa a un ghast allegro non ha più un secondo ghast accanto, e chi stava già cavalcando quando sei entrato non fluttua più a mezz'aria.
- **I ghast allegri restano dove sono stati lasciati** — Quando un giocatore lontano scende dal suo ghast allegro, questo ora resta dove è stato lasciato, invece di ricomparire dove è stato cavalcato la prima volta e tornare indietro in volo.
- **Nuove skin visibili da lontano** — Un giocatore lontano che cambia skin ora compare con quella nuova, invece di mantenere quella vecchia in lontananza.
- **Giocatori seduti correttamente in barca** — Un giocatore che si disconnette in barca ora è rivolto nella stessa direzione della barca, invece di sedere di traverso.
- **Cavalieri seduti correttamente** — Un giocatore lontano a cavallo ora siede correttamente, come modello completo o come immagine semplificata, invece di sprofondare nel cavallo o di sedersi sulla sua groppa.
- **Un solo cavallo dopo la disconnessione** — Un giocatore che si disconnette a cavallo non lascia più una copia del cavallo accanto a quello su cui siede.
- **Pulsanti Website e Issues funzionanti** — I pulsanti Website e Issues nella pagina della mod in Mod Menu non sono più disattivati. (Fabric.)

## 0.9.0 — 2026-10-19

### Correzioni
- **Giocatori disconnessi fuori dall'acqua** — I giocatori che si sono disconnessi mentre nuotavano non restano più in piedi nell'acqua: aspettano sulla riva più vicina, oppure remano in barca se la riva è a più di 10 blocchi.
- **Acqua intatta attorno alle barche** — L'acqua non diventa più trasparente attorno ai giocatori disconnessi che remano in barca con un pacchetto di shader come Photon, e non scompare più in un grande quadrato attorno alle barche lontane.
- **I rematori conservano la loro barca** — In lontananza, i giocatori disconnessi che remano in barca ora conservano la barca mentre viene creata la loro immagine semplificata, invece di sembrare in piedi senza di essa.

## 0.8.0 — 2026-10-18

### Novità
- **Giocatori semplificati** — Una nuova opzione ti permette di mantenere i giocatori lontani come modelli 3D completi, mentre le creature lontane diventano comunque immagini semplificate.
- **12 nuove lingue** — Living Horizon è ora disponibile in tedesco, spagnolo, italiano, portoghese brasiliano, polacco, turco, russo, ucraino, cinese semplificato e tradizionale, giapponese e coreano.
- **Impostazioni in Sodium** — Le impostazioni di Living Horizon sono ora raggiungibili dalle *Impostazioni grafiche* di Sodium, in pagine proprie. (Sodium 0.8 e successive.)
- **Giocatori disconnessi con la loro cavalcatura** — Un giocatore che si è disconnesso a cavallo, in barca o in groppa a un ghast allegro continua a cavalcarlo nei dintorni del punto in cui se n'è andato.
- **Controllo della memoria video** — La schermata *Entità semplificate...* mostra quanta memoria video usano le immagini delle creature e dei giocatori, e due nuove opzioni fissano un limite per ciascuna.

### Miglioramenti
- **Impostazioni più chiare** — Ogni opzione ora ha un nome e una descrizione più chiari, nello stile delle opzioni di Minecraft, e i cursori delle distanze occupano tutta la larghezza dello schermo.
- **Giocatori disconnessi più vivaci** — I giocatori disconnessi ora percorrono un piccolo giro attorno al punto in cui se ne sono andati. In lontananza diventano immagini semplificate come i giocatori online, che siano in piedi, seduti, sdraiati o a letto.
- **Cavalieri più leggeri** — In lontananza, un giocatore a cavallo, in barca, in vagonetto o su qualsiasi altra cavalcatura ora forma con essa un'unica immagine semplificata, molto più leggera di due modelli completi.
- **Grandi folle di giocatori più fluide** — Oltre un centinaio di giocatori lontani, quelli in più ora diventano anch'essi immagini semplificate invece di restare modelli completi.

### Correzioni
- **Creature nascoste correttamente dopo il ridimensionamento** — Le creature lontane non si vedono più attraverso le colline, comprese quelle di Voxy e di Distant Horizons, dopo il ridimensionamento della finestra del gioco.
- **Giocatori e gelatine visibili con Distant Horizons** — I giocatori lontani, i giocatori disconnessi e le gelatine non spariscono più sotto il terreno di Distant Horizons. (Minecraft 26.1 e successive.)
- **Creature intere davanti al terreno di Distant Horizons** — Il terreno di Distant Horizons non taglia più le creature e i giocatori che si trovano davanti a esso. (Minecraft 26.1 e successive.)
- **Creature stabili con la visuale oscillante** — Con la visuale oscillante attiva, le creature lontane non sfarfallano più e il terreno di Distant Horizons non ondeggia più davanti a loro a ogni passo. (Minecraft 26.1 e successive.)

## 0.7.0 — 2026-10-17

### Novità
- **I giocatori disconnessi prendono vita** — I giocatori disconnessi ora vivono nei dintorni del punto in cui se ne sono andati: passeggiano, si siedono, osservano bauli e banchi da lavoro, vanno a letto la notte e alzano lo scudo quando vengono colpiti o quando si avvicinano dei mostri. Restano sempre a terra: cadono se rompi il blocco sotto di loro e risalgono se li seppellisci.
- **Etichetta "(non in linea)"** — I giocatori disconnessi mostrano *(non in linea)* dopo il loro nome, e guardandone uno da vicino scopri quando è stato visto l'ultima volta. I loro nomi, o i giocatori disconnessi stessi, possono essere nascosti nelle impostazioni.
- **Uccelli in 3D** — Gli uccelli ora sono piccoli modelli 3D come le creature di Minecraft: sbattono le ali, stanno in piedi sulle zampe quando sono appollaiati e diventano immagini semplificate in lontananza. L'opzione *Stile uccelli* riporta i vecchi uccelli in 2D.
- **Mmm…** — Mi è sembrato di vedere qualcosa nel cielo. 😉

### Miglioramenti
- **Creature che saltellano** — Le gelatine, i cubi di magma e i conigli lontani ora si spostano saltellando invece di scivolare. Le gelatine si schiacciano quando atterrano, e le galline sbattono le ali quando scendono di un blocco.
- **Anatre più naturali** — Le anatre ora si alzano in volo insieme quando ti avvicini e atterrano più avanti sull'acqua, invece di andarsene per sempre.

### Correzioni
- **Creature animate attraverso il cannocchiale** — Le creature lontane viste attraverso un cannocchiale ora contano alla loro dimensione ingrandita e continuano a camminare quando appaiono grandi, invece di bloccarsi. (Opzione *Dim. min. animazione*.)
- **Creature animate all'apertura di un mondo** — Le creature lontane ora camminano e pascolano fin dall'inizio, invece di girare su sé stesse.
- **Uccelli appollaiati più naturali** — Cince, pettirossi e anatre non schizzano più avanti e indietro, non fluttuano più in aria, non stanno più dentro le foglie e non nuotano più sulla terraferma. Le anatre non compaiono più proprio accanto a te solo per volare via e ora nuotano nel loro stagno, e pettirossi e cince saltellano di più e volano via di meno.
- **Licenza corretta nell'elenco delle mod** — Living Horizon ora mostra la licenza corretta nell'elenco delle mod. (Forge, NeoForge e Quilt.)

## 0.6.0 — 2026-10-15

### Novità
- **Immagini delle creature conservate** — Le immagini semplificate delle creature lontane ora vengono salvate su disco e riutilizzate da una sessione all'altra. Vengono ricreate solo dopo un cambio di pacchetti di risorse, di mod o di versione del gioco.

### Miglioramenti
- **Nuova icona** — Living Horizon ha una nuova icona in pixel art: Steve in primo piano, circondato da creature, villici, giocatori e uccelli fino alle montagne.
- **Creature lontane più coerenti quando torni** — In giocatore singolo, le creature lontane si teletrasportano o corrono verso un altro punto molto più di rado quando torni: seguono le creature reali finché il gioco continua a muoverle, e scompaiono quando queste muoiono.

### Correzioni
- **Schermata Entità semplificate senza sfarfallio** — Due creature con lo stesso nome, come villici con abiti diversi, non sfarfallano più scambiandosi di posto.
- **Immagini delle creature più pulite** — Le creature lontane mostrate come immagini semplificate non hanno più sottili linee scure o puntini neri lungo il bordo superiore o inferiore. Le creature lunghe come i cavalli e i fiutatori non vengono più tagliate all'altezza del muso o della coda.
- **Arresto anomalo con le creature GeckoLib risolto** — Un mondo non si arresta più in modo anomalo pochi secondi dopo il caricamento quando una creatura di una mod basata su GeckoLib è stata vista in lontananza. Queste creature non vengono ancora mostrate in lontananza. (Minecraft da 1.21.2 a 1.21.8.)
- **Entità semplificate più veloci** — Il gioco non rallenta più con le immagini semplificate: le stesse creature non vengono più ridisegnate continuamente, e le creature che si somigliano condividono un'unica immagine.

## 0.5.0 — 2026-10-13

### Novità
- **Entità semplificate su Minecraft da 1.20.1 a 1.21.8** — Anche su queste versioni le creature e i giocatori molto lontani ora possono essere mostrati come immagini semplificate, il che alleggerisce molto il lavoro del computer.

### Correzioni
- **Arresto anomalo all'avvio risolto** — Il gioco non si arresta più all'avvio con la mod installata. (Forge, Minecraft da 1.20.1 a 1.20.4.)
- **Arresto anomalo della schermata Entità semplificate risolto** — Aprire la schermata *Entità semplificate* non fa più arrestare il gioco. (Minecraft 1.21.8 e precedenti.)
- **Creature presenti appena entri** — Anche le creature che erano già attorno a te quando sei entrato in un mondo ora compaiono all'orizzonte, non solo quelle che incontri dopo.

## 0.4.2 — 2026-10-12

### Correzioni
- **Creature stabili mentre si allontanano** — Una creatura che si sposta oltre la tua distanza di visibilità non scompare più per un istante e non si gira più in un'altra direzione.
- **Creature completamente nascoste dalle colline** — I vestiti di un villico, un oggetto tenuto in mano o lo strato esterno di una gelatina non si vedono più attraverso le colline di Voxy o di Distant Horizons mentre il resto della creatura è nascosto. (Minecraft 1.21.9 e successive, con Distant Horizons, oppure con Voxy e shader.)

## 0.4.1 — 2026-10-11

### Correzioni
- **Creature illuminate correttamente con gli shader** — Le creature lontane mostrate come immagini semplificate ora sono illuminate dal sole come i modelli completi, invece di essere troppo scure con pacchetti di shader di Iris come Photon o Complementary.
- **Ombre del terreno corrette** — Le creature lontane non mostrano più l'ombra del terreno alle loro spalle, e l'acqua di Voxy appare come dovrebbe. (Photon con Voxy.)
- **Creature nascoste correttamente dalle colline** — Le creature lontane non si vedono più attraverso le colline di Voxy o di Distant Horizons. (Minecraft 26.1; Minecraft 26.2 e successive con shader, comprese le versioni meno recenti di Iris; Minecraft 26.3 con Iris installato.)
- **Supporto per Voxy 0.2.18** — La mod funziona di nuovo con Voxy 0.2.18: le creature lontane stanno sul suo terreno e non si vedono più attraverso le sue colline.
- **Creature nascoste dalle colline giuste con Distant Horizons** — Le creature lontane non vengono più nascoste da colline che non si trovano davanti a loro. (Distant Horizons con l'upscaling TAAU di Photon.)

## 0.4.0 — 2026-10-10

### Novità
- **Prima scansione automatica** — La prima volta che entri in un mondo in giocatore singolo, Living Horizon cerca subito le creature entro 32 chunk, così l'orizzonte è vivo fin dall'inizio. Il raggio di scansione ora si imposta in chunk, come la distanza di visibilità.
- **Opzioni di animazione** — Due nuovi cursori smettono di animare le creature lontane troppo piccole sullo schermo o troppo distanti, per migliorare le prestazioni.
- **Preset di qualità** — Bassa, Normale, Alta e Ultra impostano con un clic quante creature lontane e quanti uccelli vedi, e fino a che distanza. Alta e Ultra sono pensati per computer potenti.

### Correzioni
- **Le creature conservano i loro colori** — Senza shader, le creature lontane non diventano più nere per un istante, soprattutto in volo.

## 0.3.2 — 2026-10-09

### Correzioni
- **Creature nascoste dalle colline giuste con Photon** — Con Voxy e l'upscaling TAAU di Photon, le creature lontane vengono di nuovo nascoste dalle colline giuste.

## 0.3.1 — 2026-10-08

### Correzioni
- **Colline prese in considerazione sulle versioni meno recenti** — Le creature lontane ora si nascondono dietro le colline di Voxy e di Distant Horizons, come sulle versioni più recenti, invece di vedersi attraverso di esse. (Minecraft da 1.20 a 1.21.4.)
- **Supporto per le versioni meno recenti di Distant Horizons** — La mod ora funziona con Distant Horizons 2.3 e successive, non solo con l'ultima versione.

## 0.3.0 — 2026-10-07

### Novità
- **Supporto per Vulkan** — Con il renderer Vulkan del gioco, anche le creature lontane si nascondono dietro le colline, compreso il terreno di Distant Horizons.

### Correzioni
- **Creature visibili davanti al terreno di Distant Horizons** — Senza shader, le creature lontane non sfumano più nel terreno di Distant Horizons.
- **Niente più sbavature al limite della distanza di visibilità** — Le sbavature che comparivano dove finisce la normale distanza di visibilità sono sparite. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Novità
- **Creature lontane** — Animali, villici e golem restano vivi all'orizzonte oltre la tua distanza di visibilità, pascolando e vagando nei dintorni.
- **Giocatori lontani** — Gli altri giocatori continuano a camminare in lontananza, con la loro skin, l'armatura e la cavalcatura. I giocatori che si disconnettono restano addormentati dove se ne sono andati.
- **Uccelli** — Stormi, oche che volano a V, gabbiani sulla costa e pipistrelli di notte animano il cielo.
- **Supporto per Distant Horizons** — Living Horizon funziona con Distant Horizons oltre che con Voxy.
- **Creature nascoste dietro le colline** — Le creature lontane dietro una collina non vengono disegnate, il che mantiene il gioco fluido.
- **Immagini semplificate in lontananza** — Le creature e i giocatori molto lontani possono essere mostrati come immagini semplificate, molto più leggere da disegnare quando sono numerosi. Questa opzione è disattivata per impostazione predefinita.
- **Scansione dei dintorni** — In giocatore singolo, una scansione legge le creature dei chunk attorno a te, così compaiono subito all'orizzonte, senza doverle prima incontrare.
- **Schermata delle impostazioni** — Un pulsante *Living Horizon...* nel menu Opzioni apre le impostazioni della mod, con il mondo ancora visibile dietro, e un pulsante ripristina i valori predefiniti.
- **Quilt, NeoForge e Forge** — Living Horizon funziona anche su Quilt, NeoForge e Forge, da Minecraft 1.20 a 26.3.
- **Logo della mod** — Living Horizon ora ha un logo nell'elenco delle mod e in quello dei pacchetti di dati.
