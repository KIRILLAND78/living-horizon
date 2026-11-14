# Living Horizon — Registro de cambios

## Próxima versión

## 0.16.0 — 2026-11-05

### Cliente

#### Novedades
- **Compatibilidad con el terreno de Lodistance** — Las criaturas y los jugadores lejanos ahora se ocultan tras el terreno lejano de Lodistance como tras cualquier colina y se funden con su niebla, y las criaturas lejanas caminan sobre su suelo, igual que con Voxy y Distant Horizons. (Minecraft 26.3, con OpenGL, sin shaders.)
- **Compatibilidad con el terreno de Far View** — Las criaturas lejanas ahora se muestran y caminan sobre el terreno lejano de Far View y se ocultan tras sus colinas, en lugar de quedar ocultas más allá de la distancia de renderizado. (Forge y NeoForge, Minecraft 1.20.1 a 1.21.4.)
- **Compatibilidad con el terreno de Ecstatic** — Las criaturas lejanas ahora aparecen sobre el terreno de Ecstatic y caminan sobre su suelo, en lugar de desaparecer más allá de la distancia de renderizado. Se ocultan correctamente tras sus colinas, siguen visibles delante de ellas y se funden con su niebla. (NeoForge, Minecraft 1.21.1, en un jugador; la niebla, sin shaders.)
- **Criaturas simplificadas iluminadas por los shaders** — Con un paquete de shaders que lea mapas de normales, como Photon con *Normal Mapping* activado, las imágenes simplificadas de las criaturas lejanas ahora reciben la iluminación del paquete como sus modelos 3D, con un lado al sol y el otro a la sombra, en lugar de verse planas y más oscuras. Estas imágenes ocupan el doble de memoria de vídeo; la nueva opción *Iluminación de shaders* permite desactivarlo. (Minecraft 1.21.6 y posteriores.)
- **Aparta a los jugadores desconectados** — Ahora puedes apartar de tu camino a los jugadores desconectados caminando contra ellos. Ellos nunca te empujan, ya que solo existen en tu juego.
- **Los jugadores desconectados van a pescar** — Los jugadores desconectados que están cerca del agua ahora pueden detenerse en la orilla para pescar un rato. Solo pesca uno a la vez en un mismo tramo de costa.
- **Los jugadores desconectados se resguardan de la lluvia** — Cuando llueve, algunos jugadores desconectados ahora van a ponerse bajo un techo o un árbol a su alcance y esperan allí hasta que escampa. Los demás siguen como de costumbre.
- **Los jugadores desconectados se reúnen junto al fuego** — Al anochecer, los jugadores desconectados ahora van a colocarse delante de una hoguera o un horno a su alcance, cada uno en su propio sitio.
- **Los jugadores desconectados charlan entre ellos** — Dos jugadores desconectados cercanos ahora pueden acercarse el uno al otro y charlar un rato, cara a cara. Nunca más de dos a la vez, y solo una conversación en una misma zona.
- **Los jugadores desconectados te devuelven la mirada** — Los jugadores desconectados ahora se giran para mirarte cuando los miras de cerca. Si uno te está mirando y te agachas, él también se agacha.

#### Mejoras
- **Noches más naturales para los jugadores desconectados** — Los jugadores desconectados ahora duermen en una cama a su alcance, o se quedan de pie, en lugar de tumbarse en el suelo.
- **Barcos lejanos más nítidos** — Las imágenes simplificadas de las barcas y vehículos altos, como los veleros de Shippy Ships, ahora son más nítidas. Los más altos, como el galeón, ahora también tienen una, en lugar de mostrarse siempre como modelo completo, que exige más al rendimiento.
- **Transición más suave a las imágenes simplificadas** — Las criaturas y los jugadores lejanos ahora giran poco a poco hacia el ángulo en el que los muestra su imagen simplificada a medida que se acercan a la distancia en la que esta los sustituye, en lugar de girar de golpe en el momento del cambio. El paso del modelo 3D a la imagen se nota menos.
- **Versiones de NeoForge 1.21.4 y 1.21.11 más fáciles de encontrar** — Las versiones de NeoForge para Minecraft 1.21.4 y 1.21.11 ahora se publican como beta. Así aparecen en CurseForge y en los launchers que ocultan las versiones alfa.

#### Correcciones
- **Criaturas mostradas una sola vez con Far View** — Las criaturas que Far View dibuja como cajas entre 128 bloques y la distancia de renderizado ahora se muestran una sola vez, en lugar de dos veces superpuestas. (Forge y NeoForge, Minecraft 1.20.1 a 1.21.4.)
- **Nombres de los jugadores desconectados visibles en Forge** — Al apuntar a un jugador desconectado ahora se muestran su nombre y cuándo se le vio por última vez, en lugar de no aparecer nada. (Forge, Minecraft 1.20.1 a 1.20.4.)
- **Mejor integración con Bobby** — Las criaturas lejanas ahora siguen visibles justo más allá de la distancia hasta la que el servidor las envía, sobre el terreno que conserva Bobby, en lugar de desaparecer. Los jugadores lejanos tampoco se detienen ya en seco al salir de esa distancia.
- **El galeón de Shippy Ships, visible a lo lejos** — El galeón de Shippy Ships ahora sigue visible a lo lejos donde lo dejaste, como los demás barcos del mod, en lugar de desaparecer en cuanto sale de la distancia de renderizado.
- **Los jugadores desconectados siguen activos a lo lejos** — Cuando te alejas, los jugadores desconectados ahora siguen paseando justo donde están, en lugar de volver al lugar donde se desconectaron o quedarse completamente quietos. Donde no hay sitio para pasear, dan unos pasos, se dan la vuelta o se sientan un rato.

## 0.15.0 — 2026-10-31

### Servidor

