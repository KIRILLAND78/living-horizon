# Living Horizon — Änderungsprotokoll

## Nächste Version

## 0.16.0 — 2026-11-05

### Client

#### Neu
- **Unterstützung für das Gelände von Lodistance** — Ferne Kreaturen und Spieler verstecken sich jetzt hinter dem fernen Gelände von Lodistance wie hinter jedem anderen Hügel und verschmelzen mit seinem Nebel, und ferne Kreaturen laufen auf seinem Boden, wie mit Voxy und Distant Horizons. (Minecraft 26.3, mit OpenGL, ohne Shader.)
- **Unterstützung für das Gelände von Far View** — Ferne Kreaturen erscheinen und laufen jetzt auf dem fernen Gelände von Far View und verstecken sich hinter seinen Hügeln, statt jenseits der Sichtweite ausgeblendet zu werden. (Forge und NeoForge, Minecraft 1.20.1 bis 1.21.4.)
- **Unterstützung für das Gelände von Ecstatic** — Ferne Kreaturen erscheinen jetzt auf dem Gelände von Ecstatic und laufen auf seinem Boden, statt jenseits der Sichtweite zu verschwinden. Sie verstecken sich korrekt hinter seinen Hügeln, bleiben davor sichtbar und verschmelzen mit seinem Nebel. (NeoForge, Minecraft 1.21.1, Einzelspieler; der Nebel ohne Shader.)
- **Vereinfachte Kreaturen im Licht der Shader** — Mit einem Shaderpaket, das Normal Maps liest, etwa Photon mit eingeschaltetem *Normal Mapping*, werden die vereinfachten Bilder ferner Kreaturen jetzt wie ihre 3D-Modelle vom Paket beleuchtet, eine Seite in der Sonne, die andere im Schatten, statt flach und dunkler zu wirken. Diese Bilder belegen doppelt so viel Grafikspeicher; die neue Einstellung *Shader-Beleuchtung* schaltet das ab. (Minecraft 1.21.6 und neuer.)
- **Offline-Spieler beiseiteschieben** — Du kannst Offline-Spieler jetzt aus dem Weg schieben, indem du in sie hineinläufst. Sie schieben dich nie zurück, da sie nur in deinem Spiel existieren.
- **Offline-Spieler gehen angeln** — Offline-Spieler in der Nähe von Wasser können jetzt am Ufer stehen bleiben und eine Weile angeln. Am selben Uferabschnitt angelt immer nur einer.
- **Offline-Spieler suchen Schutz vor dem Regen** — Wenn es regnet, stellen sich manche Offline-Spieler jetzt unter ein Dach oder einen Baum in ihrer Reichweite und warten dort, bis es aufhört. Die anderen machen weiter wie gewohnt.
- **Offline-Spieler versammeln sich am Feuer** — Abends stellen sich Offline-Spieler jetzt vor ein Lagerfeuer oder einen Ofen in ihrer Reichweite, jeder an einen eigenen Platz.
- **Offline-Spieler unterhalten sich** — Zwei Offline-Spieler, die nah beieinander sind, können jetzt aufeinander zugehen und sich eine Weile von Angesicht zu Angesicht unterhalten. Nie mehr als zwei auf einmal und nur ein Gespräch in derselben Gegend.
- **Offline-Spieler erwidern deinen Blick** — Offline-Spieler drehen sich jetzt zu dir um, wenn du sie aus der Nähe ansiehst. Schaut dich einer an und du schleichst, schleicht er ebenfalls.

#### Verbessert
- **Natürlichere Nächte für Offline-Spieler** — Offline-Spieler schlafen jetzt in einem Bett in ihrer Reichweite oder bleiben auf den Beinen, statt sich auf den Boden zu legen.
- **Schärfere ferne Schiffe** — Die vereinfachten Bilder hoher Boote und Fahrzeuge, etwa der Segelschiffe von Shippy Ships, sind jetzt schärfer. Auch die höchsten, wie die Galeone, bekommen jetzt eines, statt immer als vollständiges Modell gezeichnet zu werden, was mehr Leistung kostet.
- **Sanfterer Wechsel zu vereinfachten Bildern** — Ferne Kreaturen und Spieler drehen sich jetzt nach und nach in den Winkel, in dem ihr vereinfachtes Bild sie zeigt, während sie sich der Entfernung nähern, ab der es sie ersetzt, statt sich im Moment des Wechsels schlagartig zu drehen. Der Übergang vom 3D-Modell zum Bild fällt so weniger auf.
- **NeoForge-Builds für 1.21.4 und 1.21.11 leichter zu finden** — Die NeoForge-Builds für Minecraft 1.21.4 und 1.21.11 werden jetzt als Beta veröffentlicht. Sie erscheinen damit auf CurseForge und in Launchern, die Alpha-Versionen ausblenden.

#### Behoben
- **Kreaturen mit Far View nur einmal angezeigt** — Kreaturen, die Far View zwischen 128 Blöcken und der Sichtweite als Kästen zeichnet, werden jetzt nur einmal angezeigt, statt doppelt übereinander. (Forge und NeoForge, Minecraft 1.20.1 bis 1.21.4.)
- **Namen von Offline-Spielern auf Forge sichtbar** — Wenn du auf einen Offline-Spieler zielst, werden jetzt sein Name und der Zeitpunkt angezeigt, zu dem er zuletzt gesehen wurde, statt gar nichts. (Forge, Minecraft 1.20.1 bis 1.20.4.)
- **Bessere Zusammenarbeit mit Bobby** — Ferne Kreaturen bleiben jetzt auch knapp jenseits der Entfernung sichtbar, bis zu der der Server sie sendet, über dem Gelände, das Bobby speichert, statt zu verschwinden. Auch ferne Spieler bleiben nicht mehr zu früh stehen, wenn sie diese Entfernung verlassen.
- **Galeone von Shippy Ships aus der Ferne sichtbar** — Die Galeone aus Shippy Ships bleibt jetzt wie die anderen Schiffe der Mod dort in der Ferne sichtbar, wo du sie zurückgelassen hast, statt zu verschwinden, sobald sie die Sichtweite verlässt.
- **Offline-Spieler bewegen sich in der Ferne weiter** — Sobald du dich entfernst, spazieren Offline-Spieler jetzt genau dort weiter umher, wo sie sind, statt zu ihrem Abmeldeort zurückzulaufen oder völlig regungslos stehen zu bleiben. Wo kein Platz für einen Spaziergang ist, machen sie ein paar Schritte, drehen sich um oder setzen sich eine Weile hin.

## 0.15.0 — 2026-10-31

### Server

