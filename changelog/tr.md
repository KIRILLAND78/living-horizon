# Living Horizon — Değişiklik günlüğü

## Sonraki sürüm

## 0.16.0 — 2026-11-05

### İstemci

#### Yenilikler
- **Lodistance arazisi desteği** — Uzaktaki canlılar ve oyuncular artık Lodistance'ın uzak arazisinin arkasında, herhangi bir tepenin arkasındaymış gibi gizlenir ve sisine karışır; uzaktaki canlılar da Voxy ve Distant Horizons'ta olduğu gibi onun zemininde yürür. (Minecraft 26.3, OpenGL ile, gölgelendiriciler olmadan.)
- **Far View arazisi desteği** — Uzaktaki canlılar artık görüş mesafesinin ötesinde gizlenmek yerine Far View'un uzak arazisinde görünür, onun üzerinde yürür ve tepelerinin arkasında gizlenir. (Forge ve NeoForge, Minecraft 1.20.1 ile 1.21.4 arası.)
- **Ecstatic arazisi desteği** — Uzaktaki canlılar artık görüş mesafesinin ötesinde kaybolmak yerine Ecstatic'in arazisinde belirir ve onun zemininde yürür. Tepelerinin arkasında doğru şekilde gizlenir, önlerinde görünür kalır ve sisine karışırlar. (NeoForge, Minecraft 1.21.1, tek oyunculu; sis için gölgelendiriciler olmadan.)
- **Gölgelendiricilerle aydınlatılan basit canlılar** — Photon'da *Normal Mapping* açıkken olduğu gibi normal haritalarını okuyan bir gölgelendirici paketiyle, uzaktaki canlıların basit görüntüleri artık düz ve daha karanlık görünmek yerine 3B modelleri gibi paket tarafından aydınlatılır: bir yüzü güneşte, öbürü gölgede. Bu görüntüler iki kat video belleği kaplar; yeni *Gölgelendirici Işığı* ayarı bunu kapatır. (Minecraft 1.21.6 ve sonrası.)
- **Çevrim dışı oyuncuları kenara itin** — Artık çevrim dışı oyuncuların üzerine yürüyerek onları yolunuzdan itebilirsiniz. Yalnızca sizin oyununuzda var oldukları için sizi asla geri itmezler.
- **Çevrim dışı oyuncular balık tutuyor** — Suya yakın çevrim dışı oyuncular artık su kenarında durup bir süre balık tutabilir. Aynı kıyı boyunca aynı anda yalnızca biri balık tutar.
- **Çevrim dışı oyuncular yağmurdan korunuyor** — Yağmur yağdığında bazı çevrim dışı oyuncular artık ulaşabilecekleri bir çatının ya da ağacın altına yürür ve yağmur dinene kadar orada bekler. Diğerleri her zamanki gibi devam eder.
- **Çevrim dışı oyuncular ateşin başında toplanıyor** — Akşamları çevrim dışı oyuncular artık ulaşabilecekleri bir kamp ateşinin ya da ocağın önüne gider ve her biri kendi yerinde durur.
- **Çevrim dışı oyuncular sohbet ediyor** — Birbirine yakın iki çevrim dışı oyuncu artık birbirine doğru yürüyüp bir süre yüz yüze sohbet edebilir. Aynı anda asla ikiden fazlası sohbet etmez ve aynı bölgede yalnızca bir sohbet olur.
- **Çevrim dışı oyuncular size bakıyor** — Çevrim dışı oyunculara yakından baktığınızda artık dönüp size bakarlar. Size dönük olan biri varken eğilirseniz o da eğilir.

#### İyileştirmeler
- **Çevrim dışı oyuncular için daha doğal geceler** — Çevrim dışı oyuncular artık yere uzanmak yerine ulaşabilecekleri bir yatakta uyur ya da ayakta kalır.
- **Uzaktaki gemiler daha net** — Shippy Ships'in yelkenli gemileri gibi uzun teknelerin ve araçların basit görüntüleri artık daha net. Kalyon gibi en uzunları da artık, performansa daha ağır gelen tam model olarak çizilmek yerine, kendi basit görüntülerine kavuşuyor.
- **Basit görüntülere daha yumuşak geçiş** — Uzaktaki canlılar ve oyuncular, basit görüntülerinin yerlerini aldığı mesafeye yaklaştıkça, geçiş anında birden dönmek yerine görüntünün onları gösterdiği açıya artık yavaş yavaş döner. 3B modelden görüntüye geçiş daha az fark edilir.
- **NeoForge 1.21.4 ve 1.21.11 sürümleri daha kolay bulunuyor** — Minecraft 1.21.4 ve 1.21.11 için NeoForge sürümleri artık beta olarak yayımlanıyor. Böylece CurseForge'da ve alfa sürümleri gizleyen başlatıcılarda görünürler.

#### Düzeltmeler
- **Far View ile canlılar tek kez görünüyor** — Far View'un 128 blok ile görüş mesafesi arasında kutu olarak çizdiği canlılar artık üst üste iki kez değil, yalnızca bir kez gösterilir. (Forge ve NeoForge, Minecraft 1.20.1 ile 1.21.4 arası.)
- **Forge'da çevrim dışı oyuncuların adları görünüyor** — Bir çevrim dışı oyuncuya nişan almak artık hiçbir şey göstermemek yerine adını ve en son ne zaman görüldüğünü gösterir. (Forge, Minecraft 1.20.1 ile 1.20.4 arası.)
- **Bobby ile daha iyi uyum** — Uzaktaki canlılar artık kaybolmak yerine, sunucunun onları gönderdiği mesafenin hemen ötesinde, Bobby'nin sakladığı arazinin üzerinde görünür kalır. Uzaktaki oyuncular da bu mesafenin dışına yürürken artık yarı yolda durmaz.
- **Shippy Ships kalyonu uzaktan görünüyor** — Shippy Ships'in kalyonu artık görüş mesafesinden çıkar çıkmaz kaybolmak yerine, modun diğer gemileri gibi uzakta bıraktığınız yerde görünür kalır.
- **Çevrim dışı oyuncular uzakta hareket etmeye devam ediyor** — Siz uzaklaştığınızda çevrim dışı oyuncular artık çevrim dışı oldukları yere geri yürümek ya da kıpırdamadan durmak yerine bulundukları yerde gezinmeye devam eder. Gezinmeye yer olmayan yerlerde birkaç adım atar, geri döner ya da bir süre otururlar.

## 0.15.0 — 2026-10-31

### Sunucu