#### Novedades
- **Criaturas en tierras inexploradas** — Living Horizon ahora muestra a los jugadores de un servidor las criaturas que el mundo colocaría en las regiones que nadie ha explorado todavía, hasta 512 bloques alrededor de cada uno por defecto. Sus posiciones se calculan a partir de la semilla: pueden ser distintas, o no estar, una vez allí. Esta función se puede desactivar, o cambiar su alcance, en los ajustes del servidor.
- **Compartir la semilla** — Los servidores ahora pueden permitir que Living Horizon comparta la semilla del mundo con los jugadores que tienen el mod, para que su juego calcule por sí mismo las criaturas en cualquier lugar, con un catalejo o con *Adivinar criaturas*. Esta opción está desactivada por defecto, ya que conocer la semilla revela todo sobre el mundo.

#### Mejoras
- **Vuelo con élitros visible a lo lejos** — El servidor ahora comparte qué jugadores vuelan con élitros, para que se muestren correctamente planeando a gran distancia.

### Cliente

#### Novedades
- **Accesos rápidos a Living Horizon** — Los ajustes del mod ahora ofrecen accesos directos a Modrinth, CurseForge, Nexus Mods y el sitio web oficial, junto a Discord.
- **Criaturas en zonas inexploradas** — Las regiones que nadie ha explorado todavía ya no están vacías: Living Horizon calcula a partir de la semilla qué criaturas colocaría allí el mundo, hasta donde alcance la opción *Renderizado de criaturas*. Como solo son estimaciones, pueden ser distintas o no estar cuando llegues. Disponible en los mundos de un jugador y en los servidores con el mod; *Criaturas adivinadas* permite ocultarlas.
- **Criaturas adivinadas con el catalejo** — Al mirar por un catalejo ahora se muestran las criaturas adivinadas de las zonas inexploradas allí donde apuntes, a cualquier distancia. En un servidor, este debe compartir su semilla.
- **Adivina las criaturas de tu alrededor** — El nuevo botón *Adivinar criaturas* muestra de una vez las criaturas adivinadas hasta 1024 chunks a tu alrededor y las conserva para tus próximas sesiones. En un servidor, este debe compartir su semilla.
- **Valores introducidos con el teclado** — Los deslizadores de los ajustes del mod que terminan en *Sin límite* (cantidades máximas y distancias de renderizado) ahora aceptan un valor escrito con el teclado, incluso más allá de su último paso, y la página de Sodium también lo muestra. Haz clic derecho en un deslizador para introducir un valor exacto; las flechas del teclado también mueven cada deslizador paso a paso.
- **Vista previa de las distancias en el mundo** — Cuando pasas el cursor por encima de una opción de distancia o la cambias, su límite ahora se muestra directamente en el mundo, detrás del menú. Así ves de un vistazo hasta dónde llega el valor elegido.

#### Mejoras
- **Distancia de simplificación opcional** — La *Distancia de simplificación* ahora se puede desactivar. Las criaturas, entidades, jugadores y aves solo se convierten entonces en imágenes simplificadas cuando parecen lo bastante pequeños en pantalla.
- **Hasta 4000 criaturas lejanas** — *Máx. de criaturas* ahora llega hasta 4000 antes de *Sin límite*.
- **Sin límite al final del deslizador** — *Sin límite* ahora está en el extremo derecho de los deslizadores de distancia de renderizado, después de 4000 bloques, en lugar del izquierdo. Los deslizadores van ahora de la distancia más corta a la más larga.
- **Confirmación antes de Sin límite** — El juego ahora pide confirmación antes de poner en *Sin límite* un número de criaturas, entidades o jugadores desconectados, o una distancia de renderizado. Estos valores pueden reducir mucho los FPS, o incluso hacer que el juego se cierre inesperadamente. En la página de Sodium, la pregunta aparece al pulsar *Aplicar*.
- **Límites por defecto más sensatos** — Ninguna opción es ya ilimitada por defecto, para evitar grandes caídas de rendimiento: *Renderizado de jugadores*, *Distancia de animación* y *Renderizado de entidades* se detienen en 4000 bloques. Las plantillas *Alto* y *Ultra* muestran como máximo 1500 y 3000 criaturas, *Ultra* hasta 2048 bloques, y las opciones que seguían con los antiguos valores por defecto pasan a los nuevos.
- **Vuelo con élitros más natural** — Los jugadores lejanos que vuelan con élitros ahora se muestran con las alas desplegadas en una auténtica postura de vuelo, en lugar de correr erguidos en el aire.
- **Cambios de paquete de recursos más fluidos** — Las imágenes simplificadas de las criaturas ya no se regeneran innecesariamente al desactivar y volver a activar un paquete de recursos. La carga de recursos tampoco congela ya el juego mientras se buscan.
- **Criaturas voladoras de Ice and Fire vistas desde todos los ángulos** — Los dragones, anfiteres e hipogrifos de Ice and Fire ahora tienen imágenes simplificadas vistas desde abajo y desde arriba, como los fantasmas y los aviones, en lugar de solo de lado. Se ven bien cuando vuelan sobre ti o cuando los miras desde arriba.
- **Criaturas enormes más nítidas a lo lejos** — Las criaturas muy grandes ahora conservan su modelo 3D completo mientras sigan pareciendo grandes en pantalla, en lugar de convertirse en una imagen simplificada pixelada.

