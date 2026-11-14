# Living Horizon — Lista zmian

## Następna wersja

## 0.16.0 — 2026-11-05

### Klient

#### Nowości
- **Obsługa terenu Lodistance** — Odległe stworzenia i gracze chowają się teraz za dalekim terenem Lodistance jak za każdym innym wzgórzem i wtapiają się w jego mgłę, a odległe stworzenia chodzą po jego powierzchni, tak jak z Voxy i Distant Horizons. (Minecraft 26.3, z OpenGL, bez shaderów.)
- **Obsługa terenu Far View** — Odległe stworzenia są teraz widoczne i chodzą po dalekim terenie Far View oraz chowają się za jego wzgórzami, zamiast znikać poza zasięgiem renderowania. (Forge i NeoForge, Minecraft od 1.20.1 do 1.21.4.)
- **Obsługa terenu Ecstatic** — Odległe stworzenia pojawiają się teraz na terenie Ecstatic i chodzą po jego powierzchni, zamiast znikać poza zasięgiem renderowania. Poprawnie chowają się za jego wzgórzami, pozostają widoczne przed nimi i wtapiają się w jego mgłę. (NeoForge, Minecraft 1.21.1, tryb jednoosobowy; mgła bez shaderów.)
- **Uproszczone stworzenia oświetlane przez shadery** — Z paczką shaderów, która odczytuje mapy normalnych, taką jak Photon z włączonym *Normal Mapping*, uproszczone obrazy odległych stworzeń są teraz oświetlane przez paczkę jak ich modele 3D, z jedną stroną w słońcu, a drugą w cieniu, zamiast wyglądać płasko i ciemniej. Te obrazy zajmują dwa razy więcej pamięci wideo; nowe ustawienie *Oświetlenie shaderów* pozwala to wyłączyć. (Minecraft 1.21.6 i nowsze.)
- **Odpychanie graczy offline** — Możesz teraz odsunąć graczy offline z drogi, wchodząc w nich. Oni nigdy cię nie odpychają, bo istnieją tylko w twojej grze.
- **Gracze offline idą na ryby** — Gracze offline w pobliżu wody mogą teraz zatrzymać się na brzegu i przez chwilę łowić ryby. Na tym samym odcinku brzegu łowi tylko jeden naraz.
- **Gracze offline chronią się przed deszczem** — Gdy pada, niektórzy gracze offline idą teraz pod dach lub drzewo, do którego mogą dojść, i czekają tam, aż przestanie. Pozostali zajmują się swoimi sprawami jak zwykle.
- **Gracze offline zbierają się przy ogniu** — Wieczorem gracze offline stają teraz przed ogniskiem lub piecem, do którego mogą dojść, każdy w swoim miejscu.
- **Gracze offline rozmawiają ze sobą** — Dwóch graczy offline stojących blisko siebie może teraz do siebie podejść i przez chwilę porozmawiać twarzą w twarz. Nigdy więcej niż dwóch naraz i tylko jedna rozmowa w tej samej okolicy.
- **Gracze offline odwzajemniają spojrzenie** — Gracze offline odwracają się teraz w twoją stronę, gdy patrzysz na nich z bliska. Jeśli któryś stoi przodem do ciebie, a ty kucniesz, on też kuca.

#### Ulepszenia
- **Bardziej naturalne noce graczy offline** — Gracze offline śpią teraz w łóżku, do którego mogą dojść, albo pozostają na nogach, zamiast kłaść się na ziemi.
- **Ostrzejsze odległe statki** — Uproszczone obrazy wysokich łódek i pojazdów, takich jak żaglowce z Shippy Ships, są teraz ostrzejsze. Najwyższe, jak galeon, również mają teraz taki obraz, zamiast być zawsze rysowane jako pełny model, który bardziej obciąża wydajność.
- **Płynniejsze przejście do uproszczonych obrazów** — Odległe stworzenia i gracze obracają się teraz stopniowo pod kąt, pod jakim pokaże ich uproszczony obraz, w miarę zbliżania się do odległości, na której ich zastępuje, zamiast obracać się gwałtownie w chwili zamiany. Przejście z modelu 3D na obraz jest mniej zauważalne.
- **Łatwiej znaleźć wersje dla NeoForge 1.21.4 i 1.21.11** — Wersje NeoForge dla Minecraft 1.21.4 i 1.21.11 są teraz publikowane jako beta. Dzięki temu pojawiają się na CurseForge i w launcherach, które ukrywają wersje alfa.

#### Poprawki
- **Stworzenia widoczne tylko raz z Far View** — Stworzenia, które Far View rysuje jako proste bryły między 128 blokami a zasięgiem renderowania, są teraz widoczne tylko raz, zamiast dwa razy, jedno na drugim. (Forge i NeoForge, Minecraft od 1.20.1 do 1.21.4.)
- **Nazwy graczy offline widoczne na Forge** — Celowanie w gracza offline pokazuje teraz jego nazwę i to, kiedy był ostatnio widziany, zamiast nie pokazywać niczego. (Forge, Minecraft od 1.20.1 do 1.20.4.)
- **Lepsza integracja z Bobby** — Odległe stworzenia pozostają teraz widoczne tuż poza odległością, do której wysyła je serwer, na terenie zachowanym przez Bobby, zamiast znikać. Odlegli gracze również nie zatrzymują się już nagle, gdy wychodzą poza tę odległość.
- **Galeon z Shippy Ships widoczny z daleka** — Galeon z Shippy Ships pozostaje teraz widoczny w oddali, tam gdzie go zostawiono, jak inne statki tego moda, zamiast znikać, gdy tylko wyjdzie poza zasięg renderowania.
- **Gracze offline nadal ruszają się w oddali** — Gdy się oddalisz, gracze offline spacerują teraz dalej tam, gdzie są, zamiast wracać do miejsca wylogowania lub stać zupełnie nieruchomo. Tam, gdzie nie ma miejsca na spacer, robią kilka kroków, odwracają się lub siadają na chwilę.

## 0.15.0 — 2026-10-31

### Serwer