#### Neu
- **Kreaturen in unerkundetem Land** — Living Horizon zeigt den Spielern eines Servers jetzt die Kreaturen, die die Welt in Gegenden platzieren würde, die noch niemand erkundet hat, standardmäßig bis zu 512 Blöcke um jeden von ihnen. Ihre Positionen werden anhand des Startwerts erraten: Sie können abweichen oder verschwunden sein, sobald jemand dort ankommt. Diese Funktion lässt sich in den Servereinstellungen abschalten oder in ihrer Reichweite ändern.
- **Teilen des Startwerts** — Server können Living Horizon jetzt den Startwert der Welt mit Spielern teilen lassen, die die Mod haben, damit ihr Spiel Kreaturen überall selbst erraten kann, durch ein Fernrohr oder mit *Kreaturen erraten*. Diese Option ist standardmäßig aus, denn wer den Startwert kennt, weiß alles über die Welt.

#### Verbessert
- **Elytrenflug aus der Ferne sichtbar** — Der Server teilt jetzt mit, welche Spieler mit Elytren fliegen, damit sie auch auf große Entfernung korrekt im Gleitflug angezeigt werden.

### Client

#### Neu
- **Schnelllinks zu Living Horizon** — Die Einstellungen der Mod bieten jetzt neben Discord Verknüpfungen zu Modrinth, CurseForge, Nexus Mods und der offiziellen Website.
- **Kreaturen in unerkundeten Gegenden** — Gegenden, die noch niemand erkundet hat, sind nicht mehr leer: Living Horizon errät anhand des Startwerts, welche Kreaturen die Welt dort platzieren würde, so weit die *Kreaturen‐Sichtweite* reicht. Da es nur Vermutungen sind, können sie abweichen oder verschwunden sein, sobald du dort ankommst. Verfügbar im Einzelspielermodus und auf Servern mit der Mod; mit *Erratene Kreaturen* lassen sie sich ausblenden.
- **Erratene Kreaturen durch das Fernrohr** — Ein Blick durch ein Fernrohr zeigt jetzt die erratenen Kreaturen unerkundeter Gegenden, wohin du auch zielst, egal wie weit. Auf einem Server muss der Server dafür seinen Startwert teilen.
- **Die Kreaturen ringsum erraten** — Die neue Schaltfläche *Kreaturen erraten* zeigt auf einen Schlag die erratenen Kreaturen bis zu 1024 Chunks um dich herum und behält sie für deine nächsten Sitzungen. Auf einem Server muss der Server dafür seinen Startwert teilen.
- **Werte direkt eingeben** — Schieberegler in den Einstellungen der Mod, die bei *Unbegrenzt* enden (Höchstzahlen und Sichtweiten), nehmen jetzt einen über die Tastatur eingegebenen Wert an, sogar über ihre letzte Stufe hinaus, und auch Sodiums Seite zeigt ihn an. Klicke mit der rechten Maustaste auf einen Regler, um einen genauen Wert einzugeben; mit den Pfeiltasten lässt sich jeder Regler außerdem Stufe für Stufe verschieben.
- **Entfernungsvorschau in der Welt** — Wenn du mit der Maus auf eine Entfernungseinstellung zeigst oder sie änderst, wird ihre Grenze jetzt direkt in der Welt hinter dem Menü angezeigt. So siehst du auf einen Blick, wie weit der gewählte Wert reicht.

#### Verbessert
- **Optionale Vereinfachungsweite** — Die *Vereinfachungsweite* lässt sich jetzt abschalten. Kreaturen, Objekte, Spieler und Vögel werden dann erst zu vereinfachten Bildern, wenn sie auf dem Bildschirm klein genug wirken.
- **Bis zu 4000 ferne Kreaturen** — *Max. Kreaturen* reicht jetzt bis 4000, bevor *Unbegrenzt* folgt.
- **Unbegrenzt am Ende des Reglers** — *Unbegrenzt* liegt jetzt am rechten Ende der Sichtweitenregler, nach 4000 Blöcken, statt am linken. Die Regler reichen jetzt von der kürzesten bis zur längsten Entfernung.
- **Bestätigung vor Unbegrenzt** — Das Spiel fragt jetzt nach einer Bestätigung, bevor eine Anzahl von Kreaturen, Objekten oder Offline-Spielern oder eine Sichtweite auf *Unbegrenzt* gesetzt wird. Diese Werte können die Bildrate stark senken oder das Spiel sogar abstürzen lassen. Auf Sodiums Seite erscheint die Frage beim Klick auf *Anwenden*.
- **Vernünftigere Standardgrenzen** — Keine Einstellung ist mehr standardmäßig unbegrenzt, um starke Leistungseinbrüche zu vermeiden: *Spieler‐Sichtweite*, *Animationsweite* und *Objekt‐Sichtweite* enden bei 4000 Blöcken. Die Voreinstellungen *Hoch* und *Ultra* zeigen höchstens 1500 bzw. 3000 Kreaturen, *Ultra* bis zu 2048 Blöcke weit, und Einstellungen, die noch auf den alten Standardwerten standen, wechseln auf die neuen.
- **Natürlicherer Elytrenflug** — Ferne Spieler, die mit Elytren fliegen, werden jetzt mit ausgebreiteten Flügeln in echter Flughaltung gezeigt, statt aufrecht durch die Luft zu rennen.
- **Flüssigerer Wechsel von Ressourcenpaketen** — Vereinfachte Bilder von Kreaturen werden nicht mehr unnötig neu erstellt, wenn ein Ressourcenpaket aus- und wieder eingeschaltet wird. Auch friert das Spiel beim Laden der Ressourcen nicht mehr ein, während sie durchsucht werden.
- **Fliegende Kreaturen aus Ice and Fire aus jedem Winkel** — Die Drachen, Amphitheren und Hippogryphen aus Ice and Fire haben jetzt wie Phantome und Flugzeuge vereinfachte Bilder von unten und von oben, statt nur von der Seite. Sie sehen richtig aus, wenn sie über dich hinwegfliegen oder du auf sie hinabblickst.
- **Schärfere riesige Kreaturen in der Ferne** — Sehr große Kreaturen behalten jetzt ihr vollständiges 3D-Modell, solange sie auf dem Bildschirm noch groß wirken, statt zu einem klobigen vereinfachten Bild zu werden.