#### Correcciones
- **Criaturas lejanas visibles sobre el terreno lejano** — Las criaturas lejanas, incluidas las adivinadas, ahora siguen visibles sobre el terreno lejano, en lugar de ocultarse como si no hubiera nada debajo: durante un rato tras abrir un mundo, entre miles de criaturas adivinadas o cuando Voxy o Distant Horizons tardan en responder.
- **La niebla de Distant Horizons, también en las criaturas** — Las criaturas lejanas ahora reciben la misma niebla que el terreno de Distant Horizons y se difuminan con él, en lugar de destacar con nitidez sobre el paisaje. (Sin shaders.)
- **Criaturas de Ice and Fire bien simplificadas** — Los dragones lejanos de Ice and Fire ahora tienen imágenes simplificadas completas a su tamaño real, en lugar de un primer plano de sus patas o de un ala. Los dragones de distintas edades ya no comparten una misma imagen, y las serpientes marinas conservan su modelo 3D en lugar de una imagen recortada.
- **Los jugadores desconectados conservan su barca** — Un jugador desconectado en mitad del agua ya no pierde la barca en la que descansa cuando te alejas: la conserva de cerca y de lejos, y también la próxima vez que juegues.
- **Los jugadores desconectados ya no flotan en el aire** — Un jugador que se desconectó en el aire, planeando con élitros o cayendo, ya no vuelve allí arriba cuando te alejas: descansa en el suelo que tiene debajo. Con Voxy o Distant Horizons, está en el suelo incluso antes de que te acerques.
- **Criaturas lejanas en reposo natural** — Las criaturas lejanas más allá de la *Distancia de animación*, o más pequeñas que el *Tamaño mín. animado*, ahora se quedan donde están, en lugar de deslizarse de un lado a otro temblando y con las patas quietas. Siguen caminando desde donde se detuvieron cuando vuelven a estar lo bastante cerca.

## 0.14.0 — 2026-10-29

### Cliente

#### Novedades
- **Vehículos de otros mods visibles a lo lejos** — Los vehículos de otros mods, como los aviones de Immersive Aircraft, ahora siguen visibles más allá de la distancia de renderizado donde se dejaron, igual que las barcas. Los vehículos en los que vas sin conducir, como las vagonetas, están desactivados por defecto y se pueden activar en *Criaturas y entidades...*.
- **Ajustes propios para las entidades** — Las barcas, los vehículos y los maniquíes ahora tienen sus propios ajustes, separados de los de las criaturas: si se muestran, cuántos y hasta dónde. Su distancia de renderizado es ilimitada por defecto.
- **Simplificación separada para criaturas y entidades** — *Entidades simplificadas* pasa a ser dos opciones: *Criaturas simplificadas* y *Entidades simplificadas*. Las barcas, los vehículos y los maniquíes ahora también pueden convertirse en imágenes simplificadas a lo lejos, incluso vistos desde arriba o desde abajo. *Jugadores simplificados* ahora es independiente, y la pantalla de imágenes se llama ahora *Vista previa de imágenes...*, con una pestaña para las entidades.
- **Registro de cambios en el juego** — Un nuevo botón en los ajustes del mod muestra qué ha cambiado en cada versión, sin salir del juego. También está disponible en la página de Sodium.

#### Mejoras
- **Aviso en servidores incompatibles** — Cuando entras en un servidor sin Living Horizon, o con una versión demasiado antigua o demasiado reciente, un mensaje en el chat te avisa ahora de que allí no se mostrarán los jugadores lejanos. El mensaje no aparece si *Mostrar jugadores* está desactivado.
- **Criaturas y entidades en dos pestañas** — La pantalla *Criaturas y entidades...* ahora separa las criaturas y las demás entidades en dos pestañas, para encontrarlas más fácilmente. Allí, las barcas y los vehículos simplemente se muestran u ocultan.
- **Elección de criaturas aplicada al instante** — Mostrar u ocultar un tipo en *Criaturas y entidades...* ahora se aplica al momento en el mundo que hay detrás del menú, sin tener que cerrarlo.
- **Ajustes de criaturas mejor organizados** — Los ajustes de *Criaturas y entidades lejanas* ahora se dividen en tres partes: *General*, *Criaturas* y *Entidades*.
- **Jinetes más fieles a lo lejos** — Con *Jugadores simplificados*, un jugador lejano en una barca, una vagoneta o un avión, o sobre un ghast feliz, ahora se muestra correctamente desde arriba y desde abajo, en lugar de aparecer siempre de perfil.
- **Aviones que siguen en el aire** — Un jugador que se desconecta en pleno vuelo ahora se queda en el aire en su avión, que se mantiene suspendido o da vueltas según estuviera quieto o en movimiento.
- **Origen de las imágenes visible** — La pantalla *Vista previa de imágenes* ahora indica de dónde viene cada imagen (compartida, reutilizada de una sesión anterior, nueva o sin guardar), además de cuándo y en qué mundo se creó.
- **Las mascotas sentadas siguen sentadas** — Los gatos, lobos y loros a los que has ordenado sentarse ahora siguen sentados a lo lejos, en lugar de levantarse y marcharse a pasear.

#### Correcciones
- **Cierre inesperado de Criaturas y entidades corregido** — Abrir *Criaturas y entidades...* desde el menú principal ya no hace que el juego se cierre inesperadamente. (Minecraft 26.x.)
- **Los vehículos de otros mods conservan sus colores** — Los vehículos y criaturas lejanos de algunos mods, como los aviones de Immersive Aircraft, ahora conservan sus colores al acercarte, en lugar de volverse negros por un momento.
- **Monturas mostradas una sola vez** — La montura o el vehículo de un jugador lejano, como un avión de otro mod, ya no aparece por duplicado a su lado.

## 0.13.0 — 2026-10-28

### Cliente