#### Nowości
- **Stworzenia na niezbadanych ziemiach** — Living Horizon pokazuje teraz graczom serwera stworzenia, które świat umieściłby w regionach, których nikt jeszcze nie zbadał, domyślnie do 512 bloków wokół każdego z nich. Ich pozycje są szacowane na podstawie ziarna: na miejscu mogą okazać się inne albo może ich nie być. Tę funkcję można wyłączyć lub zmienić jej zasięg w ustawieniach serwera.
- **Udostępnianie ziarna** — Serwery mogą teraz pozwolić Living Horizon udostępniać ziarno świata graczom z modem, aby ich gra sama szacowała stworzenia w dowolnym miejscu, przez lunetę lub przyciskiem *Zgadnij stworzenia*. Ta opcja jest domyślnie wyłączona, ponieważ znajomość ziarna zdradza wszystko o świecie.

#### Ulepszenia
- **Lot na elytrach widoczny z daleka** — Serwer udostępnia teraz informację, którzy gracze lecą na elytrach, dzięki czemu są oni poprawnie pokazywani w locie szybowym z dużej odległości.

### Klient

#### Nowości
- **Szybkie linki do Living Horizon** — Ustawienia moda oferują teraz skróty do Modrinth, CurseForge, Nexus Mods i oficjalnej strony, obok Discorda.
- **Stworzenia na niezbadanych obszarach** — Regiony, których nikt jeszcze nie zbadał, nie są już puste: Living Horizon szacuje na podstawie ziarna, jakie stworzenia umieściłby tam świat, w granicach ustawienia *Zasięg render. stworzeń*. Ponieważ to tylko szacunki, na miejscu mogą okazać się inne albo może ich nie być. Dostępne w trybie jednoosobowym i na serwerach z modem; wyłączenie opcji *Zgadnięte stworzenia* je ukrywa.
- **Zgadnięte stworzenia w lunecie** — Patrzenie przez lunetę pokazuje teraz zgadnięte stworzenia niezbadanych obszarów tam, gdzie celujesz, niezależnie od odległości. Na serwerze wymaga to, aby serwer udostępniał swoje ziarno.
- **Zgadywanie stworzeń wokół ciebie** — Nowy przycisk *Zgadnij stworzenia* od razu pokazuje zgadnięte stworzenia w promieniu do 1024 chunków wokół ciebie i zachowuje je na kolejne sesje. Na serwerze wymaga to, aby serwer udostępniał swoje ziarno.
- **Wpisywanie wartości** — Suwaki w ustawieniach moda, które kończą się na *Bez limitu* (maksymalne liczby i zasięgi renderowania), przyjmują teraz wartość wpisaną z klawiatury, nawet poza ostatnim krokiem, a strona Sodium również ją pokazuje. Kliknij suwak prawym przyciskiem myszy, aby wpisać dokładną wartość; klawisze strzałek również przesuwają każdy suwak krok po kroku.
- **Podgląd odległości w świecie** — Gdy najedziesz na ustawienie odległości lub je zmienisz, jego granica jest teraz pokazywana bezpośrednio w świecie, za menu. Od razu widać, jak daleko sięga wybrana wartość.

#### Ulepszenia
- **Opcjonalna odległość uproszczenia** — Ustawienie *Odległość uproszczenia* można teraz wyłączyć. Stworzenia, byty, gracze i ptaki stają się wtedy uproszczonymi obrazami dopiero wtedy, gdy wydają się na ekranie wystarczająco małe.
- **Do 4000 odległych stworzeń** — Ustawienie *Maks. stworzeń* sięga teraz do 4000 przed wartością *Bez limitu*.
- **Bez limitu na końcu suwaka** — Wartość *Bez limitu* znajduje się teraz na prawym końcu suwaków zasięgu renderowania, za 4000 bloków, a nie na lewym. Suwaki czyta się teraz od najkrótszej do najdłuższej odległości.
- **Potwierdzenie przed wyborem Bez limitu** — Gra prosi teraz o potwierdzenie przed ustawieniem na *Bez limitu* liczby stworzeń, bytów lub graczy offline albo zasięgu renderowania. Takie wartości mogą mocno obniżyć liczbę klatek na sekundę, a nawet spowodować awarię gry. Na stronie Sodium pytanie pojawia się po kliknięciu *Zastosuj*.
- **Rozsądniejsze domyślne limity** — Żadne ustawienie nie jest już domyślnie bez limitu, aby uniknąć dużych spadków wydajności: *Zasięg render. graczy*, *Zasięg animacji* i *Zasięg render. bytów* kończą się na 4000 bloków. Profile *Wysoki* i *Ultra* pokazują najwyżej 1500 i 3000 stworzeń, *Ultra* do 2048 bloków, a ustawienia pozostawione na starych wartościach domyślnych przechodzą na nowe.
- **Bardziej naturalny lot na elytrach** — Odlegli gracze lecący na elytrach są teraz pokazywani z rozłożonymi skrzydłami, w prawdziwej pozie lotu, zamiast biec wyprostowani w powietrzu.
- **Płynniejsza zmiana paczek zasobów** — Uproszczone obrazy stworzeń nie są już niepotrzebnie tworzone od nowa, gdy paczka zasobów zostanie wyłączona i ponownie włączona. Wczytywanie zasobów nie zamraża też już gry podczas ich wyszukiwania.
- **Latające stworzenia z Ice and Fire widoczne pod każdym kątem** — Smoki, amfitery i hipogryfy z Ice and Fire mają teraz uproszczone obrazy widziane z dołu i z góry, jak fantomy i samoloty, a nie tylko z boku. Wyglądają poprawnie, gdy przelatują nad tobą lub gdy patrzysz na nie z góry.
- **Ostrzejsze ogromne stworzenia w oddali** — Bardzo duże stworzenia zachowują teraz pełny model 3D, dopóki wydają się na ekranie duże, zamiast zamieniać się w kanciasty uproszczony obraz.