#### Yenilikler
- **Keşfedilmemiş topraklarda canlılar** — Living Horizon artık sunucudaki oyunculara, dünyanın henüz kimsenin keşfetmediği bölgelere yerleştireceği canlıları, varsayılan olarak her birinin çevresinde 512 bloğa kadar gösterir. Konumları tohumdan tahmin edilir: oraya vardığınızda farklı olabilir ya da hiç olmayabilirler. Bu özellik sunucu ayarlarından kapatılabilir veya menzili değiştirilebilir.
- **Tohum paylaşımı** — Sunucular artık Living Horizon'ın dünya tohumunu modu kullanan oyuncularla paylaşmasına izin verebilir; böylece onların oyunu, dürbünle ya da *Canlıları Tahmin Et* ile, canlıları her yerde kendi başına tahmin edebilir. Tohumu bilmek dünyayla ilgili her şeyi ortaya çıkardığından bu seçenek varsayılan olarak kapalıdır.

#### İyileştirmeler
- **Elitra uçuşu uzaktan görünüyor** — Sunucu artık hangi oyuncuların elitrayla uçtuğunu paylaşıyor; böylece bu oyuncular uzak mesafede doğru şekilde süzülürken gösterilir.

### İstemci

#### Yenilikler
- **Living Horizon'a hızlı bağlantılar** — Modun ayarları artık Discord'un yanında Modrinth, CurseForge, Nexus Mods ve resmî web sitesine kısayollar sunuyor.
- **Keşfedilmemiş bölgelerde canlılar** — Henüz kimsenin keşfetmediği bölgeler artık boş değil: Living Horizon, dünyanın oraya hangi canlıları yerleştireceğini *Canlı Görüş Mesafesi* ayarının ulaştığı yere kadar tohumdan tahmin eder. Yalnızca tahmin oldukları için oraya vardığınızda farklı olabilir ya da hiç olmayabilirler. Tek oyunculuda ve modlu sunucularda kullanılabilir; *Tahmini Canlılar* onları gizler.
- **Dürbünle tahmini canlılar** — Dürbünle bakmak artık keşfedilmemiş bölgelerin tahmini canlılarını, nişan aldığınız her yerde, ne kadar uzak olursa olsun gösterir. Bir sunucuda, sunucunun tohumunu paylaşması gerekir.
- **Çevrenizdeki canlıları tahmin edin** — Yeni *Canlıları Tahmin Et* düğmesi, çevrenizdeki 1.024 yığına kadar tahmini canlıları bir anda gösterir ve sonraki oturumlarınız için saklar. Bir sunucuda, sunucunun tohumunu paylaşması gerekir.
- **Elle girilen değerler** — Modun ayarlarında *Sınırsız* ile biten kaydırıcılar (en fazla sayılar ve görüş mesafeleri) artık son adımlarının ötesinde bile klavyeyle yazılan bir değeri kabul ediyor ve Sodium'un sayfası da bunu gösteriyor. Tam bir değer girmek için kaydırıcıya sağ tıklayın; ok tuşları da her kaydırıcıyı adım adım hareket ettirir.
- **Dünyada mesafe önizlemesi** — Bir mesafe ayarının üzerine geldiğinizde ya da onu değiştirdiğinizde sınırı artık doğrudan dünyada, menünün arkasında gösterilir. Seçilen değerin ne kadar uzağa ulaştığını bir bakışta görebilirsiniz.

#### İyileştirmeler
- **İsteğe bağlı basitleştirme mesafesi** — *Basitleştirme Mesafesi* artık kapatılabilir. Canlılar, varlıklar, oyuncular ve kuşlar bu durumda yalnızca ekranda yeterince küçük göründüklerinde basit görüntülere dönüşür.
- **4.000'e kadar uzak canlı** — *En Fazla Canlı* artık *Sınırsız* seçeneğinden önce 4.000'e kadar çıkıyor.
- **Sınırsız kaydırıcının sonunda** — *Sınırsız* artık görüş mesafesi kaydırıcılarının solunda değil, 4.000 bloktan sonra sağ ucunda yer alıyor. Kaydırıcılar artık en kısa mesafeden en uzuna doğru ilerliyor.
- **Sınırsız öncesinde onay** — Oyun artık canlı, varlık ya da çevrim dışı oyuncu sayısını veya bir görüş mesafesini *Sınırsız* yapmadan önce onay istiyor. Bu değerler kare hızını ciddi şekilde düşürebilir, hatta oyunu çökertebilir. Sodium'un sayfasında soru *Uygula* ile birlikte gelir.
- **Daha makul varsayılan sınırlar** — Performansta büyük düşüşleri önlemek için artık hiçbir ayar varsayılan olarak sınırsız değil: *Oyuncu Görüş Mesafesi*, *Animasyon Mesafesi* ve *Varlık Görüş Mesafesi* 4.000 blokta durur. *Yüksek* ve *Ultra* hazır ayarları en fazla 1.500 ve 3.000 canlı gösterir, *Ultra* 2.048 bloğa kadar; eski varsayılanlarda kalan ayarlar yenilerine geçer.
- **Daha doğal elitra uçuşu** — Elitrayla uçan uzaktaki oyuncular artık havada dimdik koşmak yerine kanatları açık, gerçek bir uçuş duruşunda gösterilir.
- **Kaynak paketi değişiklikleri daha akıcı** — Bir kaynak paketi kapatılıp yeniden açıldığında canlıların basit görüntüleri artık gereksiz yere yeniden oluşturulmuyor. Kaynakların yüklenmesi de artık bunlar aranırken oyunu dondurmuyor.
- **Ice and Fire'ın uçan canlıları her açıdan görünüyor** — Ice and Fire'ın ejderhaları, amfiterleri ve hipogrifleri artık yalnızca yandan değil, kâbuslar ve uçaklar gibi alttan ve üstten görülen basit görüntülere de sahip. Tepenizden uçarken ya da onlara yukarıdan baktığınızda doğru görünürler.
- **Uzaktaki dev canlılar daha net** — Çok büyük canlılar artık ekranda hâlâ büyük göründükleri sürece köşeli bir basit görüntüye dönüşmek yerine tam 3B modellerini korur.