#### Mejoras
- **Juego más fluido con criaturas lejanas** — Living Horizon ahora calcula en segundo plano por dónde pueden caminar las criaturas lejanas y los jugadores desconectados, sin ralentizar el juego.
- **Renderizado más fluido con Voxy y Distant Horizons** — El juego espera menos al controlador gráfico en cada fotograma cuando se muestran criaturas lejanas sobre el terreno de Voxy o de Distant Horizons. La mejora es mayor en tarjetas NVIDIA con la *Optimización de subprocesos* activada.
- **Fantasmas y ghasts que vuelan a lo lejos** — Los fantasmas lejanos ahora dan vueltas en el cielo y los ghasts flotan lentamente a la deriva, en lugar de quedarse inmóviles en el aire.
- **Fantasmas y ghasts vistos desde abajo** — Como imágenes simplificadas, los fantasmas y los ghasts ahora también se muestran desde abajo y desde arriba, como las aves, y no solo de lado.
- **Más fluido con muchas criaturas lejanas** — El juego ya no pierde tiempo con cada criatura lejana en cada fotograma. (Vulkan en Minecraft 26.2 y posteriores, Distant Horizons en Minecraft 26.3.)
- **Todas las criaturas visibles a lo lejos** — Los monstruos, murciélagos, peces y todas las demás criaturas ahora se muestran a lo lejos por defecto y deambulan como los animales. Los endermen, por ejemplo, siguen visibles a lo largo del End. Los animales que habías ocultado siguen ocultos.

#### Correcciones
- **Botones de los ajustes de nuevo en orden** — Los botones de la parte inferior de *Criaturas y entidades lejanas* ya no se superponen, y *Restablecer por defecto* vuelve a estar a la izquierda de *Hecho*. (Minecraft 1.20.1.)
- **Bloqueo al cambiar de dimensión corregido** — El juego ya no se queda bloqueado para siempre al cruzar un portal o salir de un mundo. (Con Distant Horizons.)

## 0.12.1 — 2026-10-27

### Cliente

#### Correcciones
- **Comando de benchmark reservado a las pruebas** — `/livinghorizon benchmark` ya no se ofrece a los jugadores: solo sirve para probar el mod.

## 0.12.0 — 2026-10-23

### Servidor

#### Novedades
- **Ajustes del servidor** — Los propietarios de servidores ahora pueden elegir qué comparte Living Horizon: jugadores conectados, jugadores desconectados y criaturas, hasta qué distancia, solo entre compañeros de equipo si lo deseas, y qué jugadores o dimensiones no se comparten nunca. Los ajustes están en `config/livinghorizon-server.json` y se aplican en pocos segundos, sin reiniciar.

#### Correcciones
- **Conexión a servidores Forge corregida** — Los jugadores con el mod ya no son expulsados de un servidor Forge que también lo tiene con el mensaje «mismatched mod channel list». (Forge 1.20.2 y posteriores.)
- **Espectadores y jugadores invisibles, siempre ocultos** — Los jugadores en modo espectador o con invisibilidad ya no son visibles para los demás a lo lejos, ni donde se desconectaron.

### Cliente

#### Novedades
- **Botón de Discord** — Los ajustes del mod ahora te permiten unirte al Discord de Living Horizon con un clic, para hacer una pregunta o informar de un problema. El botón también está disponible en la página de Sodium.
- **Versión del mod visible** — Las pantallas de ajustes y el panel de depuración ahora muestran la versión de Living Horizon, útil para incluirla al informar de un error.
- **Tamaño de simplificación** — Una nueva opción convierte las criaturas, jugadores y aves lejanos en imágenes simplificadas en cuanto parecen más pequeños que un tamaño determinado en pantalla (10 píxeles por defecto), incluso antes de la *Distancia de simplificación*. Lo que apenas se ve resulta mucho menos costoso de dibujar.

#### Mejoras
- **Más fluido con cientos de criaturas lejanas** — *Omitir criaturas ocultas* consume mucho menos con muchas criaturas y jugadores lejanos en pantalla, y Living Horizon hace menos trabajo entre bastidores.
- **Versiones para Minecraft 1.21.1 más fáciles de encontrar** — Las versiones de Fabric, Quilt y NeoForge para Minecraft 1.21 y 1.21.1 ahora se publican como beta. Así aparecen en CurseForge y en los launchers que ocultan las versiones alfa.
- **Menos tirones con multitudes a lo lejos** — Las criaturas, jugadores y aves lejanos ahora piden mucha menos memoria al juego en cada fotograma, lo que reduce los tirones.
- **Más fluido con muchas aves** — Las aves que están detrás de ti o que son demasiado pequeñas para verse ya no se dibujan.

#### Correcciones
- **Criaturas lejanas siempre sobre suelo firme** — Las criaturas lejanas ya no se dibujan donde todavía no hay terreno visible bajo ellas, ya sea el del juego, el de Voxy o el de Distant Horizons. Ya no flotan en el vacío.
- **Los mundos de un jugador vuelven a abrirse en Forge** — Un mundo de un jugador ya no se cierra nada más abrirse con el mensaje «mismatched mod channel list». (Forge 1.20.2 y posteriores.)
- **Criaturas lejanas estables sobre el terreno de Distant Horizons** — En los mundos de un jugador, las criaturas lejanas ya no desaparecen ni dejan de caminar en las colinas y montañas de Distant Horizons.

## 0.11.0 — 2026-10-22

### Servidor

#### Mejoras
- **Más ligero en servidores grandes** — El servidor dedica menos tiempo a elegir qué skins y monturas enviar a cada jugador. Las listas largas de jugadores desconectados ahora se envían en varias partes, en lugar de en un único mensaje demasiado grande para el juego.
- **Actualización necesaria para los jugadores** — En un servidor con esta versión, los jugadores también la necesitan para ver a los jugadores lejanos.

#### Correcciones
- **Archivo de jugadores desconectados protegido** — Si el servidor no puede leer `livinghorizon-positions.dat`, el archivo ya no se sobrescribe: se conserva como `livinghorizon-positions.dat.bad` y la consola lo indica.

### Cliente

#### Novedades
- **Benchmark** — El comando `/livinghorizon benchmark` mide lo que cuesta cada parte del mod en tu mundo y genera un informe para adjuntarlo al informar de un error.
- **Mundo de benchmark** — `/livinghorizon benchmark world` crea un mundo de prueba siempre idéntico, lleno de criaturas y jugadores desconectados, para que las mediciones se puedan comparar de una vez a otra. Tu propio mundo no se modifica.
- **Máx. desconectados** — Una nueva opción limita cuántos jugadores desconectados se muestran a la vez, empezando por los más cercanos (256 por defecto). Los demás vuelven a aparecer a medida que te acercas.