#### Behoben
- **Ferne Kreaturen auf dem fernen Gelände sichtbar** — Ferne Kreaturen, erratene eingeschlossen, bleiben jetzt auf dem fernen Gelände sichtbar, statt ausgeblendet zu werden, als wäre nichts unter ihnen: eine Weile nach dem Öffnen einer Welt, zwischen Tausenden erratener Kreaturen oder wenn Voxy oder Distant Horizons langsam antworten.
- **Nebel von Distant Horizons auf Kreaturen** — Ferne Kreaturen bekommen jetzt denselben Nebel wie das Gelände von Distant Horizons und verblassen mit ihm, statt sich scharf von der Landschaft abzuheben. (Ohne Shader.)
- **Kreaturen aus Ice and Fire richtig vereinfacht** — Ferne Drachen aus Ice and Fire haben jetzt vollständige vereinfachte Bilder in ihrer echten Größe, statt einer Nahaufnahme ihrer Beine oder eines Flügels. Drachen unterschiedlichen Alters teilen sich kein Bild mehr, und Seeschlangen behalten ihr 3D-Modell statt eines abgeschnittenen Bildes.
- **Offline-Spieler behalten ihr Boot** — Ein Spieler, der sich mitten auf dem Wasser abgemeldet hat, verliert das Boot, in dem er ruht, nicht mehr, sobald du dich entfernst: Er behält es aus der Nähe wie aus der Ferne und auch beim nächsten Spielen.
- **Offline-Spieler schweben nicht mehr in der Luft** — Ein Spieler, der sich in der Luft abgemeldet hat, im Gleitflug mit Elytren oder im Fallen, kehrt nicht mehr dorthin zurück, sobald du dich entfernst: Er ruht auf dem Boden darunter. Mit Voxy oder Distant Horizons steht er schon auf dem Boden, bevor du in seine Nähe kommst.
- **Ferne Kreaturen ruhen natürlich** — Ferne Kreaturen jenseits der *Animationsweite* oder kleiner als die *Min. Animationsgröße* bleiben jetzt stehen, wo sie sind, statt mit reglosen Beinen hin und her zu gleiten und zu zittern. Sobald sie wieder nah genug sind, laufen sie von dort weiter, wo sie angehalten haben.

## 0.14.0 — 2026-10-29

### Client

#### Neu
- **Fahrzeuge anderer Mods aus der Ferne sichtbar** — Fahrzeuge aus anderen Mods, etwa die Flugzeuge von Immersive Aircraft, bleiben jetzt wie Boote jenseits der Sichtweite dort sichtbar, wo sie abgestellt wurden. Fahrzeuge, in denen du mitfährst, ohne sie zu lenken, etwa Loren, sind standardmäßig aus und lassen sich in *Kreaturen & Objekte …* einschalten.
- **Eigene Einstellungen für Objekte** — Boote, Fahrzeuge und Spielerpuppen haben jetzt eigene Einstellungen, getrennt von den Kreaturen: ob sie angezeigt werden, wie viele und wie weit. Ihre Sichtweite ist standardmäßig unbegrenzt.
- **Getrennte Vereinfachung für Kreaturen und Objekte** — *Vereinfachte Objekte* ist jetzt in zwei Optionen aufgeteilt: *Vereinfachte Kreaturen* und *Vereinfachte Objekte*. Boote, Fahrzeuge und Spielerpuppen können in der Ferne jetzt ebenfalls zu vereinfachten Bildern werden, auch von oben oder unten gesehen. *Vereinfachte Spieler* ist jetzt unabhängig, und der Bildschirm mit den Bildern heißt jetzt *Bildvorschau …*, mit einem Tab für Objekte.
- **Änderungsprotokoll im Spiel** — Eine neue Schaltfläche in den Einstellungen der Mod zeigt, was sich in jeder Version geändert hat, ohne das Spiel zu verlassen. Sie ist auch auf Sodiums Seite verfügbar.

#### Verbessert
- **Hinweis auf inkompatiblen Servern** — Wenn du einem Server ohne Living Horizon beitrittst oder einem mit einer zu alten oder zu neuen Version, teilt dir jetzt eine Chatnachricht mit, dass ferne Spieler dort nicht angezeigt werden. Die Nachricht erscheint nicht, wenn *Spieler anzeigen* aus ist.
- **Kreaturen & Objekte in zwei Tabs** — Der Bildschirm *Kreaturen & Objekte …* teilt Kreaturen und andere Objekte jetzt auf zwei Tabs auf, damit sie leichter zu finden sind. Boote und Fahrzeuge werden dort einfach angezeigt oder ausgeblendet.
- **Kreaturenauswahl sofort wirksam** — Einen Typ in *Kreaturen & Objekte …* anzuzeigen oder auszublenden, wirkt sich jetzt sofort auf die Welt hinter dem Menü aus, ohne es zu schließen.
- **Besser gegliederte Kreatureneinstellungen** — Die Einstellungen *Ferne Kreaturen & Objekte* sind jetzt in drei Bereiche gegliedert: *Allgemein*, *Kreaturen* und *Objekte*.
- **Reiter aus der Ferne naturgetreuer dargestellt** — Mit *Vereinfachte Spieler* wird ein ferner Spieler in einem Boot, einer Lore oder einem Flugzeug oder auf einem Glücklichen Ghast jetzt korrekt von oben und unten gezeigt, statt immer von der Seite zu erscheinen.
- **Flugzeuge bleiben in der Luft** — Ein Spieler, der sich mitten im Flug abmeldet, bleibt jetzt mit seinem Flugzeug in der Luft, das schwebt oder kreist, je nachdem, ob es stillstand oder sich bewegte.
- **Herkunft der Bilder angezeigt** — Der Bildschirm *Bildvorschau* zeigt jetzt, woher jedes Bild stammt (geteilt, aus einer früheren Sitzung übernommen, neu oder nicht gespeichert), und wann und in welcher Welt es erstellt wurde.
- **Sitzende Haustiere bleiben sitzen** — Katzen, Wölfe und Papageien, denen du befohlen hast zu sitzen, bleiben jetzt auch in der Ferne sitzen, statt aufzustehen und davonzulaufen.

#### Behoben
- **Absturz bei Kreaturen & Objekte behoben** — Das Öffnen von *Kreaturen & Objekte …* aus dem Hauptmenü lässt das Spiel nicht mehr abstürzen. (Minecraft 26.x.)
- **Fahrzeuge aus Mods behalten ihre Farben** — Ferne Fahrzeuge und Kreaturen einiger Mods, etwa die Flugzeuge von Immersive Aircraft, behalten jetzt ihre Farben, wenn du dich näherst, statt kurz schwarz zu werden.
- **Reittiere nur einmal angezeigt** — Das Reittier oder Fahrzeug eines fernen Spielers, etwa ein Flugzeug aus einer Mod, erscheint nicht mehr ein zweites Mal neben ihm.

## 0.13.0 — 2026-10-28

### Client