#### Poprawki
- **Odległe stworzenia widoczne na dalekim terenie** — Odległe stworzenia, łącznie ze zgadniętymi, pozostają teraz widoczne na dalekim terenie, zamiast być ukrywane, jakby nic pod nimi nie było: przez pewien czas po otwarciu świata, wśród tysięcy zgadniętych stworzeń lub gdy Voxy albo Distant Horizons odpowiada z opóźnieniem.
- **Mgła Distant Horizons na stworzeniach** — Odległe stworzenia dostają teraz tę samą mgłę co teren Distant Horizons i zanikają razem z nim, zamiast ostro odcinać się od krajobrazu. (Bez shaderów.)
- **Stworzenia z Ice and Fire poprawnie uproszczone** — Odległe smoki z Ice and Fire mają teraz całe uproszczone obrazy w prawdziwym rozmiarze, zamiast zbliżenia na nogi lub skrzydło. Smoki w różnym wieku nie dzielą już jednego obrazu, a węże morskie zachowują model 3D zamiast uciętego obrazu.
- **Gracze offline zachowują swoją łódkę** — Gracz, który wylogował się na środku wody, nie traci już łódki, w której odpoczywa, gdy się oddalisz: zachowuje ją z bliska i z daleka, a także przy następnej grze.
- **Gracze offline nie unoszą się już w powietrzu** — Gracz, który wylogował się w powietrzu, szybując na elytrach lub spadając, nie wraca już tam, gdy się oddalisz: odpoczywa na ziemi pod sobą. Z Voxy lub Distant Horizons jest na ziemi, jeszcze zanim podejdziesz.
- **Odległe stworzenia naturalnie odpoczywają** — Odległe stworzenia znajdujące się dalej niż *Zasięg animacji* lub mniejsze niż *Min. rozmiar animacji* stoją teraz w miejscu, zamiast ślizgać się tam i z powrotem z nieruchomymi nogami i drżeć. Gdy znów są wystarczająco blisko, ruszają dalej z miejsca, w którym się zatrzymały.

## 0.14.0 — 2026-10-29

### Klient

#### Nowości
- **Pojazdy z innych modów widoczne z daleka** — Pojazdy z innych modów, takie jak samoloty z Immersive Aircraft, pozostają teraz widoczne poza zasięgiem renderowania tam, gdzie je zostawiono, tak jak łódki. Pojazdy, którymi się jeździ bez sterowania, takie jak wagoniki, są domyślnie wyłączone i można je włączyć w *Stworzenia i byty...*.
- **Osobne ustawienia bytów** — Łódki, pojazdy i manekiny mają teraz własne ustawienia, oddzielne od stworzeń: czy są widoczne, ile ich jest i jak daleko. Ich zasięg renderowania jest domyślnie bez limitu.
- **Osobne upraszczanie stworzeń i bytów** — *Uproszczone byty* to teraz dwie opcje: *Uproszczone stworzenia* i *Uproszczone byty*. Łódki, pojazdy i manekiny również mogą teraz stawać się uproszczonymi obrazami w oddali, także widziane z góry lub z dołu. *Uproszczeni gracze* to teraz niezależna opcja, a ekran obrazów nazywa się teraz *Podgląd obrazów...* i ma kartę dla bytów.
- **Lista zmian w grze** — Nowy przycisk w ustawieniach moda pokazuje, co zmieniło się w każdej wersji, bez wychodzenia z gry. Jest też dostępny na stronie Sodium.

#### Ulepszenia
- **Powiadomienie na niezgodnych serwerach** — Gdy dołączasz do serwera bez Living Horizon albo ze zbyt starą lub zbyt nową wersją, wiadomość na czacie informuje teraz, że odlegli gracze nie będą tam widoczni. Wiadomość nie pojawia się, jeśli opcja *Pokazuj graczy* jest wyłączona.
- **Stworzenia i byty na dwóch kartach** — Ekran *Stworzenia i byty...* dzieli teraz stworzenia i pozostałe byty na dwie karty, dzięki czemu łatwiej je znaleźć. Łódki i pojazdy można tam po prostu pokazać lub ukryć.
- **Wybór stworzeń stosowany od razu** — Pokazanie lub ukrycie rodzaju w *Stworzenia i byty...* działa teraz od razu w świecie za menu, bez jego zamykania.
- **Lepiej uporządkowane ustawienia stworzeń** — Ustawienia *Odległe stworzenia i byty* są teraz podzielone na trzy części: *Ogólne*, *Stworzenia* i *Byty*.
- **Wierniej pokazywani jeźdźcy z daleka** — Z opcją *Uproszczeni gracze* odległy gracz w łódce, wagoniku lub samolocie albo na wesołym ghaście jest teraz poprawnie pokazywany z góry i z dołu, zamiast zawsze z profilu.
- **Samoloty, które zostają w powietrzu** — Gracz, który wyloguje się w trakcie lotu, zostaje teraz w powietrzu w swoim samolocie, który unosi się w miejscu lub krąży, zależnie od tego, czy stał, czy się poruszał.
- **Widoczne pochodzenie obrazów** — Ekran *Podgląd obrazów* pokazuje teraz, skąd pochodzi każdy obraz (udostępniony, wzięty z wcześniejszej sesji, nowy lub niezapisany), a także kiedy i w którym świecie powstał.
- **Siedzące zwierzaki nie wstają** — Koty, wilki i papugi, którym kazano usiąść, siedzą teraz także w oddali, zamiast wstawać i odchodzić.

#### Poprawki
- **Naprawiono awarię ekranu Stworzenia i byty** — Otwarcie *Stworzenia i byty...* z ekranu tytułowego nie powoduje już awarii gry. (Minecraft 26.x.)
- **Pojazdy z modów zachowują kolory** — Odległe pojazdy i stworzenia z niektórych modów, takie jak samoloty z Immersive Aircraft, zachowują teraz swoje kolory, gdy się zbliżasz, zamiast na chwilę robić się czarne.
- **Wierzchowce widoczne tylko raz** — Wierzchowiec lub pojazd odległego gracza, na przykład samolot z moda, nie pojawia się już drugi raz obok niego.

## 0.13.0 — 2026-10-28

### Klient