#### Mejoras
- **Mensaje más claro cuando las versiones no coinciden** — Cuando el mod del servidor es demasiado antiguo o demasiado reciente para el tuyo, el registro ahora indica cuál hay que actualizar, en lugar de afirmar que el servidor no tiene el mod.
- **Más fluido con muchos jugadores desconectados** — Mostrar jugadores desconectados a lo lejos cuesta menos, y los que quedan ocultos tras las colinas ya no se dibujan.

#### Correcciones
- **Renderizado de jugadores respetado** — Los jugadores desconectados ya no se muestran más allá del *Renderizado de jugadores*.
- **Maniquíes lejanos con su skin real** — Los maniquíes lejanos ahora conservan la skin que llevan de cerca, ya sea la de un jugador o la de un paquete de recursos, en lugar de convertirse en Alex. (Minecraft 1.21.9 y posteriores.)
- **Agua intacta con Photon y Distant Horizons** — Con *Ocultar tras el relieve* activado, el agua ahora se ve normal cuando hay una criatura o un jugador lejano a la vista, en lugar de dejar al descubierto su fondo desnudo y sombreado. (Minecraft 26.3.)
- **Las capas de los maniquíes caen con naturalidad** — Las capas de los maniquíes lejanos ahora cuelgan como de cerca, en lugar de quedarse levantadas en el aire. (Minecraft 1.21.9 y posteriores.)
- **Archivo de ajustes protegido** — Un error al editar `livinghorizon.json` a mano ya no borra el archivo: se conserva como `livinghorizon.json.bad` y el registro lo indica.
- **Guardados protegidos** — Si el juego se cierra durante un guardado, los archivos de las criaturas recordadas (las que Living Horizon sigue mostrando a lo lejos) y de los jugadores desconectados ya no se dañan. Un archivo dañado se conserva como `.bad` en lugar de vaciarse.
- **Conexión más fluida a servidores grandes** — Entrar en un servidor con muchos jugadores desconectados ya no provoca tirones en el juego.
- **Imágenes dañadas regeneradas automáticamente** — Una imagen simplificada guardada que esté dañada ya no puede dejar al juego sin memoria: simplemente se vuelve a generar.
- **Criaturas lejanas visibles delante del terreno de Distant Horizons con Oculus** — Con Oculus y un paquete de shaders, las criaturas lejanas ya no desaparecen tras el terreno de Distant Horizons, un problema que empeoraba al aumentar la distancia de renderizado. (Forge.)
- **Movimientos de cabeza naturales con Entity Culling** — Las criaturas y los jugadores lejanos ya no sacuden la cabeza a toda velocidad con Entity Culling, solo o junto con Chloride.
- **Jugadores en la dimensión correcta de cada mod** — Los jugadores conectados y desconectados de una dimensión de un mod, como el Aether, ya no se muestran en otra, como el Twilight Forest. En un servidor, este también necesita esta versión.
- **La niebla de Voxy, también en las criaturas** — Las criaturas lejanas sobre el terreno de Voxy ahora reciben la misma niebla que el paisaje que las rodea, en lugar de desvanecerse en el color de la niebla a partir de cierta distancia. (Sin shaders.)

## 0.10.0 — 2026-10-20

### Servidor

#### Novedades
- **Living Horizon en servidores** — El mod ahora también se puede instalar en el servidor: pon el jar en su carpeta `mods` (Fabric, Quilt, NeoForge o Forge, más la Fabric API en Fabric y Quilt). No hace falta configurar nada, y sustituye al paquete de datos.
- **Jugadores compartidos en tiempo real** — El servidor envía la posición de cada jugador cinco veces por segundo a los jugadores que tienen el mod, con su skin, su armadura, los objetos que lleva en la mano y su montura.
- **Criaturas compartidas** — El servidor envía las criaturas que hay a menos de 2048 bloques de cada jugador, incluso las que ese jugador nunca ha visto: animales, aldeanos, gólems, allays y barcas, con su montura, arnés, armadura o cofre.
- **Jugadores desconectados compartidos** — El servidor recuerda dónde se desconectó cada jugador, su aspecto, su montura y cuándo se fue, y lo transmite a todos, incluidos los jugadores que entran más tarde.
- **Jugadores de antes de la instalación** — En el primer arranque, el servidor encuentra a los jugadores que el mundo ya conocía y los muestra como jugadores desconectados donde se fueron, con su equipo.
- **Guardado con el mundo** — Todo se guarda en `livinghorizon-positions.dat`, en la carpeta del mundo. Una criatura se olvida cuando su chunk se carga y ya no está allí.
- **Mundos en LAN** — Un mundo abierto en LAN lo comparte todo a través del juego del anfitrión, sin instalar nada más.
- **Jugadores sin el mod bienvenidos** — Los jugadores sin el mod pueden seguir entrando; el servidor simplemente no les envía nada.
- **La misma versión en ambos lados** — Mantén el mod actualizado tanto en el servidor como en los juegos de los jugadores: un juego que no entiende los mensajes del servidor los ignora.
- **Privacidad: las posiciones se comparten** — El servidor comparte la posición de cada jugador con todos los jugadores que tienen el mod, a cualquier distancia. Asegúrate de avisar a tus jugadores.
- **Adiós al paquete de datos** — El paquete de datos ya no se usa: quítalo de la carpeta `datapacks` del mundo. Las coordenadas ya no pasan por la tabla de puntuación, así que ya no aparecen en las puntuaciones ni en la barra lateral.