#### Verbessert
- **Flüssigeres Spiel mit fernen Kreaturen** — Living Horizon berechnet jetzt im Hintergrund, wo ferne Kreaturen und Offline-Spieler laufen können, ohne das Spiel zu verlangsamen.
- **Flüssigere Darstellung mit Voxy und Distant Horizons** — Das Spiel wartet pro Bild weniger auf den Grafiktreiber, wenn ferne Kreaturen auf dem Gelände von Voxy oder Distant Horizons angezeigt werden. Am größten ist der Gewinn auf NVIDIA-Karten mit eingeschalteter *Threaded-Optimierung*.
- **Phantome und Ghasts fliegen in der Ferne** — Ferne Phantome kreisen jetzt am Himmel, und Ghasts treiben langsam dahin, statt reglos in der Luft zu hängen.
- **Phantome und Ghasts von unten gesehen** — Als vereinfachte Bilder werden Phantome und Ghasts jetzt wie Vögel auch von unten und oben gezeigt, nicht nur von der Seite.
- **Flüssiger mit vielen fernen Kreaturen** — Das Spiel verschwendet nicht mehr bei jedem Bild Zeit mit jeder einzelnen fernen Kreatur. (Vulkan mit Minecraft 26.2 und neuer, Distant Horizons mit Minecraft 26.3.)
- **Jede Kreatur in der Ferne sichtbar** — Monster, Fledermäuse, Fische und alle anderen Kreaturen werden jetzt standardmäßig in der Ferne angezeigt und streifen wie Tiere umher. Endermen etwa bleiben im ganzen Ende sichtbar. Tiere, die du ausgeblendet hattest, bleiben ausgeblendet.

#### Behoben
- **Schaltflächen der Einstellungen wieder geordnet** — Die Schaltflächen unten in *Ferne Kreaturen & Objekte* überlappen sich nicht mehr, und *Auf Standard zurücksetzen* steht wieder links von *Fertig*. (Minecraft 1.20.1.)
- **Einfrieren beim Dimensionswechsel behoben** — Das Spiel friert nicht mehr dauerhaft ein, wenn du durch ein Portal gehst oder eine Welt verlässt. (Mit Distant Horizons.)

## 0.12.1 — 2026-10-27

### Client

#### Behoben
- **Benchmark-Befehl nur für Tests** — `/livinghorizon benchmark` wird Spielern nicht mehr angeboten: Er dient nur zum Testen der Mod.

## 0.12.0 — 2026-10-23

### Server

#### Neu
- **Servereinstellungen** — Serverbetreiber können jetzt festlegen, was Living Horizon teilt: Online-Spieler, Offline-Spieler und Kreaturen, bis zu welcher Entfernung, auf Wunsch nur zwischen Teammitgliedern, und welche Spieler oder Dimensionen nie geteilt werden. Die Einstellungen stehen in `config/livinghorizon-server.json` und gelten innerhalb von Sekunden, ohne Neustart.

#### Behoben
- **Beitritt zu Forge-Servern behoben** — Spieler mit der Mod werden nicht mehr mit der Meldung „mismatched mod channel list“ von einem Forge-Server geworfen, auf dem die Mod ebenfalls läuft. (Forge 1.20.2 und neuer.)
- **Zuschauer und unsichtbare Spieler bleiben verborgen** — Spieler im Zuschauermodus oder unter Unsichtbarkeit sind für andere nicht mehr in der Ferne sichtbar, auch nicht dort, wo sie sich abgemeldet haben.

### Client

#### Neu
- **Discord-Schaltfläche** — In den Einstellungen der Mod kannst du jetzt mit einem Klick dem Discord von Living Horizon beitreten, um eine Frage zu stellen oder ein Problem zu melden. Die Schaltfläche ist auch auf Sodiums Seite verfügbar.
- **Mod-Version angezeigt** — Die Einstellungsbildschirme und das Debug-Panel zeigen jetzt die Version von Living Horizon an, praktisch für einen Fehlerbericht.
- **Vereinfachungsgröße** — Eine neue Einstellung macht ferne Kreaturen, Spieler und Vögel zu vereinfachten Bildern, sobald sie auf dem Bildschirm kleiner als eine festgelegte Größe wirken (standardmäßig 10 Pixel), noch vor der *Vereinfachungsweite*. Was kaum zu sehen ist, lässt sich so viel günstiger zeichnen.

#### Verbessert
- **Flüssiger mit Hunderten ferner Kreaturen** — *Verdeckte auslassen* kostet mit vielen fernen Kreaturen und Spielern auf dem Bildschirm deutlich weniger, und Living Horizon hat im Hintergrund weniger zu tun.
- **Builds für Minecraft 1.21.1 leichter zu finden** — Die Fabric-, Quilt- und NeoForge-Builds für Minecraft 1.21 und 1.21.1 werden jetzt als Beta veröffentlicht. Sie erscheinen damit auf CurseForge und in Launchern, die Alpha-Versionen ausblenden.
- **Weniger Ruckler bei großen Mengen in der Ferne** — Ferne Kreaturen, Spieler und Vögel fordern jetzt pro Bild viel weniger Speicher vom Spiel an, was Ruckler verringert.
- **Flüssiger mit vielen Vögeln** — Vögel hinter dir oder Vögel, die zu klein sind, um gesehen zu werden, werden nicht mehr gezeichnet.

#### Behoben
- **Ferne Kreaturen immer auf festem Boden** — Ferne Kreaturen werden nicht mehr gezeichnet, wo unter ihnen noch kein Gelände sichtbar ist, weder das des Spiels noch das von Voxy oder Distant Horizons. Sie schweben nicht mehr in der Leere.
- **Einzelspielerwelten öffnen sich wieder auf Forge** — Eine Einzelspielerwelt schließt sich nicht mehr gleich beim Öffnen mit der Meldung „mismatched mod channel list“. (Forge 1.20.2 und neuer.)
- **Ferne Kreaturen bleiben auf dem Gelände von Distant Horizons** — Im Einzelspielermodus verschwinden ferne Kreaturen nicht mehr auf den Hügeln und Bergen von Distant Horizons und bleiben dort auch nicht mehr stehen.

## 0.11.0 — 2026-10-22

### Server

#### Verbessert
- **Entlastung für große Server** — Der Server braucht weniger Zeit, um auszuwählen, welche Skins und Reittiere er an jeden Spieler sendet. Lange Listen von Offline-Spielern werden jetzt in mehreren Teilen gesendet, statt in einer einzigen Nachricht, die für das Spiel zu groß ist.
- **Update für Spieler erforderlich** — Auf einem Server mit dieser Version brauchen auch die Spieler diese Version, um ferne Spieler zu sehen.

#### Behoben
- **Datei der Offline-Spieler geschützt** — Kann der Server `livinghorizon-positions.dat` nicht lesen, wird die Datei nicht mehr überschrieben: Sie wird als `livinghorizon-positions.dat.bad` aufbewahrt, und die Konsole meldet es.

### Client

#### Neu
- **Benchmark** — Der Befehl `/livinghorizon benchmark` misst, was jeder Teil der Mod in deiner Welt kostet, und erstellt einen Bericht, den du einem Fehlerbericht beifügen kannst.
- **Benchmark-Welt** — `/livinghorizon benchmark world` erstellt eine Testwelt, die immer gleich ist, voller Kreaturen und Offline-Spieler, sodass sich Messungen von einem Durchlauf zum nächsten vergleichen lassen. Deine eigene Welt bleibt unberührt.
- **Max. Offline** — Eine neue Einstellung begrenzt, wie viele Offline-Spieler gleichzeitig angezeigt werden, die nächsten zuerst (standardmäßig 256). Die übrigen erscheinen wieder, wenn du näher kommst.