#### Ulepszenia
- **Płynniejsza gra z odległymi stworzeniami** — Living Horizon oblicza teraz w tle, gdzie mogą chodzić odległe stworzenia i gracze offline, bez spowalniania gry.
- **Płynniejsze renderowanie z Voxy i Distant Horizons** — Gra mniej czeka na sterownik graficzny w każdej klatce, gdy odległe stworzenia są pokazywane na terenie Voxy lub Distant Horizons. Zysk jest największy na kartach NVIDIA z włączoną opcją *Optymalizacja wątkowa*.
- **Fantomy i ghasty latające w oddali** — Odległe fantomy krążą teraz po niebie, a ghasty powoli dryfują, zamiast wisieć nieruchomo w powietrzu.
- **Fantomy i ghasty widziane od dołu** — Jako uproszczone obrazy fantomy i ghasty są teraz pokazywane także z dołu i z góry, jak ptaki, a nie tylko z boku.
- **Płynniej z wieloma odległymi stworzeniami** — Gra nie traci już czasu na każde odległe stworzenie w każdej klatce. (Vulkan w Minecraft 26.2 i nowszych, Distant Horizons w Minecraft 26.3.)
- **Każde stworzenie widoczne w oddali** — Potwory, nietoperze, ryby i wszystkie inne stworzenia są teraz domyślnie pokazywane w oddali i wędrują jak zwierzęta. Endermany pozostają na przykład widoczne w całym Endzie. Wcześniej ukryte zwierzęta pozostają ukryte.

#### Poprawki
- **Przyciski ustawień znów na swoim miejscu** — Przyciski na dole ekranu *Odległe stworzenia i byty* już na siebie nie nachodzą, a *Przywróć domyślne* jest znów po lewej stronie przycisku *Gotowe*. (Minecraft 1.20.1.)
- **Naprawiono zawieszanie się przy zmianie wymiaru** — Gra nie zawiesza się już na dobre, gdy przechodzisz przez portal lub opuszczasz świat. (Z Distant Horizons.)

## 0.12.1 — 2026-10-27

### Klient

#### Poprawki
- **Polecenie benchmarku tylko do testów** — `/livinghorizon benchmark` nie jest już podpowiadane graczom: służy wyłącznie do testowania moda.

## 0.12.0 — 2026-10-23

### Serwer

#### Nowości
- **Ustawienia serwera** — Właściciele serwerów mogą teraz wybrać, co udostępnia Living Horizon: graczy online, graczy offline i stworzenia, do jakiej odległości, w razie potrzeby tylko między członkami drużyny, oraz których graczy lub wymiarów nigdy nie udostępniać. Ustawienia znajdują się w `config/livinghorizon-server.json` i zaczynają działać w ciągu kilku sekund, bez restartu.

#### Poprawki
- **Naprawiono dołączanie do serwerów Forge** — Gracze z modem nie są już wyrzucani z serwera Forge, który też go ma, z komunikatem „mismatched mod channel list”. (Forge 1.20.2 i nowsze.)
- **Widzowie i niewidzialni gracze pozostają ukryci** — Gracze w trybie widza lub pod wpływem niewidzialności nie są już widoczni dla innych w oddali ani tam, gdzie się wylogowali.

### Klient

#### Nowości
- **Przycisk Discorda** — Ustawienia moda pozwalają teraz jednym kliknięciem dołączyć do Discorda Living Horizon, aby zadać pytanie lub zgłosić problem. Przycisk jest też dostępny na stronie Sodium.
- **Widoczna wersja moda** — Ekrany ustawień i panel debugowania pokazują teraz wersję Living Horizon, co przydaje się w zgłoszeniu błędu.
- **Rozmiar uproszczenia** — Nowe ustawienie zamienia odległe stworzenia, graczy i ptaki w uproszczone obrazy, gdy tylko wydają się na ekranie mniejsze niż ustalony rozmiar (domyślnie 10 pikseli), nawet bliżej niż *Odległość uproszczenia*. To, co ledwo widać, rysuje się dzięki temu znacznie mniejszym kosztem.

#### Ulepszenia
- **Płynniej z setkami odległych stworzeń** — Opcja *Pomijaj zasłonięte* obciąża grę znacznie mniej, gdy na ekranie jest wiele odległych stworzeń i graczy, a Living Horizon wykonuje mniej pracy w tle.
- **Łatwiej znaleźć wersje dla Minecraft 1.21.1** — Wersje Fabric, Quilt i NeoForge dla Minecraft 1.21 i 1.21.1 są teraz publikowane jako beta. Dzięki temu pojawiają się na CurseForge i w launcherach, które ukrywają wersje alfa.
- **Mniej przycięć przy tłumach w oddali** — Odległe stworzenia, gracze i ptaki wymagają teraz od gry znacznie mniej pamięci w każdej klatce, co ogranicza przycięcia.
- **Płynniej z wieloma ptakami** — Ptaki za tobą lub zbyt małe, by je zobaczyć, nie są już rysowane.

#### Poprawki
- **Odległe stworzenia zawsze na stałym gruncie** — Odległe stworzenia nie są już rysowane tam, gdzie pod nimi nie widać jeszcze terenu, czy to gry, Voxy, czy Distant Horizons. Nie unoszą się już w pustce.
- **Światy jednoosobowe znów się otwierają na Forge** — Świat w trybie jednoosobowym nie zamyka się już zaraz po otwarciu z komunikatem „mismatched mod channel list”. (Forge 1.20.2 i nowsze.)
- **Odległe stworzenia zostają na terenie Distant Horizons** — W trybie jednoosobowym odległe stworzenia nie znikają już i nie przestają chodzić na wzgórzach i górach Distant Horizons.

## 0.11.0 — 2026-10-22

### Serwer

#### Ulepszenia
- **Mniejsze obciążenie dużych serwerów** — Serwer poświęca mniej czasu na wybieranie skinów i wierzchowców do wysłania każdemu graczowi. Długie listy graczy offline są teraz wysyłane w kilku częściach, zamiast w jednej wiadomości zbyt dużej dla gry.
- **Wymagana aktualizacja u graczy** — Na serwerze z tą wersją gracze również jej potrzebują, aby widzieć odległych graczy.

#### Poprawki
- **Ochrona pliku graczy offline** — Jeśli serwer nie może odczytać `livinghorizon-positions.dat`, plik nie jest już nadpisywany: zostaje zachowany jako `livinghorizon-positions.dat.bad`, a konsola to zgłasza.

### Klient