#### Correcciones
- **Jugadores conservados al cerrar un mundo** — Cuando un servidor o un mundo en LAN se cierra, los jugadores que siguen en él ahora se quedan donde estaban como jugadores desconectados, en lugar de desaparecer.

### Cliente

#### Novedades
- **Todos los jugadores, a cualquier distancia** — En un servidor con el mod, ahora ves a todos los demás jugadores, estén donde estén, con su skin, su armadura, los objetos que llevan en la mano y su montura.
- **Criaturas que nunca has visto** — En un servidor con el mod, las criaturas lejanas aparecen enseguida, incluso aquellas a las que nunca te has acercado, con su montura, arnés, armadura o cofre.
- **Jugadores desconectados visibles al entrar** — Ves a los jugadores desconectados donde se fueron, con su aspecto y su montura, aunque se fueran antes de que llegaras.
- **Jugadores desconectados en los mundos de un jugador** — En un mundo de un jugador, los jugadores desconectados aparecen en cuanto lo abres, incluidos los de antes de instalar el mod.
- **Un horizonte vivo desde el principio** — Las criaturas lejanas y los jugadores desconectados aparecen en cuanto entras, no unos segundos después.
- **Servidores sin el mod** — Puedes seguir entrando en ellos. Allí solo se muestran las criaturas que ya has visto, sin jugadores lejanos, y el registro lo menciona una vez.

#### Mejoras
- **Posiciones de los jugadores lejanos más fiables** — Los jugadores lejanos ahora se colocan solo a partir de las posiciones que comparte el servidor. Desaparece la estimación mediante la barra localizadora, junto con la opción *Tiempo de jugador perdido*.

#### Correcciones
- **Ghasts felices bien mostrados** — Un jugador lejano montado en un ghast feliz ya no tiene un segundo ghast a su lado, y uno que ya iba montado cuando entraste ya no flota en el aire.
- **Los ghasts felices se quedan donde se dejaron** — Cuando un jugador lejano se baja de su ghast feliz, este ahora se queda donde se dejó, en lugar de reaparecer donde se montó por primera vez y volver volando.
- **Skins nuevas visibles a lo lejos** — Un jugador lejano que cambia de skin ahora aparece con la nueva, en lugar de conservar la antigua a lo lejos.
- **Jugadores bien sentados en las barcas** — Un jugador que se desconecta en una barca ahora mira en la misma dirección que la barca, en lugar de sentarse de lado.
- **Jinetes bien sentados** — Un jugador lejano montado a caballo ahora se sienta correctamente, como modelo completo o como imagen simplificada, en lugar de hundirse en el caballo o sentarse en su grupa.
- **Un solo caballo tras desconectarse** — Un jugador que se desconecta montado a caballo ya no deja una copia del caballo junto al que monta.
- **Botones Website e Issues operativos** — Los botones Website e Issues de la página del mod en Mod Menu ya no aparecen en gris. (Fabric.)

## 0.9.0 — 2026-10-19

### Correcciones
- **Jugadores desconectados fuera del agua** — Los jugadores que se desconectaron mientras nadaban ya no se quedan de pie en el agua: esperan en la orilla más cercana, o reman en una barca si la orilla está a más de 10 bloques.
- **Agua intacta alrededor de las barcas** — El agua ya no se vuelve transparente alrededor de los jugadores desconectados que reman en una barca con un paquete de shaders como Photon, y ya no desaparece en un gran cuadrado alrededor de las barcas lejanas.
- **Los remeros conservan su barca** — A lo lejos, los jugadores desconectados que reman en una barca ahora conservan la barca mientras se crea su imagen simplificada, en lugar de aparecer de pie sin ella.

## 0.8.0 — 2026-10-18

### Novedades
- **Jugadores simplificados** — Una nueva opción permite mantener a los jugadores lejanos como modelos 3D completos, mientras las criaturas lejanas siguen convirtiéndose en imágenes simplificadas.
- **12 idiomas nuevos** — Living Horizon ahora está disponible en alemán, español, italiano, portugués de Brasil, polaco, turco, ruso, ucraniano, chino simplificado y tradicional, japonés y coreano.
- **Ajustes en Sodium** — Los ajustes de Living Horizon ahora se pueden abrir desde la pantalla *Gráficos* de Sodium, en sus propias páginas. (Sodium 0.8 y posteriores.)
- **Jugadores desconectados con su montura** — Un jugador que se desconectó a caballo, en una barca o sobre un ghast feliz sigue montándolo por los alrededores del lugar donde se fue.
- **Control de la memoria de vídeo** — La pantalla *Entidades simplificadas...* muestra cuánta memoria de vídeo usan las imágenes de las criaturas y de los jugadores, y dos nuevas opciones fijan un límite para cada una.

### Mejoras
- **Ajustes más claros** — Cada opción tiene ahora un nombre y una descripción más claros, al estilo de las opciones de Minecraft, y los deslizadores de distancia ocupan todo el ancho de la pantalla.
- **Jugadores desconectados más vivos** — Los jugadores desconectados ahora dan un pequeño paseo en círculo alrededor del lugar donde se fueron. A lo lejos, se convierten en imágenes simplificadas como los jugadores conectados, ya estén de pie, sentados, tumbados o en la cama.
- **Jinetes más ligeros** — A lo lejos, un jugador montado en un caballo, una barca, una vagoneta o cualquier otra montura forma ahora una sola imagen simplificada con ella, mucho más ligera que dos modelos completos.
- **Grandes multitudes de jugadores más fluidas** — A partir de unos cien jugadores lejanos, los que sobran ahora también se convierten en imágenes simplificadas en lugar de seguir como modelos completos.