#### Verbessert
- **Klarere Meldung bei unterschiedlichen Versionen** — Wenn die Mod auf dem Server zu alt oder zu neu für deine ist, steht im Log jetzt, welche aktualisiert werden muss, statt zu behaupten, der Server habe die Mod nicht.
- **Flüssiger mit vielen Offline-Spielern** — Offline-Spieler kosten in der Ferne weniger Leistung, und solche hinter Hügeln werden nicht mehr gezeichnet.

#### Behoben
- **Spieler‐Sichtweite wird eingehalten** — Offline-Spieler werden nicht mehr jenseits der *Spieler‐Sichtweite* angezeigt.
- **Ferne Spielerpuppen mit ihrem echten Skin** — Ferne Spielerpuppen behalten jetzt den Skin, den sie aus der Nähe tragen, ob von einem Spieler oder aus einem Ressourcenpaket, statt zu Alex zu werden. (Minecraft 1.21.9 und neuer.)
- **Wasser bleibt unter Photon mit Distant Horizons erhalten** — Mit eingeschalteter *Geländeverdeckung* bleibt Wasser jetzt normal, wenn eine ferne Kreatur oder ein ferner Spieler in Sicht ist, statt seinen kahlen, schattigen Grund freizulegen. (Minecraft 26.3.)
- **Umhänge von Spielerpuppen hängen natürlich** — Die Umhänge ferner Spielerpuppen hängen jetzt herab wie aus der Nähe, statt in die Luft abzustehen. (Minecraft 1.21.9 und neuer.)
- **Einstellungsdatei geschützt** — Ein Fehler beim Bearbeiten von `livinghorizon.json` von Hand löscht die Datei nicht mehr: Sie wird als `livinghorizon.json.bad` aufbewahrt, und das Log meldet es.
- **Gespeicherte Daten geschützt** — Wird das Spiel während des Speicherns geschlossen, werden die Dateien der gemerkten Kreaturen (die Living Horizon weiterhin in der Ferne anzeigt) und der Offline-Spieler nicht mehr beschädigt. Eine beschädigte Datei wird als `.bad` aufbewahrt, statt geleert zu werden.
- **Flüssigerer Beitritt zu großen Servern** — Der Beitritt zu einem Server mit vielen Offline-Spielern lässt das Spiel nicht mehr ruckeln.
- **Beschädigte Bilder automatisch neu erstellt** — Ein beschädigtes gespeichertes vereinfachtes Bild kann dem Spiel nicht mehr den Arbeitsspeicher ausgehen lassen: Es wird einfach neu erstellt.
- **Ferne Kreaturen mit Oculus vor dem Gelände von Distant Horizons sichtbar** — Mit Oculus und einem Shaderpaket verschwinden ferne Kreaturen nicht mehr hinter dem Gelände von Distant Horizons, ein Problem, das mit wachsender Sichtweite schlimmer wurde. (Forge.)
- **Natürliche Kopfbewegungen mit Entity Culling** — Ferne Kreaturen und Spieler schütteln mit Entity Culling, allein oder zusammen mit Chloride, nicht mehr in hohem Tempo den Kopf.
- **Spieler in der richtigen Mod-Dimension** — Online- und Offline-Spieler in einer Dimension aus einer Mod, etwa dem Aether, werden nicht mehr in einer anderen angezeigt, etwa dem Twilight Forest. Auf einem Server braucht auch der Server diese Version.
- **Nebel von Voxy auf Kreaturen** — Ferne Kreaturen auf dem Gelände von Voxy bekommen jetzt denselben Nebel wie die Landschaft um sie herum, statt ab einer bestimmten Entfernung in der Nebelfarbe zu verblassen. (Ohne Shader.)

## 0.10.0 — 2026-10-20

### Server

#### Neu
- **Living Horizon auf Servern** — Die Mod kann jetzt auch auf dem Server installiert werden: Lege die JAR-Datei in dessen Ordner `mods` (Fabric, Quilt, NeoForge oder Forge, dazu die Fabric API auf Fabric und Quilt). Es ist keine Einrichtung nötig, und sie ersetzt das Datenpaket.
- **Spieler in Echtzeit geteilt** — Der Server sendet die Position jedes Spielers fünfmal pro Sekunde an die Spieler, die die Mod haben, mit Skin, Rüstung, gehaltenen Gegenständen und Reittier.
- **Kreaturen geteilt** — Der Server sendet die Kreaturen im Umkreis von 2048 Blöcken um jeden Spieler, selbst solche, denen dieser Spieler nie begegnet ist: Tiere, Dorfbewohner, Golems, Hilfsgeister und Boote, mit Sattel, Geschirr, Rüstung oder Truhe.
- **Offline-Spieler geteilt** — Der Server merkt sich, wo sich jeder Spieler abgemeldet hat, sein Aussehen, sein Reittier und wann er gegangen ist, und gibt das an alle weiter, auch an Spieler, die später beitreten.
- **Spieler aus der Zeit vor der Installation** — Beim ersten Start findet der Server die Spieler, die die Welt bereits kannte, und zeigt sie als Offline-Spieler dort, wo sie gegangen sind, mit ihrer Ausrüstung.
- **Mit der Welt gespeichert** — Alles wird in `livinghorizon-positions.dat` im Weltordner gespeichert. Eine Kreatur wird vergessen, wenn ihr Chunk geladen wird und sie nicht mehr da ist.
- **LAN-Welten** — Eine im LAN geöffnete Welt teilt alles über das Spiel des Hosts, ohne dass sonst etwas installiert werden muss.
- **Spieler ohne die Mod willkommen** — Spieler ohne die Mod können trotzdem beitreten; der Server sendet ihnen einfach nichts.
- **Gleiche Version auf beiden Seiten** — Halte die Mod sowohl auf dem Server als auch in den Spielen der Spieler aktuell: Ein Spiel, das die Nachrichten des Servers nicht versteht, ignoriert sie.
- **Datenschutz: Positionen werden geteilt** — Der Server teilt die Position jedes Spielers mit allen Spielern, die die Mod haben, in jeder Entfernung. Informiere deine Spieler unbedingt darüber.
- **Kein Datenpaket mehr** — Das Datenpaket wird nicht mehr verwendet: Entferne es aus dem Ordner `datapacks` der Welt. Koordinaten laufen nicht mehr über die Anzeigetafel, daher erscheinen sie nicht mehr in den Punkteständen oder in der Seitenleiste.