#### Düzeltmeler
- **Uzaktaki canlılar uzak arazide görünüyor** — Tahmini olanlar dahil uzaktaki canlılar artık altlarında hiçbir şey yokmuş gibi gizlenmek yerine uzak arazide görünür kalır: bir dünya açıldıktan sonraki bir süre boyunca, binlerce tahmini canlının arasında ya da Voxy veya Distant Horizons geç yanıt verdiğinde.
- **Canlılarda Distant Horizons sisi** — Uzaktaki canlılar artık manzaranın önünde keskin biçimde göze batmak yerine Distant Horizons arazisiyle aynı sisi alır ve onunla birlikte solar. (Gölgelendiriciler olmadan.)
- **Ice and Fire canlıları doğru basitleştiriliyor** — Uzaktaki Ice and Fire ejderhaları artık bacaklarının ya da bir kanadının yakın çekimi yerine gerçek boyutlarında, bütün basit görüntülere sahip. Farklı yaşlardaki ejderhalar artık tek bir görüntüyü paylaşmıyor ve deniz yılanları kesik bir görüntü yerine 3B modellerini koruyor.
- **Çevrim dışı oyuncular teknelerini koruyor** — Suyun ortasında çevrim dışı olan bir oyuncu, siz uzaklaştığınızda artık içinde dinlendiği tekneyi kaybetmiyor: tekneyi yakında da uzakta da, bir sonraki oyununuzda da korur.
- **Çevrim dışı oyuncular artık havada asılı kalmıyor** — Havadayken, elitrayla süzülürken ya da düşerken çevrim dışı olan bir oyuncu, siz uzaklaştığınızda artık oraya geri çıkmıyor: altındaki zeminde dinlenir. Voxy veya Distant Horizons ile siz yaklaşmadan önce bile zemindedir.
- **Uzaktaki canlılar doğal şekilde dinleniyor** — *Animasyon Mesafesi* ötesindeki ya da *Animasyon Eşiği* değerinden küçük olan uzaktaki canlılar artık bacakları kıpırdamadan ileri geri kayıp titremek yerine bulundukları yerde durur. Yeterince yaklaştıklarında durdukları yerden yürümeye devam ederler.

## 0.14.0 — 2026-10-29

### İstemci

#### Yenilikler
- **Modlu araçlar uzaktan görünüyor** — Immersive Aircraft'ın uçakları gibi diğer modların araçları artık tıpkı tekneler gibi görüş mesafesinin ötesinde bırakıldıkları yerde görünür kalır. Vagonlar gibi yönlendirmeden bindiğiniz araçlar varsayılan olarak kapalıdır ve *Canlılar ve Varlıklar...* ekranından açılabilir.
- **Varlıklara özel ayarlar** — Tekneler, araçlar ve mankenler artık canlılardan ayrı, kendi ayarlarına sahip: gösterilip gösterilmeyecekleri, kaç tane ve ne kadar uzağa kadar gösterilecekleri. Görüş mesafeleri varsayılan olarak sınırsızdır.
- **Canlılar ve varlıklar için ayrı basitleştirme** — *Basit Varlıklar* artık iki seçenek: *Basit Canlılar* ve *Basit Varlıklar*. Tekneler, araçlar ve mankenler de artık uzakta, yukarıdan ya da aşağıdan görüldüklerinde dahil, basit görüntülere dönüşebilir. *Basit Oyuncular* artık bağımsız ve resimler ekranının adı artık *Resim Önizleme...*; varlıklar için de bir sekmesi var.
- **Oyun içi değişiklik günlüğü** — Modun ayarlarındaki yeni bir düğme, oyundan çıkmadan her sürümde nelerin değiştiğini gösterir. Sodium'un sayfasında da bulunur.

#### İyileştirmeler
- **Uyumsuz sunucularda bildirim** — Living Horizon olmayan ya da sürümü çok eski veya çok yeni olan bir sunucuya katıldığınızda, artık bir sohbet mesajı uzaktaki oyuncuların orada gösterilmeyeceğini size bildirir. *Oyuncuları Göster* kapalıysa mesaj görünmez.
- **İki sekmede Canlılar ve Varlıklar** — *Canlılar ve Varlıklar...* ekranı artık canlıları ve diğer varlıkları iki sekmeye ayırarak bulmayı kolaylaştırıyor. Oradaki tekneler ve araçlar yalnızca gösterilir ya da gizlenir.
- **Canlı seçimleri anında uygulanıyor** — *Canlılar ve Varlıklar...* ekranında bir türü göstermek ya da gizlemek artık menüyü kapatmadan, arkadaki dünyada hemen etkili olur.
- **Daha düzenli canlı ayarları** — *Uzaktaki Canlılar ve Varlıklar* ayarları artık üç bölüme ayrılıyor: *Genel*, *Canlılar* ve *Varlıklar*.
- **Biniciler uzaktan daha aslına uygun** — *Basit Oyuncular* açıkken bir teknede, vagonda ya da uçakta veya bir mutlu ghastın üzerinde bulunan uzaktaki bir oyuncu, artık her zaman yandan görünmek yerine yukarıdan ve aşağıdan doğru şekilde gösterilir.
- **Havada kalan uçaklar** — Uçuşun ortasında çevrim dışı olan bir oyuncu artık uçağıyla havada kalır; uçak, duruyor mu yoksa hareket halinde mi olduğuna göre havada asılı durur ya da daireler çizer.
- **Resimlerin kaynağı gösteriliyor** — *Resim Önizleme* ekranı artık her resmin nereden geldiğini (paylaşılan, önceki bir oturumdan yeniden kullanılan, yeni ya da kaydedilmemiş), ne zaman ve hangi dünyada oluşturulduğuyla birlikte gösteriyor.
- **Oturan evcil hayvanlar oturmaya devam ediyor** — Oturması söylenen kediler, kurtlar ve papağanlar artık kalkıp uzaklaşmak yerine uzakta oturmaya devam eder.

#### Düzeltmeler
- **Canlılar ve Varlıklar çökmesi düzeltildi** — *Canlılar ve Varlıklar...* ekranını ana menüden açmak artık oyunu çökertmiyor. (Minecraft 26.x.)
- **Modlu araçlar renklerini koruyor** — Immersive Aircraft'ın uçakları gibi bazı modların uzaktaki araçları ve canlıları, siz yaklaşırken artık bir anlığına kararmak yerine renklerini korur.
- **Binekler yalnızca bir kez görünüyor** — Uzaktaki bir oyuncunun modlu bir uçak gibi bineği ya da aracı artık yanında ikinci kez belirmiyor.

## 0.13.0 — 2026-10-28

### İstemci