#### Nowości
- **Benchmark** — Polecenie `/livinghorizon benchmark` mierzy, ile kosztuje każda część moda w twoim świecie, i tworzy raport do dołączenia do zgłoszenia błędu.
- **Świat do benchmarku** — `/livinghorizon benchmark world` tworzy zawsze taki sam świat testowy, wypełniony stworzeniami i graczami offline, aby pomiary można było porównywać między kolejnymi uruchomieniami. Twój własny świat pozostaje nietknięty.
- **Maks. offline** — Nowe ustawienie ogranicza, ilu graczy offline jest widocznych naraz, najpierw najbliżsi (domyślnie 256). Pozostali pojawiają się ponownie, gdy się zbliżysz.

#### Ulepszenia
- **Jaśniejszy komunikat przy różnych wersjach** — Gdy mod na serwerze jest zbyt stary lub zbyt nowy w stosunku do twojego, dziennik podaje teraz, który zaktualizować, zamiast twierdzić, że serwer nie ma moda.
- **Płynniej z wieloma graczami offline** — Pokazywanie graczy offline w oddali mniej kosztuje, a ci ukryci za wzgórzami nie są już rysowani.

#### Poprawki
- **Zasięg renderowania graczy przestrzegany** — Gracze offline nie są już pokazywani dalej, niż pozwala ustawienie *Zasięg render. graczy*.
- **Odległe manekiny z prawdziwym skinem** — Odległe manekiny zachowują teraz skin, który noszą z bliska, czy to gracza, czy z paczki zasobów, zamiast zamieniać się w Alex. (Minecraft 1.21.9 i nowsze.)
- **Woda zachowana pod Photonem z Distant Horizons** — Z włączoną opcją *Ukrywaj za terenem* woda wygląda teraz normalnie, gdy w polu widzenia jest odległe stworzenie lub gracz, zamiast odsłaniać swoje gołe, zacienione dno. (Minecraft 26.3.)
- **Peleryny manekinów zwisają naturalnie** — Peleryny odległych manekinów zwisają teraz tak jak z bliska, zamiast sterczeć w powietrzu. (Minecraft 1.21.9 i nowsze.)
- **Ochrona pliku ustawień** — Błąd przy ręcznej edycji `livinghorizon.json` nie czyści już pliku: zostaje on zachowany jako `livinghorizon.json.bad`, a dziennik to zgłasza.
- **Ochrona zapisów** — Jeśli gra zamknie się podczas zapisu, pliki zapamiętanych stworzeń (tych, które Living Horizon nadal pokazuje w oddali) i graczy offline nie są już uszkadzane. Uszkodzony plik jest zachowywany jako `.bad`, zamiast zostać opróżniony.
- **Płynniejsze dołączanie do dużych serwerów** — Dołączanie do serwera z wieloma graczami offline nie powoduje już przycięć gry.
- **Automatyczne odtwarzanie uszkodzonych obrazów** — Uszkodzony zapisany uproszczony obraz nie może już wyczerpać pamięci gry: jest po prostu tworzony od nowa.
- **Odległe stworzenia widoczne przed terenem Distant Horizons z Oculus** — Z Oculus i paczką shaderów odległe stworzenia nie znikają już za terenem Distant Horizons, co nasilało się wraz ze wzrostem zasięgu renderowania. (Forge.)
- **Naturalne ruchy głowy z Entity Culling** — Odległe stworzenia i gracze nie kręcą już bardzo szybko głowami z Entity Culling, samym lub razem z Chloride.
- **Gracze we właściwym wymiarze z moda** — Gracze online i offline w wymiarze z moda, takim jak Aether, nie są już pokazywani w innym, takim jak Twilight Forest. Na serwerze sam serwer również potrzebuje tej wersji.
- **Mgła Voxy na stworzeniach** — Odległe stworzenia na terenie Voxy dostają teraz tę samą mgłę co otaczający je krajobraz, zamiast blednąć do koloru mgły powyżej pewnej odległości. (Bez shaderów.)

## 0.10.0 — 2026-10-20

### Serwer

#### Nowości
- **Living Horizon na serwerach** — Moda można teraz zainstalować także na serwerze: umieść plik jar w jego folderze `mods` (Fabric, Quilt, NeoForge lub Forge, a na Fabric i Quilt także Fabric API). Nie wymaga on konfiguracji i zastępuje paczkę danych.
- **Gracze udostępniani na bieżąco** — Serwer wysyła pozycję każdego gracza pięć razy na sekundę graczom z modem, razem z jego skinem, zbroją, trzymanymi przedmiotami i wierzchowcem.
- **Udostępniane stworzenia** — Serwer wysyła stworzenia w promieniu 2048 bloków od każdego gracza, nawet te, których ten gracz nigdy nie spotkał: zwierzęta, osadników, golemy, otuszki i łódki, z ich siodłem, uprzężą, zbroją lub skrzynią.
- **Udostępniani gracze offline** — Serwer zapamiętuje, gdzie każdy gracz się wylogował, jak wyglądał, jego wierzchowca i czas wyjścia, i przekazuje to wszystkim, także graczom, którzy dołączą później.
- **Gracze sprzed instalacji** — Przy pierwszym uruchomieniu serwer odnajduje graczy, których świat już znał, i pokazuje ich jako graczy offline tam, gdzie odeszli, z ich wyposażeniem.
- **Zapisywane razem ze światem** — Wszystko jest zapisywane w `livinghorizon-positions.dat`, w folderze świata. Stworzenie zostaje zapomniane, gdy jego chunk się wczyta, a jego już tam nie ma.
- **Światy w LAN** — Świat udostępniony w LAN udostępnia wszystko przez grę gospodarza, bez instalowania czegokolwiek więcej.
- **Gracze bez moda mile widziani** — Gracze bez moda nadal mogą dołączać; serwer po prostu nic im nie wysyła.
- **Ta sama wersja po obu stronach** — Aktualizuj moda zarówno na serwerze, jak i w grach graczy: gra, która nie rozumie wiadomości serwera, ignoruje je.
- **Prywatność: pozycje są udostępniane** — Serwer udostępnia pozycję każdego gracza wszystkim graczom z modem, niezależnie od odległości. Pamiętaj, aby poinformować o tym swoich graczy.
- **Koniec z paczką danych** — Paczka danych nie jest już używana: usuń ją z folderu `datapacks` świata. Współrzędne nie przechodzą już przez tablicę wyników, więc nie pojawiają się już w wynikach ani na pasku bocznym.