#### Behoben
- **Spieler bleiben beim Schließen einer Welt erhalten** — Wenn ein Server oder eine LAN-Welt geschlossen wird, bleiben die Spieler, die noch darin sind, jetzt als Offline-Spieler dort, wo sie waren, statt zu verschwinden.

### Client

#### Neu
- **Jeder Spieler, in jeder Entfernung** — Auf einem Server mit der Mod siehst du jetzt alle anderen Spieler, wo auch immer sie sind, mit Skin, Rüstung, gehaltenen Gegenständen und Reittier.
- **Kreaturen, denen du nie begegnet bist** — Auf einem Server mit der Mod erscheinen ferne Kreaturen sofort, selbst solche, in deren Nähe du nie warst, mit Sattel, Geschirr, Rüstung oder Truhe.
- **Offline-Spieler schon beim Beitritt sichtbar** — Du siehst Offline-Spieler dort, wo sie gegangen sind, mit ihrem Aussehen und ihrem Reittier, selbst wenn sie gegangen sind, bevor du kamst.
- **Offline-Spieler im Einzelspielermodus** — Im Einzelspielermodus erscheinen die Offline-Spieler deiner Welt, sobald du sie öffnest, auch die aus der Zeit vor der Installation der Mod.
- **Ein lebendiger Horizont von Anfang an** — Ferne Kreaturen und Offline-Spieler erscheinen, sobald du beitrittst, nicht erst ein paar Sekunden später.
- **Server ohne die Mod** — Du kannst ihnen weiterhin beitreten. Dort werden nur die Kreaturen angezeigt, denen du bereits begegnet bist, ohne ferne Spieler, und das Log weist einmal darauf hin.

#### Verbessert
- **Zuverlässigere Positionen ferner Spieler** — Ferne Spieler werden jetzt nur noch anhand der Positionen platziert, die der Server teilt. Die Schätzung über die Ortungsleiste entfällt, ebenso die Einstellung *Zeit für verlorene Spieler*.

#### Behoben
- **Glückliche Ghasts richtig angezeigt** — Ein ferner Spieler, der auf einem Glücklichen Ghast reitet, hat keinen zweiten Ghast mehr neben sich, und einer, der bei deinem Beitritt bereits ritt, schwebt nicht mehr mitten in der Luft.
- **Glückliche Ghasts bleiben, wo sie zurückgelassen wurden** — Wenn ein ferner Spieler von seinem Glücklichen Ghast absteigt, bleibt dieser jetzt dort, wo er zurückgelassen wurde, statt dort wieder aufzutauchen, wo er zuerst geritten wurde, und zurückzufliegen.
- **Neue Skins aus der Ferne sichtbar** — Ein ferner Spieler, der seinen Skin wechselt, erscheint jetzt mit dem neuen, statt in der Ferne den alten zu behalten.
- **Spieler sitzen richtig in Booten** — Ein Spieler, der sich in einem Boot abmeldet, schaut jetzt in dieselbe Richtung wie das Boot, statt seitlich zu sitzen.
- **Reiter sitzen richtig** — Ein ferner Spieler auf einem Pferd sitzt jetzt korrekt, als vollständiges Modell wie als vereinfachtes Bild, statt im Pferd zu versinken oder auf seiner Kruppe zu sitzen.
- **Nur ein Pferd nach dem Abmelden** — Ein Spieler, der sich auf einem Pferd abmeldet, hinterlässt keine Kopie des Pferdes mehr neben dem, auf dem er sitzt.
- **Schaltflächen Website und Issues funktionieren** — Die Schaltflächen Website und Issues auf der Seite der Mod in Mod Menu sind nicht mehr ausgegraut. (Fabric.)

## 0.9.0 — 2026-10-19

### Behoben
- **Offline-Spieler aus dem Wasser** — Spieler, die sich beim Schwimmen abgemeldet haben, bleiben nicht mehr im Wasser stehen: Sie warten am nächsten Ufer oder rudern ein Boot, wenn das Ufer mehr als 10 Blöcke entfernt ist.
- **Wasser um Boote bleibt intakt** — Mit einem Shaderpaket wie Photon wird das Wasser um Offline-Spieler, die ein Boot rudern, nicht mehr durchsichtig, und um ferne Boote verschwindet es nicht mehr in einem großen Quadrat.
- **Ruderer behalten ihr Boot** — In der Ferne behalten Offline-Spieler, die ein Boot rudern, jetzt ihr Boot, während ihr vereinfachtes Bild erstellt wird, statt scheinbar ohne es dazustehen.

## 0.8.0 — 2026-10-18

### Neu
- **Vereinfachte Spieler** — Mit einer neuen Einstellung kannst du ferne Spieler als vollständige 3D-Modelle behalten, während ferne Kreaturen weiterhin zu vereinfachten Bildern werden.
- **12 neue Sprachen** — Living Horizon ist jetzt auf Deutsch, Spanisch, Italienisch, brasilianischem Portugiesisch, Polnisch, Türkisch, Russisch, Ukrainisch, vereinfachtem und traditionellem Chinesisch, Japanisch und Koreanisch verfügbar.
- **Einstellungen in Sodium** — Die Einstellungen von Living Horizon sind jetzt über die *Grafikeinstellungen* von Sodium erreichbar, auf eigenen Seiten. (Sodium 0.8 und neuer.)
- **Offline-Spieler mit ihrem Reittier** — Ein Spieler, der sich auf einem Pferd, in einem Boot oder auf einem Glücklichen Ghast abgemeldet hat, reitet damit weiter in der Gegend umher, in der er gegangen ist.
- **Kontrolle über den Grafikspeicher** — Der Bildschirm *Vereinfachte Objekte …* zeigt, wie viel Grafikspeicher die Bilder von Kreaturen und Spielern belegen, und zwei neue Einstellungen legen für beide jeweils eine Grenze fest.

### Verbessert
- **Klarere Einstellungen** — Jede Einstellung hat jetzt einen klareren Namen und eine klarere Beschreibung im Stil der Optionen von Minecraft selbst, und Entfernungsregler nutzen die volle Bildschirmbreite.
- **Lebendigere Offline-Spieler** — Offline-Spieler laufen jetzt eine kleine Runde um den Ort, an dem sie gegangen sind. In der Ferne werden sie wie Online-Spieler zu vereinfachten Bildern, ob sie stehen, sitzen, liegen oder im Bett sind.
- **Leichtere Reiter** — In der Ferne bildet ein Spieler auf einem Pferd, in einem Boot, einer Lore oder auf einem anderen Reittier jetzt mit diesem ein einziges vereinfachtes Bild, viel leichter als zwei vollständige Modelle.
- **Flüssiger bei großen Spielermengen** — Ab etwa hundert fernen Spielern werden die zusätzlichen jetzt ebenfalls zu vereinfachten Bildern, statt vollständige Modelle zu bleiben.