#### İyileştirmeler
- **Uzaktaki canlılarla daha akıcı oyun** — Living Horizon artık uzaktaki canlıların ve çevrim dışı oyuncuların nerede yürüyebileceğini oyunu yavaşlatmadan arka planda hesaplıyor.
- **Voxy ve Distant Horizons ile daha akıcı görüntü** — Uzaktaki canlılar Voxy'nin ya da Distant Horizons'ın arazisinde gösterildiğinde oyun her karede grafik sürücüsünü daha az bekliyor. Kazanç en çok *Threaded optimization* açık NVIDIA kartlarında görülür.
- **Uzakta uçan kâbuslar ve ghastlar** — Uzaktaki kâbuslar artık havada hareketsiz asılı kalmak yerine gökyüzünde daireler çizer, ghastlar da yavaşça süzülür.
- **Alttan görülen kâbuslar ve ghastlar** — Basit görüntü olarak kâbuslar ve ghastlar artık yalnızca yandan değil, kuşlar gibi alttan ve üstten de gösteriliyor.
- **Çok sayıda uzak canlıyla daha akıcı** — Oyun artık her karede her uzak canlı için boşa zaman harcamıyor. (Minecraft 26.2 ve sonrasında Vulkan, Minecraft 26.3'te Distant Horizons.)
- **Her canlı uzakta görünüyor** — Canavarlar, yarasalar, balıklar ve diğer tüm canlılar artık varsayılan olarak uzakta gösteriliyor ve hayvanlar gibi dolaşıyor. Örneğin Endermanlar End boyunca görünür kalır. Gizlediğiniz hayvanlar gizli kalır.

#### Düzeltmeler
- **Ayar düğmeleri yeniden düzende** — *Uzaktaki Canlılar ve Varlıklar* ekranının altındaki düğmeler artık üst üste binmiyor ve *Varsayılana Sıfırla* yeniden *Bitti* düğmesinin solunda. (Minecraft 1.20.1.)
- **Boyut değiştirirken donma düzeltildi** — Bir geçitten geçtiğinizde ya da bir dünyadan ayrıldığınızda oyun artık tamamen donmuyor. (Distant Horizons ile.)

## 0.12.1 — 2026-10-27

### İstemci

#### Düzeltmeler
- **Kıyaslama komutu teste ayrıldı** — `/livinghorizon benchmark` artık oyunculara sunulmuyor: yalnızca modu test etmek için tasarlandı.

## 0.12.0 — 2026-10-23

### Sunucu

#### Yenilikler
- **Sunucu ayarları** — Sunucu sahipleri artık Living Horizon'ın neyi paylaşacağını seçebilir: çevrim içi oyuncular, çevrim dışı oyuncular ve canlılar, hangi mesafeye kadar, isterseniz yalnızca takım arkadaşları arasında ve hangi oyuncuların ya da boyutların asla paylaşılmayacağı. Ayarlar `config/livinghorizon-server.json` dosyasında bulunur ve yeniden başlatma gerekmeden birkaç saniye içinde uygulanır.

#### Düzeltmeler
- **Forge sunucularına katılım düzeltildi** — Modu kullanan oyuncular artık modun yüklü olduğu bir Forge sunucusundan "mismatched mod channel list" mesajıyla atılmıyor. (Forge 1.20.2 ve sonrası.)
- **Seyirciler ve görünmez oyuncular gizli kalıyor** — Seyirci modundaki ya da görünmezlik etkisindeki oyuncular artık diğer oyunculara uzakta görünmüyor, çevrim dışı oldukları yerde de gösterilmiyor.

### İstemci

#### Yenilikler
- **Discord düğmesi** — Modun ayarları artık soru sormak ya da bir sorunu bildirmek için tek tıkla Living Horizon'ın Discord'una katılmanızı sağlıyor. Düğme Sodium'un sayfasında da bulunur.
- **Mod sürümü gösteriliyor** — Ayar ekranları ve hata ayıklama paneli artık Living Horizon sürümünü gösteriyor; bir hata raporuna eklemek için kullanışlıdır.
- **Basitleştirme Boyutu** — Yeni bir ayar, uzaktaki canlıları, oyuncuları ve kuşları, *Basitleştirme Mesafesi* gelmeden bile, ekranda belirli bir boyuttan (varsayılan olarak 10 piksel) küçük görünür görünmez basit görüntülere dönüştürür. Zar zor görünen her şeyin çizimi çok daha hafif hale gelir.

#### İyileştirmeler
- **Yüzlerce uzak canlıyla daha akıcı** — *Gizli Canlıları Atla*, ekranda çok sayıda uzak canlı ve oyuncu varken çok daha az yük getiriyor ve Living Horizon arka planda daha az iş yapıyor.
- **Minecraft 1.21.1 sürümleri daha kolay bulunuyor** — Minecraft 1.21 ve 1.21.1 için Fabric, Quilt ve NeoForge sürümleri artık beta olarak yayımlanıyor. Böylece CurseForge'da ve alfa sürümleri gizleyen başlatıcılarda görünürler.
- **Uzaktaki kalabalıklarla daha az takılma** — Uzaktaki canlılar, oyuncular ve kuşlar artık her karede oyundan çok daha az bellek istiyor; bu da takılmaları azaltıyor.
- **Çok sayıda kuşla daha akıcı** — Arkanızdaki ya da görülemeyecek kadar küçük kuşlar artık çizilmiyor.

#### Düzeltmeler
- **Uzaktaki canlılar her zaman sağlam zeminde** — Uzaktaki canlılar artık altlarında henüz arazi görünmeyen yerlerde çizilmiyor; ister oyunun, ister Voxy'nin, ister Distant Horizons'ın arazisi olsun. Artık boşlukta süzülmüyorlar.
- **Forge'da tek oyunculu dünyalar yeniden açılıyor** — Tek oyunculu bir dünya artık açılır açılmaz "mismatched mod channel list" mesajıyla kapanmıyor. (Forge 1.20.2 ve sonrası.)
- **Uzaktaki canlılar Distant Horizons arazisinde kalıyor** — Tek oyunculuda uzaktaki canlılar artık Distant Horizons'ın tepelerinde ve dağlarında kaybolmuyor ya da yürümeyi bırakmıyor.

## 0.11.0 — 2026-10-22

### Sunucu

#### İyileştirmeler
- **Büyük sunucularda daha hafif** — Sunucu, her oyuncuya hangi görünümlerin ve bineklerin gönderileceğini seçmek için daha az zaman harcıyor. Uzun çevrim dışı oyuncu listeleri artık oyun için fazla büyük tek bir mesaj yerine birkaç parça halinde gönderiliyor.
- **Oyuncular için güncelleme gerekli** — Bu sürümü çalıştıran bir sunucuda, uzaktaki oyuncuları görmek için oyuncuların da bu sürüme ihtiyacı var.

#### Düzeltmeler
- **Çevrim dışı oyuncu dosyası korunuyor** — Sunucu `livinghorizon-positions.dat` dosyasını okuyamazsa dosyanın üzerine artık yazılmıyor: dosya `livinghorizon-positions.dat.bad` olarak saklanır ve konsol bunu bildirir.

### İstemci

#### Yenilikler
- **Kıyaslama** — `/livinghorizon benchmark` komutu, modun her bölümünün dünyanızda ne kadar yük getirdiğini ölçer ve bir hata raporuna eklenecek bir rapor oluşturur.
- **Kıyaslama dünyası** — `/livinghorizon benchmark world`, canlılar ve çevrim dışı oyuncularla dolu, her zaman aynı olan bir test dünyası oluşturur; böylece ölçümler bir çalıştırmadan diğerine karşılaştırılabilir. Kendi dünyanıza dokunulmaz.
- **En Fazla Çevrim Dışı** — Yeni bir ayar, aynı anda kaç çevrim dışı oyuncunun gösterileceğini en yakından başlayarak sınırlar (varsayılan olarak 256). Diğerleri siz yaklaştıkça yeniden belirir.

#### İyileştirmeler
- **Sürümler farklı olduğunda daha net mesaj** — Sunucudaki mod sizinkine göre çok eski veya çok yeniyse günlük artık sunucuda mod olmadığını iddia etmek yerine hangisinin güncellenmesi gerektiğini söylüyor.
- **Çok sayıda çevrim dışı oyuncuyla daha akıcı** — Çevrim dışı oyuncuları uzakta göstermek daha az yük getiriyor ve tepelerin arkasında kalanlar artık çizilmiyor.

#### Düzeltmeler
- **Oyuncu görüş mesafesine uyuluyor** — Çevrim dışı oyuncular artık *Oyuncu Görüş Mesafesi* ötesinde gösterilmiyor.
- **Uzaktaki mankenler gerçek görünümleriyle** — Uzaktaki mankenler artık Alex'e dönüşmek yerine, ister bir oyuncunun ister bir kaynak paketinin olsun, yakından taşıdıkları görünümü korur. (Minecraft 1.21.9 ve sonrası.)
- **Photon ve Distant Horizons ile su korunuyor** — *Uzak Arazide Gizle* açıkken, uzaktaki bir canlı ya da oyuncu görüş alanındayken su artık çıplak, gölgeli tabanını ortaya çıkarmak yerine normal kalıyor. (Minecraft 26.3.)
- **Manken pelerinleri doğal şekilde sarkıyor** — Uzaktaki mankenlerin pelerinleri artık havaya dikilmek yerine yakındaki gibi aşağı sarkıyor. (Minecraft 1.21.9 ve sonrası.)
- **Ayar dosyası korunuyor** — `livinghorizon.json` dosyasını elle düzenlerken yapılan bir hata artık dosyayı silmiyor: dosya `livinghorizon.json.bad` olarak saklanır ve günlük bunu bildirir.
- **Kayıtlar korunuyor** — Oyun kayıt sırasında kapanırsa hatırlanan canlıların (Living Horizon'ın uzakta göstermeye devam ettiği canlılar) ve çevrim dışı oyuncuların dosyaları artık bozulmuyor. Bozuk bir dosya boşaltılmak yerine `.bad` olarak saklanır.
- **Büyük sunuculara daha akıcı katılım** — Çok sayıda çevrim dışı oyuncusu olan bir sunucuya katılmak artık oyunu takıltmıyor.
- **Bozuk görüntüler otomatik olarak yeniden oluşturuluyor** — Kaydedilmiş bozuk bir basit görüntü artık oyunun belleğinin tükenmesine yol açamaz: yalnızca yeniden oluşturulur.
- **Oculus ile uzaktaki canlılar Distant Horizons arazisinin önünde görünüyor** — Oculus ve bir gölgelendirici paketiyle uzaktaki canlılar artık Distant Horizons arazisinin arkasında kaybolmuyor; bu sorun görüş mesafesi arttıkça kötüleşiyordu. (Forge.)
- **Entity Culling ile doğal baş hareketleri** — Uzaktaki canlılar ve oyuncular, Entity Culling tek başına ya da Chloride ile birlikte kullanıldığında artık başlarını çok hızlı sallamıyor.
- **Oyuncular doğru modlu boyutta** — Aether gibi modlu bir boyuttaki çevrim içi ve çevrim dışı oyuncular artık Twilight Forest gibi başka bir boyutta gösterilmiyor. Bir sunucuda, sunucunun da bu sürüme ihtiyacı var.
- **Canlılarda Voxy sisi** — Voxy'nin arazisindeki uzak canlılar artık belli bir mesafeden sonra sis rengine bürünmek yerine çevrelerindeki manzarayla aynı sisi alıyor. (Gölgelendiriciler olmadan.)

## 0.10.0 — 2026-10-20

### Sunucu

#### Yenilikler
- **Sunucularda Living Horizon** — Mod artık sunucuya da yüklenebilir: jar dosyasını sunucunun `mods` klasörüne koyun (Fabric, Quilt, NeoForge veya Forge; Fabric ve Quilt'te Fabric API ile birlikte). Hiçbir kurulum gerekmez ve veri paketinin yerini alır.
- **Oyuncular gerçek zamanlı paylaşılıyor** — Sunucu, her oyuncunun konumunu görünümü, zırhı, elindeki eşyalar ve bineğiyle birlikte saniyede beş kez modu kullanan oyunculara gönderir.
- **Canlılar paylaşılıyor** — Sunucu, her oyuncunun 2.048 blok çevresindeki canlıları, o oyuncunun hiç karşılaşmadıkları da dahil, eyerleri, dizginleri, zırhları ya da sandıklarıyla birlikte gönderir: hayvanlar, köylüler, golemler, huzurverenler ve tekneler.
- **Çevrim dışı oyuncular paylaşılıyor** — Sunucu her oyuncunun nerede çevrim dışı olduğunu, dış görünüşünü, bineğini ve ne zaman ayrıldığını hatırlar ve bunu sonradan katılanlar dahil herkese iletir.
- **Kurulumdan önceki oyuncular** — İlk başlatmada sunucu, dünyanın zaten tanıdığı oyuncuları bulur ve onları teçhizatlarıyla birlikte ayrıldıkları yerde çevrim dışı oyuncular olarak gösterir.
- **Dünyayla birlikte kaydediliyor** — Her şey dünya klasöründeki `livinghorizon-positions.dat` dosyasına kaydedilir. Bir canlı, yığını yüklendiğinde orada değilse unutulur.
- **Yerel ağ dünyaları** — Yerel ağa açılan bir dünya, başka hiçbir şey kurmaya gerek kalmadan her şeyi ev sahibinin oyunu üzerinden paylaşır.
- **Modu olmayan oyuncular da katılabilir** — Modu olmayan oyuncular yine de katılabilir; sunucu onlara hiçbir şey göndermez.
- **İki tarafta aynı sürüm** — Modu hem sunucuda hem de oyuncuların oyunlarında güncel tutun: sunucunun mesajlarını anlayamayan bir oyun onları yok sayar.
- **Gizlilik: konumlar paylaşılır** — Sunucu, her oyuncunun konumunu modu kullanan tüm oyuncularla, mesafe fark etmeksizin paylaşır. Oyuncularınızı bu konuda bilgilendirmeyi unutmayın.
- **Artık veri paketi yok** — Veri paketi artık kullanılmıyor: onu dünyanın `datapacks` klasöründen kaldırın. Koordinatlar artık skor tablosundan geçmediği için skorlarda ya da kenar panelinde görünmüyor.

#### Düzeltmeler
- **Dünya kapandığında oyuncular korunuyor** — Bir sunucu ya da yerel ağ dünyası kapandığında, hâlâ içinde olan oyuncular artık kaybolmak yerine bulundukları yerde çevrim dışı oyuncular olarak kalıyor.

### İstemci

#### Yenilikler
- **Her oyuncu, her mesafede** — Modlu bir sunucuda artık diğer tüm oyuncuları, nerede olurlarsa olsunlar, görünümleri, zırhları, ellerindeki eşyalar ve binekleriyle görürsünüz.
- **Hiç karşılaşmadığınız canlılar** — Modlu bir sunucuda uzaktaki canlılar, yakınından hiç geçmediğiniz canlılar dahil, eyerleri, dizginleri, zırhları ya da sandıklarıyla hemen belirir.
- **Katılır katılmaz görünen çevrim dışı oyuncular** — Çevrim dışı oyuncuları, siz gelmeden önce ayrılmış olsalar bile, dış görünüşleri ve binekleriyle ayrıldıkları yerde görürsünüz.
- **Tek oyunculuda çevrim dışı oyuncular** — Tek oyunculuda dünyanızın çevrim dışı oyuncuları, mod yüklenmeden öncekiler dahil, dünyayı açar açmaz belirir.
- **Baştan itibaren yaşayan bir ufuk** — Uzaktaki canlılar ve çevrim dışı oyuncular birkaç saniye sonra değil, katılır katılmaz belirir.
- **Modu olmayan sunucular** — Bu sunuculara yine de katılabilirsiniz. Orada yalnızca daha önce karşılaştığınız canlılar gösterilir, uzaktaki oyuncular gösterilmez ve günlük bunu bir kez belirtir.

#### İyileştirmeler
- **Uzaktaki oyuncuların konumları daha güvenilir** — Uzaktaki oyuncular artık yalnızca sunucunun paylaştığı konumlara göre yerleştiriliyor. Konum çubuğuna dayalı tahmin, *Kayıp Oyuncu Süresi* ayarıyla birlikte kaldırıldı.

#### Düzeltmeler
- **Mutlu ghastlar doğru gösteriliyor** — Mutlu ghasta binen uzaktaki bir oyuncunun yanında artık ikinci bir ghast olmuyor ve siz katıldığınızda zaten binmekte olan biri artık havada asılı kalmıyor.
- **Mutlu ghastlar bırakıldıkları yerde kalıyor** — Uzaktaki bir oyuncu mutlu ghastından indiğinde ghast artık ilk binildiği yerde yeniden belirip geri uçmak yerine bırakıldığı yerde kalıyor.
- **Yeni görünümler uzaktan görünüyor** — Görünümünü değiştiren uzaktaki bir oyuncu artık uzakta eskisini korumak yerine yenisiyle görünüyor.
- **Oyuncular teknelerde düzgün oturuyor** — Teknedeyken çevrim dışı olan bir oyuncu artık yan oturmak yerine tekneyle aynı yöne bakıyor.
- **Biniciler düzgün oturuyor** — Ata binen uzaktaki bir oyuncu artık, ister tam model ister basit görüntü olarak, atın içine gömülmek ya da sağrısına oturmak yerine doğru şekilde oturuyor.
- **Çevrim dışı olduktan sonra tek at** — At üzerinde çevrim dışı olan bir oyuncu artık oturduğu atın yanında atın bir kopyasını bırakmıyor.
- **Website ve Issues düğmeleri çalışıyor** — Mod Menu'deki mod sayfasında bulunan Website ve Issues düğmeleri artık gri ve devre dışı görünmüyor. (Fabric.)

## 0.9.0 — 2026-10-19

### Düzeltmeler
- **Çevrim dışı oyuncular sudan çıkıyor** — Yüzerken çevrim dışı olan oyuncular artık suyun içinde ayakta bırakılmıyor: en yakın kıyıda bekliyor ya da kıyı 10 bloktan uzaktaysa bir teknede kürek çekiyorlar.
- **Teknelerin çevresinde su bozulmuyor** — Photon gibi bir gölgelendirici paketiyle, kürek çeken çevrim dışı oyuncuların çevresinde su artık saydamlaşmıyor ve uzaktaki teknelerin çevresinde geniş bir kare halinde kaybolmuyor.
- **Kürek çekenler teknelerini koruyor** — Uzakta kürek çeken çevrim dışı oyuncular artık basit görüntüleri oluşturulurken teknesiz duruyormuş gibi görünmek yerine teknelerini koruyor.

## 0.8.0 — 2026-10-18

### Yenilikler
- **Basit Oyuncular** — Yeni bir ayar, uzaktaki canlılar basit görüntülere dönüşmeye devam ederken uzaktaki oyuncuları tam 3B model olarak tutmanızı sağlar.
- **12 yeni dil** — Living Horizon artık Almanca, İspanyolca, İtalyanca, Brezilya Portekizcesi, Lehçe, Türkçe, Rusça, Ukraynaca, Basitleştirilmiş ve Geleneksel Çince, Japonca ve Korece olarak kullanılabilir.
- **Sodium'da ayarlar** — Living Horizon'ın ayarlarına artık Sodium'un *Görüntü Ayarları* içinden, kendi sayfalarında ulaşılabilir. (Sodium 0.8 ve sonrası.)
- **Binekleriyle çevrim dışı oyuncular** — At üzerinde, teknede ya da mutlu ghastın üzerinde çevrim dışı olan bir oyuncu, ayrıldığı yerin çevresinde ona binmeye devam eder.
- **Video belleği denetimi** — *Basit Varlıklar...* ekranı, canlı ve oyuncu görüntülerinin ne kadar video belleği kullandığını gösterir ve iki yeni ayar her biri için bir sınır belirler.

### İyileştirmeler
- **Daha anlaşılır ayarlar** — Her ayarın artık Minecraft'ın kendi seçenekleri tarzında daha anlaşılır bir adı ve açıklaması var; mesafe kaydırıcıları da ekranın tüm genişliğini kullanıyor.
- **Daha hareketli çevrim dışı oyuncular** — Çevrim dışı oyuncular artık ayrıldıkları yerin çevresinde küçük bir tur atıyor. Uzakta, ister ayakta ister oturuyor, uzanıyor ya da yatakta olsunlar, çevrim içi oyuncular gibi basit görüntülere dönüşüyorlar.
- **Daha hafif biniciler** — Uzakta, ata, tekneye, vagona ya da başka bir bineğe binen bir oyuncu artık bineğiyle birlikte, iki tam modelden çok daha hafif tek bir basit görüntü oluşturuyor.
- **Büyük oyuncu kalabalıkları daha akıcı** — Yaklaşık yüz uzak oyuncunun ötesinde, fazladan olanlar artık tam model olarak kalmak yerine onlar da basit görüntülere dönüşüyor.

### Düzeltmeler
- **Pencere boyutu değiştikten sonra doğru gizlenen canlılar** — Oyun penceresi yeniden boyutlandırıldıktan sonra uzaktaki canlılar artık Voxy'ninkiler ve Distant Horizons'ınkiler dahil tepelerin içinden görünmüyor.
- **Distant Horizons ile oyuncular ve balçıklar görünüyor** — Uzaktaki oyuncular, çevrim dışı oyuncular ve balçıklar artık Distant Horizons arazisinin altında kaybolmuyor. (Minecraft 26.1 ve sonrası.)
- **Distant Horizons arazisinin önünde bütün canlılar** — Distant Horizons arazisi artık önünde duran canlıları ve oyuncuları kesmiyor. (Minecraft 26.1 ve sonrası.)
- **Görüntü sarsılmasında sabit canlılar** — Görüntü sarsılması açıkken uzaktaki canlılar artık titremiyor ve Distant Horizons arazisi her adımda önlerinde dalgalanmıyor. (Minecraft 26.1 ve sonrası.)

## 0.7.0 — 2026-10-17

### Yenilikler
- **Çevrim dışı oyuncular hayat buluyor** — Çevrim dışı oyuncular artık ayrıldıkları yerin çevresinde yaşıyor: geziniyor, oturuyor, sandıklara ve çalışma masalarına bakıyor, geceleri yatağa gidiyor ve vurulduklarında ya da canavarlar yaklaştığında kalkanlarını kaldırıyorlar. Her zaman yerde kalırlar: altlarındaki bloğu kırarsanız düşerler, onları gömerseniz yeniden yukarı tırmanırlar.
- **"(çevrim dışı)" etiketi** — Çevrim dışı oyuncuların adlarının ardında *(çevrim dışı)* yazar ve birine yakından baktığınızda en son ne zaman görüldüğünü öğrenirsiniz. Adları ya da çevrim dışı oyuncuların kendileri ayarlardan gizlenebilir.
- **3B kuşlar** — Kuşlar artık Minecraft'ın canlıları gibi küçük 3B modeller: kanat çırpıyor, tünediklerinde bacaklarının üzerinde duruyor ve uzakta basit görüntülere dönüşüyorlar. *Kuş Stili* ayarı eski 2B kuşları geri getirir.
- **Hmm…** — Gökyüzünde bir şey gördüm sanki. 😉

### İyileştirmeler
- **Zıplayan canlılar** — Uzaktaki balçıklar, magma küpleri ve tavşanlar artık kaymak yerine zıplayarak ilerliyor. Balçıklar yere indiklerinde yassılıyor, tavuklar da bir blok aşağı inerken kanat çırpıyor.
- **Daha doğal ördekler** — Ördekler artık siz yaklaştığınızda temelli gitmek yerine birlikte havalanıyor ve suyun daha ilerisine konuyor.

### Düzeltmeler
- **Dürbünle bakıldığında hareketli canlılar** — Dürbünle görülen uzaktaki canlılar artık yakınlaştırılmış boyutlarına göre değerlendiriliyor ve büyük göründüklerinde donmak yerine yürümeye devam ediyor. (*Animasyon Eşiği* ayarı.)
- **Dünya açılırken hareketli canlılar** — Uzaktaki canlılar artık olduğu yerde dönmek yerine baştan itibaren yürüyor ve otluyor.
- **Daha doğal tüneyen kuşlar** — Baştankaralar, kızılgerdanlar ve ördekler artık ileri geri fırlamıyor, havada asılı kalmıyor, yaprakların içinde durmuyor ya da kuru toprakta yüzmüyor. Ördekler artık hemen yanınızda belirip uçup gitmiyor ve göletlerinin çevresinde yüzüyor; kızılgerdanlar ve baştankaralar daha çok sekiyor, daha az uçup gidiyor.
- **Mod listesinde doğru lisans** — Living Horizon artık mod listesinde doğru lisansı gösteriyor. (Forge, NeoForge ve Quilt.)

## 0.6.0 — 2026-10-15

### Yenilikler
- **Canlı görüntüleri saklanıyor** — Uzaktaki canlıların basit görüntüleri artık diske kaydediliyor ve bir oturumdan diğerine yeniden kullanılıyor. Yalnızca kaynak paketleri, modlar ya da oyun sürümü değiştiğinde yeniden oluşturuluyor.

### İyileştirmeler
- **Yeni simge** — Living Horizon'ın yeni bir piksel sanatı simgesi var: önde Steve, çevresinde dağlara kadar uzanan canlılar, köylüler, oyuncular ve kuşlar.
- **Geri döndüğünüzde daha tutarlı uzak canlılar** — Tek oyunculuda uzaktaki canlılar, geri döndüğünüzde artık çok daha seyrek ışınlanıyor ya da başka bir yere koşuyor: oyun gerçek canlıları hareket ettirdiği sürece onları takip ediyor ve onlar öldüğünde kayboluyorlar.

### Düzeltmeler
- **Basit Varlıklar ekranı titremeden** — Farklı kıyafetlerdeki köylüler gibi aynı ada sahip iki canlı artık yer değiştirerek titremiyor.
- **Daha temiz canlı görüntüleri** — Basit görüntü olarak gösterilen uzak canlıların üst ya da alt kenarında artık ince koyu çizgiler veya siyah benekler olmuyor. Atlar ve koklarcalar gibi uzun canlılar artık burunlarından ya da kuyruklarından kesilmiyor.
- **GeckoLib canlı çökmesi düzeltildi** — Bir GeckoLib modundan bir canlı uzakta görüldüğünde dünya artık yüklendikten birkaç saniye sonra çökmüyor. Bu canlılar henüz uzakta gösterilmiyor. (Minecraft 1.21.2 ile 1.21.8 arası.)
- **Daha hızlı Basit Varlıklar** — Oyun artık basit görüntülerle yavaşlamıyor: aynı canlılar artık tekrar tekrar çizilmiyor ve birbirine benzeyen canlılar tek bir görüntüyü paylaşıyor.

## 0.5.0 — 2026-10-13

### Yenilikler
- **Minecraft 1.20.1 ile 1.21.8 arasında Basit Varlıklar** — Çok uzaktaki canlılar ve oyuncular artık bu sürümlerde de basit görüntü olarak gösterilebiliyor; bu da bilgisayarınızın yükünü büyük ölçüde hafifletiyor.

### Düzeltmeler
- **Başlangıç çökmesi düzeltildi** — Mod yüklüyken oyun artık başlangıçta çökmüyor. (Forge, Minecraft 1.20.1 ile 1.20.4 arası.)
- **Basit Varlıklar ekranı çökmesi düzeltildi** — *Basit Varlıklar* ekranını açmak artık oyunu çökertmiyor. (Minecraft 1.21.8 ve öncesi.)
- **Katıldığınızda var olan canlılar görünüyor** — Bir dünyaya katıldığınızda zaten çevrenizde olan canlılar da artık ufukta beliriyor; yalnızca sonradan karşılaştıklarınız değil.

## 0.4.2 — 2026-10-12

### Düzeltmeler
- **Uzaklaşırken sabit canlılar** — Görüş mesafenizin ötesine geçen bir canlı artık bir anlığına kaybolmuyor ya da başka bir yöne dönmüyor.
- **Tepelerle tamamen gizlenen canlılar** — Bir köylünün kıyafetleri, elinde tuttuğu bir eşya ya da bir balçığın dış katmanı, canlının geri kalanı gizliyken artık Voxy'nin veya Distant Horizons'ın tepelerinin içinden görünmüyor. (Minecraft 1.21.9 ve sonrası, Distant Horizons ile ya da Voxy ve gölgelendiricilerle.)

## 0.4.1 — 2026-10-11

### Düzeltmeler
- **Gölgelendiricilerle doğru aydınlatılan canlılar** — Basit görüntü olarak gösterilen uzak canlılar artık Photon veya Complementary gibi Iris gölgelendirici paketleriyle fazla karanlık kalmak yerine tam modeller gibi güneşle aydınlatılıyor.
- **Arazi gölgeleri düzeltildi** — Uzaktaki canlılar artık arkalarındaki arazinin gölgesini göstermiyor ve Voxy'nin suyu olması gerektiği gibi görünüyor. (Voxy ile Photon.)
- **Tepelerle doğru gizlenen canlılar** — Uzaktaki canlılar artık Voxy'nin veya Distant Horizons'ın tepelerinin içinden görünmüyor. (Minecraft 26.1; Minecraft 26.2 ve sonrasında gölgelendiricilerle, eski Iris sürümleri dahil; Iris yüklüyken Minecraft 26.3.)
- **Voxy 0.2.18 desteği** — Mod yeniden Voxy 0.2.18 ile çalışıyor: uzaktaki canlılar onun arazisinde duruyor ve artık tepelerinin içinden görünmüyor.
- **Distant Horizons ile canlıları doğru tepeler gizliyor** — Uzaktaki canlılar artık önlerinde olmayan tepeler tarafından gizlenmiyor. (Photon'un TAAU ölçeklendirmesiyle Distant Horizons.)

## 0.4.0 — 2026-10-10

### Yenilikler
- **Otomatik ilk tarama** — Tek oyunculu bir dünyaya ilk girdiğinizde Living Horizon 32 yığın içindeki canlıları hemen arar; böylece ufuk baştan itibaren hayat dolu olur. Tarama menzili artık görüş mesafesi gibi yığın cinsinden ayarlanıyor.
- **Animasyon ayarları** — İki yeni kaydırıcı, performansı artırmak için ekranda çok küçük kalan ya da çok uzakta olan uzak canlıların animasyonunu durdurur.
- **Kalite hazır ayarları** — Düşük, Normal, Yüksek ve Ultra, kaç uzak canlı ve kuş göreceğinizi ve bunları ne kadar uzağa kadar göreceğinizi tek tıkla belirler. Yüksek ve Ultra güçlü bilgisayarlar içindir.

### Düzeltmeler
- **Canlılar renklerini koruyor** — Gölgelendiriciler olmadan uzaktaki canlılar artık, çoğunlukla uçarken, bir anlığına kararmıyor.

## 0.3.2 — 2026-10-09

### Düzeltmeler
- **Photon ile canlıları doğru tepeler gizliyor** — Voxy ve Photon'un TAAU ölçeklendirmesiyle uzaktaki canlılar yeniden doğru tepeler tarafından gizleniyor.

## 0.3.1 — 2026-10-08

### Düzeltmeler
- **Eski sürümlerde tepeler hesaba katılıyor** — Uzaktaki canlılar artık, yeni sürümlerde olduğu gibi, içlerinden görünmek yerine Voxy'nin ve Distant Horizons'ın tepelerinin arkasında gizleniyor. (Minecraft 1.20 ile 1.21.4 arası.)
- **Eski Distant Horizons sürümleri destekleniyor** — Mod artık yalnızca en son sürümle değil, Distant Horizons 2.3 ve sonrasıyla çalışıyor.

## 0.3.0 — 2026-10-07

### Yenilikler
- **Vulkan desteği** — Oyunun Vulkan oluşturucusuyla da uzaktaki canlılar, Distant Horizons arazisi dahil, tepelerin arkasında gizleniyor.

### Düzeltmeler
- **Distant Horizons arazisinin önünde görünen canlılar** — Gölgelendiriciler olmadan uzaktaki canlılar artık Distant Horizons arazisinin içinde solup kaybolmuyor.
- **Görüş mesafesinin kenarında artık leke yok** — Normal görüş mesafesinin bittiği yerde beliren lekeler giderildi. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Yenilikler
- **Uzaktaki canlılar** — Hayvanlar, köylüler ve golemler görüş mesafenizin ötesinde, ufukta otlayarak ve dolaşarak yaşamaya devam eder.
- **Uzaktaki oyuncular** — Diğer oyuncular görünümleri, zırhları ve binekleriyle uzakta yürümeye devam eder. Çevrim dışı olan oyuncular ayrıldıkları yerde uyur halde kalır.
- **Kuşlar** — Sürüler, V şeklinde uçan kazlar, kıyıların üzerindeki martılar ve geceleri yarasalar gökyüzüne hayat verir.
- **Distant Horizons desteği** — Living Horizon, Voxy'nin yanı sıra Distant Horizons ile de çalışır.
- **Tepelerin arkasında gizlenen canlılar** — Bir tepenin arkasındaki uzak canlılar çizilmez; bu da oyunun akıcı çalışmasını sağlar.
- **Uzakta basit görüntüler** — Çok uzaktaki canlılar ve oyuncular, sayıları fazla olduğunda çizilmesi çok daha hafif olan basit görüntüler olarak gösterilebilir. Bu seçenek varsayılan olarak kapalıdır.
- **Çevre taraması** — Tek oyunculuda bir tarama, çevrenizdeki yığınlardaki canlıları okur; böylece önce onlarla karşılaşmanız gerekmeden hemen ufukta belirirler.
- **Ayarlar ekranı** — Ayarlar menüsündeki *Living Horizon...* düğmesi, dünya arkada görünür kalırken modun ayarlarını açar ve bir düğme varsayılanları geri yükler.
- **Quilt, NeoForge ve Forge** — Living Horizon, Minecraft 1.20'den 26.3'e kadar Quilt, NeoForge ve Forge'da da çalışır.
- **Mod logosu** — Living Horizon'ın artık mod listesinde ve veri paketi listesinde bir logosu var.