#### Poprawki
- **Gracze zachowani po zamknięciu świata** — Gdy serwer lub świat w LAN się zamyka, gracze, którzy wciąż w nim są, zostają teraz tam, gdzie byli, jako gracze offline, zamiast znikać.

### Klient

#### Nowości
- **Każdy gracz, w dowolnej odległości** — Na serwerze z modem widzisz teraz wszystkich innych graczy, gdziekolwiek są, z ich skinem, zbroją, trzymanymi przedmiotami i wierzchowcem.
- **Stworzenia, których jeszcze nie spotkano** — Na serwerze z modem odległe stworzenia pojawiają się od razu, nawet te, których nigdy nie widziano z bliska, z ich siodłem, uprzężą, zbroją lub skrzynią.
- **Gracze offline widoczni od razu po dołączeniu** — Widzisz graczy offline tam, gdzie odeszli, z ich wyglądem i wierzchowcem, nawet jeśli wyszli przed twoim przybyciem.
- **Gracze offline w trybie jednoosobowym** — W trybie jednoosobowym gracze offline twojego świata pojawiają się od razu po jego otwarciu, także ci sprzed instalacji moda.
- **Żywy horyzont od samego początku** — Odległe stworzenia i gracze offline pojawiają się, gdy tylko dołączysz, a nie kilka sekund później.
- **Serwery bez moda** — Nadal możesz do nich dołączać. Widoczne są tam tylko stworzenia, które już wcześniej napotkano, bez odległych graczy, a dziennik raz o tym wspomina.

#### Ulepszenia
- **Bardziej wiarygodne pozycje odległych graczy** — Odlegli gracze są teraz umieszczani wyłącznie na podstawie pozycji udostępnianych przez serwer. Szacowanie z paska nawigacyjnego zniknęło, razem z ustawieniem *Czas utraty gracza*.

#### Poprawki
- **Wesołe ghasty pokazywane poprawnie** — Odległy gracz lecący na wesołym ghaście nie ma już obok siebie drugiego ghasta, a ten, który leciał na nim już w chwili twojego dołączenia, nie unosi się już w powietrzu.
- **Wesołe ghasty zostają tam, gdzie je zostawiono** — Gdy odległy gracz zsiądzie z wesołego ghasta, ten zostaje teraz tam, gdzie go zostawiono, zamiast pojawiać się znów tam, gdzie gracz go dosiadł po raz pierwszy, i wracać lotem.
- **Nowe skiny widoczne z daleka** — Odległy gracz, który zmieni skin, pojawia się teraz z nowym, zamiast zachowywać stary w oddali.
- **Gracze poprawnie siedzący w łódkach** — Gracz, który wyloguje się w łódce, jest teraz zwrócony w tę samą stronę co łódka, zamiast siedzieć bokiem.
- **Jeźdźcy poprawnie w siodle** — Odległy gracz jadący konno siedzi teraz poprawnie, zarówno jako pełny model, jak i uproszczony obraz, zamiast zapadać się w konia lub siedzieć na jego zadzie.
- **Tylko jeden koń po wylogowaniu** — Gracz, który wyloguje się na koniu, nie zostawia już kopii konia obok tego, na którym siedzi.
- **Działające przyciski Website i Issues** — Przyciski Website i Issues na stronie moda w Mod Menu nie są już wyszarzone. (Fabric.)

## 0.9.0 — 2026-10-19

### Poprawki
- **Gracze offline wychodzą z wody** — Gracze, którzy wylogowali się podczas pływania, nie stoją już w wodzie: czekają na najbliższym brzegu albo wiosłują w łódce, jeśli brzeg jest dalej niż 10 bloków.
- **Nienaruszona woda wokół łódek** — Woda nie staje się już przezroczysta wokół graczy offline wiosłujących w łódce z paczką shaderów taką jak Photon i nie znika już w dużym kwadracie wokół odległych łódek.
- **Wioślarze zachowują łódkę** — W oddali gracze offline wiosłujący w łódce zachowują ją teraz podczas tworzenia ich uproszczonego obrazu, zamiast wyglądać, jakby stali bez niej.

## 0.8.0 — 2026-10-18

### Nowości
- **Uproszczeni gracze** — Nowe ustawienie pozwala zachować odległych graczy jako pełne modele 3D, podczas gdy odległe stworzenia nadal stają się uproszczonymi obrazami.
- **12 nowych języków** — Living Horizon jest teraz dostępny w językach: niemieckim, hiszpańskim, włoskim, portugalskim (Brazylia), polskim, tureckim, rosyjskim, ukraińskim, chińskim uproszczonym i tradycyjnym, japońskim i koreańskim.
- **Ustawienia w Sodium** — Ustawienia Living Horizon są teraz dostępne w Sodium, w menu *Ustawienia graficzne*, na własnych stronach. (Sodium 0.8 i nowsze.)
- **Gracze offline z wierzchowcem** — Gracz, który wylogował się na koniu, w łódce lub na wesołym ghaście, nadal na nim jeździ w okolicy miejsca, w którym odszedł.
- **Kontrola pamięci wideo** — Ekran *Uproszczone byty...* pokazuje, ile pamięci wideo zajmują obrazy stworzeń i graczy, a dwa nowe ustawienia określają limit dla każdego z nich.

### Ulepszenia
- **Czytelniejsze ustawienia** — Każde ustawienie ma teraz jaśniejszą nazwę i opis, w stylu opcji samego Minecrafta, a suwaki odległości zajmują całą szerokość ekranu.
- **Bardziej żywi gracze offline** — Gracze offline chodzą teraz po małej pętli wokół miejsca, w którym odeszli. W oddali stają się uproszczonymi obrazami jak gracze online, czy to stojąc, siedząc, leżąc, czy w łóżku.
- **Lżejsi jeźdźcy** — W oddali gracz jadący na koniu, w łódce, w wagoniku lub na dowolnym innym wierzchowcu tworzy teraz z nim jeden uproszczony obraz, znacznie lżejszy niż dwa pełne modele.
- **Płynniejsze duże tłumy graczy** — Powyżej około stu odległych graczy kolejni też stają się teraz uproszczonymi obrazami, zamiast pozostawać pełnymi modelami.