### Behoben
- **Kreaturen nach Größenänderung richtig verdeckt** — Ferne Kreaturen scheinen nach dem Ändern der Fenstergröße nicht mehr durch Hügel, auch nicht durch die von Voxy und Distant Horizons.
- **Spieler und Schleime mit Distant Horizons sichtbar** — Ferne Spieler, Offline-Spieler und Schleime verschwinden nicht mehr unter dem Gelände von Distant Horizons. (Minecraft 26.1 und neuer.)
- **Ganze Kreaturen vor dem Gelände von Distant Horizons** — Das Gelände von Distant Horizons schneidet nicht mehr in Kreaturen und Spieler, die davor stehen. (Minecraft 26.1 und neuer.)
- **Stabile Kreaturen mit Gehbewegung** — Mit eingeschalteter Gehbewegung flackern ferne Kreaturen nicht mehr, und das Gelände von Distant Horizons wackelt nicht mehr bei jedem Schritt vor ihnen. (Minecraft 26.1 und neuer.)

## 0.7.0 — 2026-10-17

### Neu
- **Offline-Spieler erwachen zum Leben** — Offline-Spieler leben jetzt in der Umgebung des Ortes, an dem sie gegangen sind: Sie spazieren umher, sitzen, sehen sich Truhen und Arbeitsblöcke an, gehen nachts ins Bett und heben ihren Schild, wenn sie getroffen werden oder Monster nahe kommen. Sie bleiben immer auf dem Boden: Sie fallen, wenn du den Block unter ihnen abbaust, und klettern wieder hinauf, wenn du sie eingräbst.
- **Zusatz „(offline)“** — Offline-Spieler zeigen *(offline)* hinter ihrem Namen, und wenn du einen aus der Nähe ansiehst, erfährst du, wann er zuletzt gesehen wurde. Ihre Namen oder die Offline-Spieler selbst lassen sich in den Einstellungen ausblenden.
- **3D-Vögel** — Vögel sind jetzt kleine 3D-Modelle wie die Kreaturen von Minecraft: Sie schlagen mit den Flügeln, stehen nach der Landung auf ihren Beinen und werden in der Ferne zu vereinfachten Bildern. Die Einstellung *Vogelstil* bringt die alten 2D-Vögel zurück.
- **Hmm …** — Ich dachte, ich hätte etwas am Himmel gesehen. 😉

### Verbessert
- **Hüpfende Kreaturen** — Ferne Schleime, Magmawürfel und Kaninchen bewegen sich jetzt hüpfend fort, statt zu gleiten. Schleime werden bei der Landung zusammengedrückt, und Hühner schlagen mit den Flügeln, wenn sie einen Block hinabsteigen.
- **Natürlichere Enten** — Enten fliegen jetzt gemeinsam auf, wenn du dich näherst, und landen weiter entfernt auf dem Wasser, statt für immer zu verschwinden.

### Behoben
- **Kreaturen durch das Fernrohr animiert** — Durch ein Fernrohr gesehene ferne Kreaturen zählen jetzt mit ihrer vergrößerten Größe und laufen weiter, wenn sie groß wirken, statt zu erstarren. (Einstellung *Min. Animationsgröße*.)
- **Kreaturen beim Öffnen einer Welt animiert** — Ferne Kreaturen laufen und grasen jetzt von Anfang an, statt sich auf der Stelle zu drehen.
- **Natürlichere sitzende Vögel** — Meisen, Rotkehlchen und Enten flitzen nicht mehr hin und her, schweben nicht mehr in der Luft, stehen nicht mehr im Laub und schwimmen nicht mehr auf trockenem Land. Enten tauchen nicht mehr direkt neben dir auf, nur um davonzufliegen, und schwimmen jetzt auf ihrem Teich umher, und Rotkehlchen und Meisen hüpfen mehr und fliegen seltener davon.
- **Richtige Lizenz in der Modliste** — Living Horizon zeigt in der Modliste jetzt die richtige Lizenz an. (Forge, NeoForge und Quilt.)

## 0.6.0 — 2026-10-15

### Neu
- **Kreaturenbilder bleiben erhalten** — Vereinfachte Bilder ferner Kreaturen werden jetzt auf der Festplatte gespeichert und von einer Sitzung zur nächsten wiederverwendet. Sie werden nur nach einer Änderung der Ressourcenpakete, der Mods oder der Spielversion neu erstellt.

### Verbessert
- **Neues Symbol** — Living Horizon hat ein neues Pixel-Art-Symbol: Steve im Vordergrund, umgeben von Kreaturen, Dorfbewohnern, Spielern und Vögeln bis hin zu den Bergen.
- **Stimmigere ferne Kreaturen bei deiner Rückkehr** — Im Einzelspielermodus teleportieren sich ferne Kreaturen viel seltener oder eilen an einen anderen Ort, wenn du zurückkommst: Sie folgen den echten Kreaturen, solange das Spiel diese bewegt, und verschwinden, wenn diese sterben.

### Behoben
- **Kein Flackern mehr im Bildschirm Vereinfachte Objekte** — Zwei Kreaturen mit demselben Namen, etwa Dorfbewohner in verschiedener Kleidung, flackern nicht mehr, weil sie ihre Plätze tauschen.
- **Sauberere Kreaturenbilder** — Ferne Kreaturen, die als vereinfachte Bilder angezeigt werden, haben keine dünnen dunklen Linien oder schwarzen Sprenkel mehr an ihrer Ober- oder Unterkante. Lange Kreaturen wie Pferde und Schnüffler werden nicht mehr an Schnauze oder Schwanz abgeschnitten.
- **Absturz durch GeckoLib-Kreaturen behoben** — Eine Welt stürzt nicht mehr wenige Sekunden nach dem Laden ab, wenn eine Kreatur aus einer GeckoLib-Mod in der Ferne gesehen wurde. Diese Kreaturen werden in der Ferne noch nicht angezeigt. (Minecraft 1.21.2 bis 1.21.8.)
- **Schnellere Vereinfachte Objekte** — Das Spiel wird mit vereinfachten Bildern nicht mehr langsamer: Dieselben Kreaturen werden nicht mehr immer wieder neu gezeichnet, und ähnlich aussehende Kreaturen teilen sich ein einziges Bild.

## 0.5.0 — 2026-10-13

### Neu
- **Vereinfachte Objekte in Minecraft 1.20.1 bis 1.21.8** — Sehr ferne Kreaturen und Spieler können jetzt auch in diesen Versionen als vereinfachte Bilder angezeigt werden, was deinem Rechner viel Arbeit abnimmt.