### Correcciones
- **Criaturas bien ocultas tras cambiar el tamaño de la ventana** — Las criaturas lejanas ya no se ven a través de las colinas, incluidas las de Voxy y Distant Horizons, después de cambiar el tamaño de la ventana del juego.
- **Jugadores y slimes visibles con Distant Horizons** — Los jugadores lejanos, los jugadores desconectados y los slimes ya no desaparecen bajo el terreno de Distant Horizons. (Minecraft 26.1 y posteriores.)
- **Criaturas enteras delante del terreno de Distant Horizons** — El terreno de Distant Horizons ya no corta a las criaturas y los jugadores que están delante de él. (Minecraft 26.1 y posteriores.)
- **Criaturas estables con el balanceo de la vista** — Con el balanceo de la vista activado, las criaturas lejanas ya no parpadean y el terreno de Distant Horizons ya no oscila delante de ellas a cada paso. (Minecraft 26.1 y posteriores.)

## 0.7.0 — 2026-10-17

### Novedades
- **Los jugadores desconectados cobran vida** — Los jugadores desconectados ahora viven alrededor del lugar donde se fueron: pasean, se sientan, miran cofres y estaciones de trabajo, se van a la cama por la noche y levantan el escudo cuando reciben un golpe o cuando se acercan monstruos. Siempre se quedan en el suelo: caen si rompes el bloque que tienen debajo, y vuelven a subir si los entierras.
- **Etiqueta «(sin conexión)»** — Los jugadores desconectados muestran *(sin conexión)* tras su nombre, y al mirar a uno de cerca se indica cuándo se le vio por última vez. Sus nombres, o los propios jugadores desconectados, se pueden ocultar en los ajustes.
- **Aves en 3D** — Las aves ahora son pequeños modelos 3D como las criaturas de Minecraft: baten las alas, se apoyan en sus patas cuando están posadas y se convierten en imágenes simplificadas a lo lejos. La opción *Estilo de aves* recupera las antiguas aves en 2D.
- **Hmm…** — Me ha parecido ver algo en el cielo. 😉

### Mejoras
- **Criaturas que saltan** — Los slimes, cubos de magma y conejos lejanos ahora se mueven a saltos en lugar de deslizarse. Los slimes se aplastan al aterrizar, y los pollos baten las alas al bajar un bloque.
- **Patos más naturales** — Los patos ahora alzan el vuelo juntos cuando te acercas y se posan más adelante en el agua, en lugar de marcharse para siempre.

### Correcciones
- **Criaturas animadas a través del catalejo** — Las criaturas lejanas vistas a través de un catalejo ahora cuentan con su tamaño ampliado y siguen caminando cuando parecen grandes, en lugar de quedarse congeladas. (Opción *Tamaño mín. animado*.)
- **Criaturas animadas al abrir un mundo** — Las criaturas lejanas ahora caminan y pastan desde el principio, en lugar de girar sobre sí mismas.
- **Aves posadas más naturales** — Los herrerillos, petirrojos y patos ya no van y vienen sin parar, ni flotan en el aire, ni se quedan dentro de las hojas, ni nadan en tierra firme. Los patos ya no aparecen justo a tu lado solo para echar a volar y ahora nadan por su estanque, y los petirrojos y herrerillos dan más saltitos y echan a volar menos.
- **Licencia correcta en la lista de mods** — Living Horizon ahora muestra la licencia correcta en la lista de mods. (Forge, NeoForge y Quilt.)

## 0.6.0 — 2026-10-15

### Novedades
- **Imágenes de criaturas conservadas** — Las imágenes simplificadas de las criaturas lejanas ahora se guardan en el disco y se reutilizan de una sesión a otra. Solo se regeneran tras un cambio de paquetes de recursos, de mods o de versión del juego.

### Mejoras
- **Nuevo icono** — Living Horizon tiene un nuevo icono en pixel art: Steve en primer plano, rodeado de criaturas, aldeanos, jugadores y aves hasta las montañas.
- **Criaturas lejanas más coherentes al volver** — En los mundos de un jugador, las criaturas lejanas se teletransportan o corren hacia otro lugar con mucha menos frecuencia cuando vuelves: siguen a las criaturas reales mientras el juego las siga moviendo, y desaparecen cuando estas mueren.

### Correcciones
- **Pantalla de Entidades simplificadas sin parpadeos** — Dos criaturas con el mismo nombre, como aldeanos con distintos atuendos, ya no parpadean intercambiándose el sitio.
- **Imágenes de criaturas más limpias** — Las criaturas lejanas mostradas como imágenes simplificadas ya no tienen finas líneas oscuras ni puntos negros en su borde superior o inferior. Las criaturas alargadas, como los caballos y los sniffers, ya no aparecen cortadas por el hocico o la cola.
- **Cierre inesperado con criaturas de GeckoLib corregido** — Un mundo ya no se cierra inesperadamente unos segundos después de cargarse cuando se ha visto a lo lejos una criatura de un mod que usa GeckoLib. Estas criaturas todavía no se muestran a lo lejos. (Minecraft 1.21.2 a 1.21.8.)
- **Entidades simplificadas más rápidas** — El juego ya no se ralentiza con las imágenes simplificadas: las mismas criaturas ya no se vuelven a dibujar una y otra vez, y las que se parecen comparten una sola imagen.

## 0.5.0 — 2026-10-13

### Novedades
- **Entidades simplificadas en Minecraft 1.20.1 a 1.21.8** — Las criaturas y los jugadores muy lejanos ahora también se pueden mostrar como imágenes simplificadas en estas versiones, lo que le ahorra mucho trabajo a tu ordenador.

### Correcciones
- **Cierre inesperado al iniciar corregido** — El juego ya no se cierra inesperadamente al iniciarse con el mod instalado. (Forge, Minecraft 1.20.1 a 1.20.4.)
- **Cierre inesperado de la pantalla Entidades simplificadas corregido** — Abrir la pantalla *Entidades simplificadas* ya no hace que el juego se cierre inesperadamente. (Minecraft 1.21.8 y anteriores.)
- **Criaturas presentes al entrar** — Las criaturas que ya estaban a tu alrededor al entrar en un mundo ahora también aparecen en el horizonte, no solo las que encuentras después.