### Poprawki
- **Stworzenia poprawnie ukryte po zmianie rozmiaru okna** — Odległe stworzenia nie prześwitują już przez wzgórza, także te Voxy i Distant Horizons, po zmianie rozmiaru okna gry.
- **Gracze i szlamy widoczni z Distant Horizons** — Odlegli gracze, gracze offline i szlamy nie znikają już pod terenem Distant Horizons. (Minecraft 26.1 i nowsze.)
- **Całe stworzenia przed terenem Distant Horizons** — Teren Distant Horizons nie wcina się już w stworzenia i graczy stojących przed nim. (Minecraft 26.1 i nowsze.)
- **Stabilne stworzenia z animacją chodzenia** — Z włączoną animacją chodzenia odległe stworzenia już nie migoczą, a teren Distant Horizons nie faluje już przed nimi przy każdym kroku. (Minecraft 26.1 i nowsze.)

## 0.7.0 — 2026-10-17

### Nowości
- **Gracze offline ożywają** — Gracze offline żyją teraz w okolicy miejsca, w którym odeszli: spacerują, siadają, przyglądają się skrzyniom i stanowiskom pracy, w nocy chodzą spać i unoszą tarczę, gdy ktoś ich uderzy lub zbliżają się potwory. Zawsze pozostają na ziemi: spadają, jeśli zniszczysz blok pod nimi, i wychodzą na powierzchnię, jeśli ich zakopiesz.
- **Dopisek „(offline)”** — Gracze offline mają *(offline)* po swojej nazwie, a spojrzenie na któregoś z bliska pokazuje, kiedy był ostatnio widziany. Ich nazwy albo samych graczy offline można ukryć w ustawieniach.
- **Ptaki 3D** — Ptaki są teraz małymi modelami 3D, jak stworzenia Minecrafta: machają skrzydłami, stoją na nogach, gdy przysiądą, i stają się uproszczonymi obrazami w oddali. Ustawienie *Styl ptaków* przywraca dawne ptaki 2D.
- **Hmm…** — Chyba coś mignęło mi na niebie. 😉

### Ulepszenia
- **Skaczące stworzenia** — Odległe szlamy, kostki magmy i króliki poruszają się teraz skokami, zamiast się ślizgać. Szlamy spłaszczają się przy lądowaniu, a kury machają skrzydłami, schodząc o blok w dół.
- **Bardziej naturalne kaczki** — Kaczki odlatują teraz razem, gdy się zbliżasz, i lądują dalej na wodzie, zamiast odlatywać na dobre.

### Poprawki
- **Stworzenia animowane w lunecie** — Odległe stworzenia oglądane przez lunetę są teraz liczone w powiększonym rozmiarze i nadal chodzą, gdy wydają się duże, zamiast zastygać. (Ustawienie *Min. rozmiar animacji*.)
- **Stworzenia animowane od otwarcia świata** — Odległe stworzenia chodzą i pasą się teraz od samego początku, zamiast obracać się w miejscu.
- **Bardziej naturalne przysiadające ptaki** — Sikory, rudziki i kaczki nie miotają się już tam i z powrotem, nie unoszą się w powietrzu, nie stoją w liściach i nie pływają po suchym lądzie. Kaczki nie pojawiają się już tuż obok ciebie tylko po to, by odlecieć, i teraz pływają po swoim stawie, a rudziki i sikory częściej skaczą i rzadziej odlatują.
- **Właściwa licencja na liście modów** — Living Horizon pokazuje teraz właściwą licencję na liście modów. (Forge, NeoForge i Quilt.)

## 0.6.0 — 2026-10-15

### Nowości
- **Zachowane obrazy stworzeń** — Uproszczone obrazy odległych stworzeń są teraz zapisywane na dysku i używane ponownie między sesjami. Są tworzone od nowa tylko po zmianie paczek zasobów, modów lub wersji gry.

### Ulepszenia
- **Nowa ikona** — Living Horizon ma nową ikonę w stylu pixel art: Steve na pierwszym planie, otoczony stworzeniami, osadnikami, graczami i ptakami aż po góry.
- **Bardziej spójne odległe stworzenia po powrocie** — W trybie jednoosobowym odległe stworzenia znacznie rzadziej teleportują się lub pędzą w inne miejsce, gdy wracasz: podążają za prawdziwymi stworzeniami, dopóki gra je porusza, i znikają, gdy te zginą.

### Poprawki
- **Ekran Uproszczone byty bez migotania** — Dwa stworzenia o tej samej nazwie, takie jak osadnicy w różnych strojach, nie migoczą już, zamieniając się miejscami.
- **Czystsze obrazy stworzeń** — Odległe stworzenia pokazywane jako uproszczone obrazy nie mają już cienkich ciemnych linii ani czarnych plamek wzdłuż górnej lub dolnej krawędzi. Długie stworzenia, takie jak konie i niuchacze, nie są już ucinane przy pysku lub ogonie.
- **Naprawiono awarię ze stworzeniami GeckoLib** — Świat nie ulega już awarii kilka sekund po wczytaniu, gdy w oddali pojawiło się stworzenie z moda korzystającego z GeckoLib. Te stworzenia nie są jeszcze pokazywane w oddali. (Minecraft od 1.21.2 do 1.21.8.)
- **Szybsze Uproszczone byty** — Gra nie zwalnia już z uproszczonymi obrazami: te same stworzenia nie są już rysowane w kółko, a podobne do siebie dzielą jeden obraz.

## 0.5.0 — 2026-10-13

### Nowości
- **Uproszczone byty w Minecraft od 1.20.1 do 1.21.8** — Bardzo odległe stworzenia i gracze mogą teraz być pokazywani jako uproszczone obrazy także w tych wersjach, co znacznie odciąża komputer.

### Poprawki
- **Naprawiono awarię przy uruchamianiu** — Gra nie ulega już awarii przy uruchamianiu z zainstalowanym modem. (Forge, Minecraft od 1.20.1 do 1.20.4.)
- **Naprawiono awarię ekranu Uproszczone byty** — Otwarcie ekranu *Uproszczone byty* nie powoduje już awarii gry. (Minecraft 1.21.8 i starsze.)
- **Stworzenia obecne od chwili dołączenia** — Stworzenia, które były wokół ciebie w chwili dołączenia do świata, również pojawiają się teraz na horyzoncie, a nie tylko te spotkane później.