### Behoben
- **Absturz beim Start behoben** — Das Spiel stürzt mit installierter Mod nicht mehr beim Start ab. (Forge, Minecraft 1.20.1 bis 1.20.4.)
- **Absturz im Bildschirm Vereinfachte Objekte behoben** — Das Öffnen des Bildschirms *Vereinfachte Objekte* lässt das Spiel nicht mehr abstürzen. (Minecraft 1.21.8 und älter.)
- **Kreaturen schon beim Beitritt vorhanden** — Kreaturen, die bereits um dich herum waren, als du einer Welt beigetreten bist, erscheinen jetzt ebenfalls am Horizont, nicht nur die, denen du danach begegnest.

## 0.4.2 — 2026-10-12

### Behoben
- **Stabile Kreaturen, wenn sie sich entfernen** — Eine Kreatur, die sich über deine Sichtweite hinaus bewegt, verschwindet nicht mehr kurz und dreht sich nicht mehr in eine andere Richtung.
- **Kreaturen vollständig von Hügeln verdeckt** — Die Kleidung eines Dorfbewohners, ein gehaltener Gegenstand oder die äußere Schicht eines Schleims scheinen nicht mehr durch die Hügel von Voxy oder Distant Horizons, während der Rest der Kreatur verdeckt ist. (Minecraft 1.21.9 und neuer, mit Distant Horizons oder mit Voxy und Shadern.)

## 0.4.1 — 2026-10-11

### Behoben
- **Kreaturen mit Shadern richtig beleuchtet** — Als vereinfachte Bilder angezeigte ferne Kreaturen werden jetzt wie vollständige Modelle von der Sonne beleuchtet, statt mit Iris-Shaderpaketen wie Photon oder Complementary zu dunkel zu sein.
- **Geländeschatten behoben** — Ferne Kreaturen zeigen nicht mehr den Schatten des Geländes hinter ihnen, und das Wasser von Voxy sieht aus, wie es soll. (Photon mit Voxy.)
- **Kreaturen richtig von Hügeln verdeckt** — Ferne Kreaturen scheinen nicht mehr durch die Hügel von Voxy oder Distant Horizons. (Minecraft 26.1; Minecraft 26.2 und neuer mit Shadern, auch mit älteren Iris-Versionen; Minecraft 26.3 mit installiertem Iris.)
- **Unterstützung für Voxy 0.2.18** — Die Mod funktioniert wieder mit Voxy 0.2.18: Ferne Kreaturen stehen auf seinem Gelände und scheinen nicht mehr durch seine Hügel.
- **Mit Distant Horizons von den richtigen Hügeln verdeckt** — Ferne Kreaturen werden nicht mehr von Hügeln verdeckt, die gar nicht vor ihnen liegen. (Distant Horizons mit dem TAAU-Upscaling von Photon.)

## 0.4.0 — 2026-10-10

### Neu
- **Automatischer erster Scan** — Wenn du eine Einzelspielerwelt zum ersten Mal betrittst, sucht Living Horizon sofort nach den Kreaturen im Umkreis von 32 Chunks, damit der Horizont von Anfang an lebt. Der Scanradius wird jetzt wie die Sichtweite in Chunks eingestellt.
- **Animationseinstellungen** — Zwei neue Schieberegler beenden die Animation ferner Kreaturen, die auf dem Bildschirm zu klein oder zu weit entfernt sind, um die Leistung zu verbessern.
- **Qualitätsvoreinstellungen** — Niedrig, Normal, Hoch und Ultra legen mit einem Klick fest, wie viele ferne Kreaturen und Vögel du siehst und wie weit. Hoch und Ultra sind für leistungsstarke Rechner gedacht.

### Behoben
- **Kreaturen behalten ihre Farben** — Ohne Shader werden ferne Kreaturen nicht mehr kurz schwarz, vor allem im Flug.

## 0.3.2 — 2026-10-09

### Behoben
- **Mit Photon von den richtigen Hügeln verdeckt** — Mit Voxy und dem TAAU-Upscaling von Photon werden ferne Kreaturen wieder von den richtigen Hügeln verdeckt.

## 0.3.1 — 2026-10-08

### Behoben
- **Hügel auch in älteren Versionen berücksichtigt** — Ferne Kreaturen verstecken sich jetzt wie in neueren Versionen hinter den Hügeln von Voxy und Distant Horizons, statt durch sie hindurchzuscheinen. (Minecraft 1.20 bis 1.21.4.)
- **Ältere Versionen von Distant Horizons unterstützt** — Die Mod funktioniert jetzt mit Distant Horizons 2.3 und neuer, nicht nur mit der neuesten Version.

## 0.3.0 — 2026-10-07

### Neu
- **Vulkan-Unterstützung** — Mit dem Vulkan-Renderer des Spiels verstecken sich ferne Kreaturen ebenfalls hinter Hügeln, auch hinter dem Gelände von Distant Horizons.

### Behoben
- **Kreaturen vor dem Gelände von Distant Horizons sichtbar** — Ohne Shader verblassen ferne Kreaturen nicht mehr im Gelände von Distant Horizons.
- **Keine Schlieren mehr am Rand der Sichtweite** — Die Schlieren, die dort auftraten, wo die normale Sichtweite endet, sind verschwunden. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Neu
- **Ferne Kreaturen** — Tiere, Dorfbewohner und Golems bleiben jenseits deiner Sichtweite am Horizont lebendig, grasen und streifen umher.
- **Ferne Spieler** — Andere Spieler laufen in der Ferne weiter, mit ihrem Skin, ihrer Rüstung und ihrem Reittier. Spieler, die sich abmelden, schlafen dort weiter, wo sie gegangen sind.
- **Vögel** — Schwärme, Gänse im V-Flug, Möwen über der Küste und nachts Fledermäuse bringen Leben in den Himmel.
- **Unterstützung für Distant Horizons** — Living Horizon funktioniert sowohl mit Distant Horizons als auch mit Voxy.
- **Kreaturen hinter Hügeln verborgen** — Ferne Kreaturen hinter einem Hügel werden nicht gezeichnet, damit das Spiel flüssig läuft.
- **Vereinfachte Bilder in der Ferne** — Sehr ferne Kreaturen und Spieler können als vereinfachte Bilder angezeigt werden, die viel leichter zu zeichnen sind, wenn es viele davon gibt. Diese Option ist standardmäßig aus.
- **Scan der Umgebung** — Im Einzelspielermodus liest ein Scan die Kreaturen der Chunks um dich herum ein, damit sie sofort am Horizont erscheinen, ohne dass du ihnen zuerst begegnen musst.
- **Einstellungsbildschirm** — Eine Schaltfläche *Living Horizon …* im Menü Optionen öffnet die Einstellungen der Mod, mit der Welt weiterhin sichtbar im Hintergrund, und eine Schaltfläche stellt die Standardwerte wieder her.
- **Quilt, NeoForge und Forge** — Living Horizon läuft auch auf Quilt, NeoForge und Forge, von Minecraft 1.20 bis 26.3.
- **Mod-Logo** — Living Horizon hat jetzt ein Logo in der Modliste und in der Liste der Datenpakete.