## 0.4.2 — 2026-10-12

### Correcciones
- **Criaturas estables al alejarse** — Una criatura que se aleja más allá de tu distancia de renderizado ya no desaparece un momento ni se gira hacia otro lado.
- **Criaturas totalmente ocultas por las colinas** — La ropa de un aldeano, un objeto en la mano o la capa exterior de un slime ya no se ven a través de las colinas de Voxy o de Distant Horizons cuando el resto de la criatura está oculto. (Minecraft 1.21.9 y posteriores, con Distant Horizons, o con Voxy y shaders.)

## 0.4.1 — 2026-10-11

### Correcciones
- **Criaturas bien iluminadas con shaders** — Las criaturas lejanas mostradas como imágenes simplificadas ahora reciben la luz del sol como los modelos completos, en lugar de verse demasiado oscuras con paquetes de shaders de Iris como Photon o Complementary.
- **Sombras del terreno corregidas** — Las criaturas lejanas ya no muestran la sombra del terreno que tienen detrás, y el agua de Voxy se ve como debe. (Photon con Voxy.)
- **Criaturas bien ocultas por las colinas** — Las criaturas lejanas ya no se ven a través de las colinas de Voxy o de Distant Horizons. (Minecraft 26.1; Minecraft 26.2 y posteriores con shaders, incluidas versiones antiguas de Iris; Minecraft 26.3 con Iris instalado.)
- **Compatibilidad con Voxy 0.2.18** — El mod vuelve a funcionar con Voxy 0.2.18: las criaturas lejanas se apoyan en su terreno y ya no se ven a través de sus colinas.
- **Criaturas ocultas por las colinas correctas con Distant Horizons** — Las criaturas lejanas ya no quedan ocultas por colinas que no están delante de ellas. (Distant Horizons con el reescalado TAAU de Photon.)

## 0.4.0 — 2026-10-10

### Novedades
- **Primer escaneo automático** — La primera vez que entras en un mundo de un jugador, Living Horizon busca enseguida las criaturas en un radio de 32 chunks, para que el horizonte esté vivo desde el principio. El radio de escaneo ahora se ajusta en chunks, como la distancia de renderizado.
- **Ajustes de animación** — Dos nuevos deslizadores dejan de animar las criaturas lejanas demasiado pequeñas en pantalla o demasiado alejadas, para mejorar el rendimiento.
- **Plantillas de calidad** — Bajo, Normal, Alto y Ultra ajustan con un clic cuántas criaturas lejanas y aves ves, y hasta dónde. Alto y Ultra están pensadas para ordenadores potentes.

### Correcciones
- **Las criaturas conservan sus colores** — Sin shaders, las criaturas lejanas ya no se vuelven negras por un momento, sobre todo mientras vuelan.

## 0.3.2 — 2026-10-09

### Correcciones
- **Criaturas ocultas por las colinas correctas con Photon** — Con Voxy y el reescalado TAAU de Photon, las criaturas lejanas vuelven a quedar ocultas por las colinas correctas.

## 0.3.1 — 2026-10-08

### Correcciones
- **Colinas tenidas en cuenta en versiones antiguas** — Las criaturas lejanas ahora se ocultan tras las colinas de Voxy y de Distant Horizons, como en las versiones recientes, en lugar de verse a través de ellas. (Minecraft 1.20 a 1.21.4.)
- **Compatibilidad con versiones antiguas de Distant Horizons** — El mod ahora funciona con Distant Horizons 2.3 y posteriores, no solo con la última versión.

## 0.3.0 — 2026-10-07

### Novedades
- **Compatibilidad con Vulkan** — Con el renderizador Vulkan del juego, las criaturas lejanas también se ocultan tras las colinas, incluido el terreno de Distant Horizons.

### Correcciones
- **Criaturas visibles delante del terreno de Distant Horizons** — Sin shaders, las criaturas lejanas ya no se desvanecen dentro del terreno de Distant Horizons.
- **Adiós a las manchas en el borde de la distancia de renderizado** — Las manchas que aparecían donde termina la distancia de renderizado normal han desaparecido. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Novedades
- **Criaturas lejanas** — Los animales, aldeanos y gólems siguen vivos en el horizonte más allá de tu distancia de renderizado, pastando y deambulando.
- **Jugadores lejanos** — Los demás jugadores siguen caminando a lo lejos, con su skin, su armadura y su montura. Los jugadores que se desconectan se quedan dormidos donde se fueron.
- **Aves** — Bandadas, gansos volando en V, gaviotas sobre la costa y murciélagos por la noche dan vida al cielo.
- **Compatibilidad con Distant Horizons** — Living Horizon funciona con Distant Horizons además de con Voxy.
- **Criaturas ocultas tras las colinas** — Las criaturas lejanas que están detrás de una colina no se dibujan, lo que mantiene el juego fluido.
- **Imágenes simplificadas a lo lejos** — Las criaturas y los jugadores muy lejanos se pueden mostrar como imágenes simplificadas, mucho más ligeras de dibujar cuando hay muchos. Esta opción está desactivada por defecto.
- **Escaneo de los alrededores** — En los mundos de un jugador, un escaneo lee las criaturas de los chunks que te rodean para que aparezcan enseguida en el horizonte, sin tener que encontrarlas antes.
- **Pantalla de ajustes** — Un botón *Living Horizon...* en el menú Opciones abre los ajustes del mod, con el mundo aún visible detrás, y un botón restablece los valores por defecto.
- **Quilt, NeoForge y Forge** — Living Horizon también funciona en Quilt, NeoForge y Forge, de Minecraft 1.20 a 26.3.
- **Logo del mod** — Living Horizon ahora tiene un logo en la lista de mods y en la de paquetes de datos.