## 0.4.2 — 2026-10-12

### Poprawki
- **Stabilne stworzenia podczas oddalania się** — Stworzenie, które przemieszcza się poza twój zasięg renderowania, nie znika już na chwilę ani nie odwraca się w inną stronę.
- **Stworzenia całkowicie ukryte za wzgórzami** — Ubranie osadnika, trzymany przedmiot czy zewnętrzna warstwa szlamu nie prześwitują już przez wzgórza Voxy lub Distant Horizons, gdy reszta stworzenia jest ukryta. (Minecraft 1.21.9 i nowsze, z Distant Horizons albo z Voxy i shaderami.)

## 0.4.1 — 2026-10-11

### Poprawki
- **Stworzenia poprawnie oświetlone z shaderami** — Odległe stworzenia pokazywane jako uproszczone obrazy są teraz oświetlane przez słońce jak pełne modele, zamiast być zbyt ciemne z paczkami shaderów Iris, takimi jak Photon czy Complementary.
- **Naprawione cienie terenu** — Na odległych stworzeniach nie widać już cienia terenu za nimi, a woda Voxy wygląda tak, jak powinna. (Photon z Voxy.)
- **Stworzenia poprawnie ukryte za wzgórzami** — Odległe stworzenia nie prześwitują już przez wzgórza Voxy lub Distant Horizons. (Minecraft 26.1; Minecraft 26.2 i nowsze z shaderami, także ze starszymi wersjami Iris; Minecraft 26.3 z zainstalowanym Iris.)
- **Obsługa Voxy 0.2.18** — Mod znów działa z Voxy 0.2.18: odległe stworzenia stoją na jego terenie i nie prześwitują już przez jego wzgórza.
- **Stworzenia ukryte za właściwymi wzgórzami z Distant Horizons** — Odległe stworzenia nie są już ukrywane przez wzgórza, które nie znajdują się przed nimi. (Distant Horizons ze skalowaniem TAAU w Photonie.)

## 0.4.0 — 2026-10-10

### Nowości
- **Automatyczny pierwszy skan** — Gdy pierwszy raz wchodzisz do świata w trybie jednoosobowym, Living Horizon od razu szuka stworzeń w promieniu 32 chunków, aby horyzont tętnił życiem od samego początku. Promień skanu ustawia się teraz w chunkach, tak jak zasięg renderowania.
- **Ustawienia animacji** — Dwa nowe suwaki zatrzymują animację odległych stworzeń, które są zbyt małe na ekranie lub zbyt daleko, aby poprawić wydajność.
- **Profile jakości** — Niski, Normalny, Wysoki i Ultra jednym kliknięciem ustalają, ile odległych stworzeń i ptaków widzisz i jak daleko. Wysoki i Ultra są przeznaczone dla mocnych komputerów.

### Poprawki
- **Stworzenia zachowują kolory** — Bez shaderów odległe stworzenia nie robią się już na chwilę czarne, głównie w locie.

## 0.3.2 — 2026-10-09

### Poprawki
- **Stworzenia ukryte za właściwymi wzgórzami z Photonem** — Z Voxy i skalowaniem TAAU w Photonie odległe stworzenia znów są ukrywane przez właściwe wzgórza.

## 0.3.1 — 2026-10-08

### Poprawki
- **Wzgórza uwzględniane w starszych wersjach** — Odległe stworzenia chowają się teraz za wzgórzami Voxy i Distant Horizons, tak jak w nowszych wersjach, zamiast przez nie prześwitywać. (Minecraft od 1.20 do 1.21.4.)
- **Obsługa starszych wersji Distant Horizons** — Mod działa teraz z Distant Horizons 2.3 i nowszymi, a nie tylko z najnowszą wersją.

## 0.3.0 — 2026-10-07

### Nowości
- **Obsługa Vulkana** — Z rendererem Vulkan w grze odległe stworzenia również chowają się za wzgórzami, także za terenem Distant Horizons.

### Poprawki
- **Stworzenia widoczne przed terenem Distant Horizons** — Bez shaderów odległe stworzenia nie zanikają już w terenie Distant Horizons.
- **Koniec smug na granicy zasięgu renderowania** — Smugi, które pojawiały się tam, gdzie kończy się zwykły zasięg renderowania, zniknęły. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Nowości
- **Odległe stworzenia** — Zwierzęta, osadnicy i golemy żyją dalej na horyzoncie, poza twoim zasięgiem renderowania, pasąc się i wędrując.
- **Odlegli gracze** — Inni gracze dalej chodzą w oddali, ze swoim skinem, zbroją i wierzchowcem. Gracze, którzy się wylogują, śpią tam, gdzie odeszli.
- **Ptaki** — Stada, gęsi lecące kluczem, mewy nad wybrzeżem i nietoperze nocą ożywiają niebo.
- **Obsługa Distant Horizons** — Living Horizon działa z Distant Horizons, tak samo jak z Voxy.
- **Stworzenia ukryte za wzgórzami** — Odległe stworzenia za wzgórzem nie są rysowane, dzięki czemu gra działa płynnie.
- **Uproszczone obrazy w oddali** — Bardzo odległe stworzenia i gracze mogą być pokazywani jako uproszczone obrazy, znacznie lżejsze do rysowania, gdy jest ich wiele. Ta opcja jest domyślnie wyłączona.
- **Skan otoczenia** — W trybie jednoosobowym skan odczytuje stworzenia z chunków wokół ciebie, aby od razu pojawiły się na horyzoncie, bez konieczności wcześniejszego spotkania.
- **Ekran ustawień** — Przycisk *Living Horizon...* w menu Ustawienia otwiera ustawienia moda, ze światem wciąż widocznym w tle, a jeden przycisk przywraca wartości domyślne.
- **Quilt, NeoForge i Forge** — Living Horizon działa także na Quilt, NeoForge i Forge, od Minecraft 1.20 do 26.3.
- **Logo moda** — Living Horizon ma teraz logo na liście modów i na liście paczek danych.
