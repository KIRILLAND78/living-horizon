# Living Horizon — Registro de alterações

## Próxima versão

## 0.16.0 — 2026-11-05

### Cliente

#### Novidades
- **Suporte ao terreno do Lodistance** — As criaturas e os jogadores distantes agora se escondem atrás do terreno distante do Lodistance como atrás de qualquer colina e se misturam à sua neblina, e as criaturas distantes andam sobre o seu solo, como já fazem com o Voxy e o Distant Horizons. (Minecraft 26.3, com OpenGL, sem shaders.)
- **Suporte ao terreno do Far View** — As criaturas distantes agora aparecem e andam sobre o terreno distante do Far View e se escondem atrás das suas colinas, em vez de ficarem ocultas além da distância de renderização. (Forge e NeoForge, Minecraft 1.20.1 a 1.21.4.)
- **Suporte ao terreno do Ecstatic** — As criaturas distantes agora aparecem sobre o terreno do Ecstatic e andam sobre o seu solo, em vez de sumirem além da distância de renderização. Elas se escondem corretamente atrás das suas colinas, continuam visíveis à frente delas e se misturam à sua neblina. (NeoForge, Minecraft 1.21.1, modo um jogador; a neblina, sem shaders.)
- **Criaturas simplificadas iluminadas pelos shaders** — Com um pacote de shaders que lê mapas normais, como o Photon com *Normal Mapping* ativado, as imagens simplificadas das criaturas distantes agora são iluminadas pelo pacote como os seus modelos 3D, com um lado ao sol e o outro à sombra, em vez de parecerem chapadas e mais escuras. Essas imagens ocupam o dobro de memória de vídeo; o novo ajuste *Iluminação de shaders* permite desativar isso. (Minecraft 1.21.6 e posteriores.)
- **Empurre os jogadores offline** — Agora você pode tirar os jogadores offline do seu caminho andando contra eles. Eles nunca empurram você de volta, já que só existem no seu jogo.
- **Jogadores offline vão pescar** — Os jogadores offline perto da água agora podem parar à beira da água para pescar um pouco. Só um pesca de cada vez no mesmo trecho de margem.
- **Jogadores offline se protegem da chuva** — Quando chove, alguns jogadores offline agora vão se abrigar sob um telhado ou uma árvore ao seu alcance e esperam ali até a chuva passar. Os outros seguem normalmente.
- **Jogadores offline se reúnem perto do fogo** — Ao anoitecer, os jogadores offline agora vão ficar diante de uma fogueira ou de uma fornalha ao seu alcance, cada um no seu próprio lugar.
- **Jogadores offline conversam entre si** — Dois jogadores offline próximos um do outro agora podem se aproximar e conversar um pouco, frente a frente. Nunca mais de dois ao mesmo tempo, e só uma conversa na mesma área.
- **Jogadores offline olham de volta para você** — Os jogadores offline agora se viram para olhar para você quando você olha para eles de perto. Se um deles estiver de frente para você e você agachar, ele agacha também.

#### Melhorias
- **Noites mais naturais para os jogadores offline** — Os jogadores offline agora dormem numa cama ao seu alcance, ou ficam de pé, em vez de se deitarem no chão.
- **Navios distantes mais nítidos** — As imagens simplificadas dos botes e veículos altos, como os veleiros do Shippy Ships, agora são mais nítidas. Os maiores, como o galeão, agora também ganham uma, em vez de serem sempre exibidos como modelo completo, que pesa mais no desempenho.
- **Transição mais suave para as imagens simplificadas** — As criaturas e os jogadores distantes agora giram aos poucos até o ângulo em que a sua imagem simplificada os mostra, conforme se aproximam da distância em que ela os substitui, em vez de virarem de uma vez no momento da troca. A passagem do modelo 3D para a imagem fica menos perceptível.
- **Versões NeoForge 1.21.4 e 1.21.11 mais fáceis de encontrar** — As versões NeoForge para Minecraft 1.21.4 e 1.21.11 agora são publicadas como beta. Assim, elas aparecem no CurseForge e nos launchers que escondem as versões alfa.

#### Correções
- **Criaturas exibidas uma só vez com o Far View** — As criaturas que o Far View desenha como caixas entre 128 blocos e a distância de renderização agora são exibidas uma só vez, em vez de duas, uma sobre a outra. (Forge e NeoForge, Minecraft 1.20.1 a 1.21.4.)
- **Nomes dos jogadores offline exibidos no Forge** — Mirar num jogador offline agora mostra o nome dele e quando ele foi visto pela última vez, em vez de não mostrar nada. (Forge, Minecraft 1.20.1 a 1.20.4.)
- **Melhor integração com o Bobby** — As criaturas distantes agora continuam visíveis logo além da distância até onde o servidor as envia, sobre o terreno que o Bobby guarda, em vez de sumirem. Os jogadores distantes também não param mais de repente ao saírem dessa distância.
- **Galeão do Shippy Ships visível ao longe** — O galeão do Shippy Ships agora continua visível ao longe, onde você o deixou, como os outros navios do mod, em vez de sumir assim que sai da distância de renderização.
- **Jogadores offline continuam se movendo ao longe** — Quando você se afasta, os jogadores offline agora continuam passeando bem onde estão, em vez de voltarem ao lugar onde saíram ou ficarem totalmente parados. Onde não há espaço para passear, eles dão alguns passos, se viram ou se sentam um pouco.

## 0.15.0 — 2026-10-31

### Servidor

#### Novidades
- **Criaturas em terras inexploradas** — O Living Horizon agora mostra aos jogadores de um servidor as criaturas que o mundo colocaria nas regiões que ninguém explorou ainda, até 512 blocos ao redor de cada um, por padrão. As posições delas são estimadas a partir da semente: elas podem ser diferentes, ou nem estar lá, quando você chegar. Esse recurso pode ser desativado, ou o seu alcance alterado, nos ajustes do servidor.
- **Compartilhamento da semente** — Os servidores agora podem permitir que o Living Horizon compartilhe a semente do mundo com os jogadores que têm o mod, para que o próprio jogo deles estime as criaturas em qualquer lugar, pela luneta ou com *Adivinhar criaturas*. Essa opção vem desativada por padrão, já que conhecer a semente revela tudo sobre o mundo.

#### Melhorias
- **Voo com élitros visível ao longe** — O servidor agora informa quais jogadores estão voando com élitros, para que sejam exibidos corretamente planando a longa distância.

### Cliente

#### Novidades
- **Acesso rápido ao Living Horizon** — Os ajustes do mod agora oferecem atalhos para o Modrinth, o CurseForge, o Nexus Mods e o site oficial, além do Discord.
- **Criaturas em áreas inexploradas** — As regiões que ninguém explorou ainda não ficam mais vazias: o Living Horizon estima a partir da semente quais criaturas o mundo colocaria ali, até onde a *Distância das criaturas* alcança. Como são apenas estimativas, elas podem ser diferentes, ou nem estar lá, quando você chegar. Disponível no modo um jogador e nos servidores com o mod; *Criaturas adivinhadas* permite ocultá-las.
- **Criaturas adivinhadas pela luneta** — Olhar pela luneta agora mostra as criaturas adivinhadas das áreas inexploradas onde quer que você mire, por mais longe que seja. Num servidor, ele precisa compartilhar a sua semente.
- **Estime as criaturas ao seu redor** — O novo botão *Adivinhar criaturas* mostra de uma vez as criaturas adivinhadas até 1.024 chunks ao seu redor e as guarda para as suas próximas sessões. Num servidor, ele precisa compartilhar a sua semente.
- **Valores digitados** — Os controles deslizantes dos ajustes do mod que terminam em *Ilimitado* (quantidades máximas e distâncias de renderização) agora aceitam um valor digitado no teclado, mesmo além do último passo, e a página do Sodium também o mostra. Clique com o botão direito num controle deslizante para inserir um valor exato; as setas do teclado também movem cada controle passo a passo.
- **Prévia das distâncias no mundo** — Quando você passa o cursor sobre um ajuste de distância ou o altera, o limite dele agora aparece diretamente no mundo, atrás do menu. Assim, você vê num relance até onde vai o valor escolhido.

#### Melhorias
- **Distância de simplificação opcional** — A *Distância de simplificação* agora pode ser desativada. As criaturas, entidades, jogadores e aves só viram imagens simplificadas quando parecem pequenos o suficiente na tela.
- **Até 4.000 criaturas distantes** — *Máx. de criaturas* agora vai até 4.000 antes de *Ilimitado*.
- **Ilimitado no fim do controle deslizante** — *Ilimitada* agora fica na ponta direita dos controles deslizantes de distância de renderização, depois de 4.000 blocos, em vez da esquerda. Assim, os controles vão da distância mais curta à mais longa.
- **Confirmação antes de Ilimitado** — O jogo agora pede confirmação antes de definir como *Ilimitado* uma quantidade de criaturas, entidades ou jogadores offline, ou uma distância de renderização. Esses valores podem derrubar bastante o FPS, ou até fazer o jogo travar. Na página do Sodium, a pergunta aparece ao clicar em *Aplicar*.
- **Limites padrão mais sensatos** — Nenhum ajuste é mais ilimitado por padrão, para evitar grandes quedas de desempenho: *Distância dos jogadores*, *Distância das animações* e *Distância das entidades* param em 4.000 blocos. As predefinições *Alta* e *Ultra* mostram no máximo 1.500 e 3.000 criaturas, a *Ultra* até 2.048 blocos, e os ajustes que estavam nos antigos valores padrão passam para os novos.
- **Voo com élitros mais natural** — Os jogadores distantes que voam com élitros agora aparecem com as asas abertas, numa pose de voo de verdade, em vez de correrem em pé no ar.
- **Troca de pacotes de recursos mais suave** — As imagens simplificadas das criaturas não são mais refeitas sem necessidade quando um pacote de recursos é desativado e ativado de novo. O carregamento dos recursos também não congela mais o jogo enquanto eles são procurados.
- **Criaturas voadoras do Ice and Fire vistas de todos os ângulos** — Os dragões, anfiteras e hipogrifos do Ice and Fire agora têm imagens simplificadas vistas de baixo e de cima, como os espectros e os aviões, em vez de só de lado. Assim, eles ficam com a aparência certa quando voam sobre você ou quando você os olha do alto.
- **Criaturas gigantes mais nítidas ao longe** — As criaturas muito grandes agora mantêm o seu modelo 3D completo enquanto ainda parecem grandes na tela, em vez de virarem uma imagem simplificada pixelada.

#### Correções
- **Criaturas distantes visíveis no terreno distante** — As criaturas distantes, incluindo as adivinhadas, agora continuam visíveis no terreno distante, em vez de ficarem ocultas como se não houvesse nada embaixo delas: por um tempo depois de abrir um mundo, entre milhares de criaturas adivinhadas, ou quando o Voxy ou o Distant Horizons demora a responder.
- **Neblina do Distant Horizons nas criaturas** — As criaturas distantes agora recebem a mesma neblina que o terreno do Distant Horizons e desaparecem aos poucos com ele, em vez de se destacarem nitidamente na paisagem. (Sem shaders.)
- **Criaturas do Ice and Fire simplificadas corretamente** — Os dragões distantes do Ice and Fire agora têm imagens simplificadas inteiras, no seu tamanho real, em vez de um close das patas ou de uma asa. Dragões de idades diferentes não dividem mais a mesma imagem, e as serpentes marinhas mantêm o seu modelo 3D em vez de uma imagem cortada.
- **Jogadores offline mantêm o seu bote** — Um jogador offline no meio da água não perde mais o bote em que descansa quando você se afasta: ele o mantém de perto e de longe, e da próxima vez que você jogar.
- **Jogadores offline não flutuam mais no ar** — Um jogador que saiu do jogo em pleno ar, planando com élitros ou caindo, não volta mais para lá quando você se afasta: ele descansa no chão abaixo dele. Com o Voxy ou o Distant Horizons, ele já está no chão antes mesmo de você se aproximar.
- **Criaturas distantes descansam com naturalidade** — As criaturas distantes além da *Distância das animações*, ou menores que o *Tam. mín. animado*, agora ficam paradas onde estão, em vez de deslizarem para a frente e para trás com as patas imóveis, tremendo. Elas voltam a andar de onde pararam quando ficam perto o bastante de novo.

## 0.14.0 — 2026-10-29

### Cliente

#### Novidades
- **Veículos de outros mods visíveis ao longe** — Os veículos de outros mods, como os aviões do Immersive Aircraft, agora continuam visíveis além da distância de renderização, onde foram deixados, assim como os botes. Os veículos que você monta sem conduzir, como os carrinhos de mina, vêm desativados por padrão e podem ser ativados em *Criaturas e entidades...*.
- **Ajustes próprios para as entidades** — Os botes, veículos e manequins agora têm ajustes próprios, separados dos das criaturas: se são exibidos, quantos e até que distância. A distância de renderização deles é ilimitada por padrão.
- **Simplificação separada para criaturas e entidades** — *Entidades simplificadas* agora são duas opções: *Criaturas simplificadas* e *Entidades simplificadas*. Os botes, veículos e manequins agora também podem virar imagens simplificadas ao longe, inclusive quando vistos de cima ou de baixo. *Jogadores simplificados* agora é independente, e a tela das imagens agora se chama *Prévia das imagens...*, com uma aba para as entidades.
- **Registro de alterações no jogo** — Um novo botão nos ajustes do mod mostra o que mudou em cada versão, sem sair do jogo. Ele também está disponível na página do Sodium.

#### Melhorias
- **Aviso em servidores incompatíveis** — Quando você entra num servidor sem o Living Horizon, ou com uma versão antiga ou nova demais, uma mensagem no chat agora avisa que os jogadores distantes não serão exibidos ali. A mensagem não aparece se *Mostrar jogadores* estiver desativado.
- **Criaturas e entidades em duas abas** — A tela *Criaturas e entidades...* agora separa as criaturas e as outras entidades em duas abas, para facilitar encontrá-las. Os botes e veículos ali são simplesmente exibidos ou ocultos.
- **Escolha de criaturas aplicada na hora** — Exibir ou ocultar um tipo em *Criaturas e entidades...* agora tem efeito imediato no mundo atrás do menu, sem precisar fechá-lo.
- **Ajustes de criaturas mais organizados** — Os ajustes de *Criaturas e entidades distantes* agora são divididos em três partes: *Gerais*, *Criaturas* e *Entidades*.
- **Jogadores montados mais fiéis ao longe** — Com *Jogadores simplificados*, um jogador distante num bote, num carrinho de mina ou num avião, ou montado num ghast feliz, agora aparece corretamente visto de cima e de baixo, em vez de sempre de lado.
- **Aviões que continuam no ar** — Um jogador que sai do jogo em pleno voo agora continua no ar no seu avião, que paira ou dá voltas, conforme estivesse parado ou em movimento.
- **Origem das imagens exibida** — A tela *Prévia das imagens* agora mostra de onde vem cada imagem (compartilhada, reaproveitada de uma sessão anterior, nova ou não salva), além de quando e em que mundo ela foi criada.
- **Animais sentados continuam sentados** — Gatos, lobos e papagaios mandados sentar agora continuam sentados ao longe, em vez de se levantarem e saírem andando.

#### Correções
- **Travamento em Criaturas e entidades corrigido** — Abrir *Criaturas e entidades...* a partir do menu principal não faz mais o jogo travar. (Minecraft 26.x.)
- **Veículos de mods mantêm as suas cores** — Os veículos e criaturas distantes de alguns mods, como os aviões do Immersive Aircraft, agora mantêm as suas cores quando você se aproxima, em vez de ficarem pretos por um instante.
- **Montarias exibidas uma só vez** — A montaria ou o veículo de um jogador distante, como um avião de mod, não aparece mais uma segunda vez ao lado dele.

## 0.13.0 — 2026-10-28

### Cliente

#### Melhorias
- **Jogo mais fluido com criaturas distantes** — O Living Horizon agora calcula em segundo plano por onde as criaturas distantes e os jogadores offline podem andar, sem deixar o jogo lento.
- **Renderização mais fluida com o Voxy e o Distant Horizons** — O jogo espera menos pelo driver de vídeo a cada quadro quando há criaturas distantes exibidas no terreno do Voxy ou do Distant Horizons. O ganho é maior nas placas NVIDIA com a *Otimização em threads* ativada.
- **Espectros e ghasts voando ao longe** — Os espectros distantes agora dão voltas no céu e os ghasts flutuam devagar, em vez de ficarem imóveis no ar.
- **Espectros e ghasts vistos de baixo** — Como imagens simplificadas, os espectros e os ghasts agora também aparecem vistos de baixo e de cima, como as aves, e não só de lado.
- **Mais fluido com muitas criaturas distantes** — O jogo não perde mais tempo com cada criatura distante a cada quadro. (Vulkan no Minecraft 26.2 e posteriores, Distant Horizons no Minecraft 26.3.)
- **Todas as criaturas visíveis ao longe** — Monstros, morcegos, peixes e todas as outras criaturas agora são exibidos ao longe por padrão e passeiam como os animais. Os endermen, por exemplo, continuam visíveis por todo o End. Os animais que você tinha ocultado continuam ocultos.

#### Correções
- **Botões dos ajustes de volta à ordem** — Os botões na parte de baixo de *Criaturas e entidades distantes* não se sobrepõem mais, e *Restaurar padrões* voltou a ficar à esquerda de *Concluído*. (Minecraft 1.20.1.)
- **Congelamento ao mudar de dimensão corrigido** — O jogo não congela mais de vez quando você atravessa um portal ou sai de um mundo. (Com o Distant Horizons.)

## 0.12.1 — 2026-10-27

### Cliente

#### Correções
- **Comando de benchmark reservado para testes** — `/livinghorizon benchmark` não é mais oferecido aos jogadores: ele serve apenas para testar o mod.

## 0.12.0 — 2026-10-23

### Servidor

#### Novidades
- **Ajustes do servidor** — Os donos de servidor agora podem escolher o que o Living Horizon compartilha: jogadores online, jogadores offline e criaturas, até que distância, só entre colegas de equipe se quiserem, e quais jogadores ou dimensões nunca são compartilhados. Os ajustes ficam em `config/livinghorizon-server.json` e se aplicam em poucos segundos, sem reiniciar.

#### Correções
- **Entrada em servidores Forge corrigida** — Os jogadores com o mod não são mais expulsos de um servidor Forge que também o tem, com a mensagem "mismatched mod channel list". (Forge 1.20.2 e posteriores.)
- **Espectadores e jogadores invisíveis continuam ocultos** — Os jogadores no modo espectador ou sob efeito de invisibilidade não ficam mais visíveis para os outros ao longe, nem no lugar onde saíram.

### Cliente

#### Novidades
- **Botão do Discord** — Os ajustes do mod agora permitem entrar no Discord do Living Horizon com um clique, para tirar uma dúvida ou relatar um problema. O botão também está disponível na página do Sodium.
- **Versão do mod exibida** — As telas de ajustes e o painel de depuração agora mostram a versão do Living Horizon, prática para incluir num relatório de bug.
- **Tamanho de simplificação** — Um novo ajuste transforma as criaturas, jogadores e aves distantes em imagens simplificadas assim que parecem menores que um certo tamanho na tela (10 pixels por padrão), mesmo antes da *Distância de simplificação*. O que mal se vê passa a custar muito menos para desenhar.

#### Melhorias
- **Mais fluido com centenas de criaturas distantes** — *Pular criaturas ocultas* custa muito menos com muitas criaturas e jogadores distantes na tela, e o Living Horizon faz menos trabalho nos bastidores.
- **Versões para Minecraft 1.21.1 mais fáceis de encontrar** — As versões Fabric, Quilt e NeoForge para Minecraft 1.21 e 1.21.1 agora são publicadas como beta. Assim, elas aparecem no CurseForge e nos launchers que escondem as versões alfa.
- **Menos engasgos com multidões ao longe** — As criaturas, jogadores e aves distantes agora pedem muito menos memória ao jogo a cada quadro, o que reduz os engasgos.
- **Mais fluido com muitas aves** — As aves atrás de você ou pequenas demais para serem vistas não são mais desenhadas.

#### Correções
- **Criaturas distantes sempre em terreno firme** — As criaturas distantes não são mais desenhadas onde ainda não há terreno visível embaixo delas, seja o do jogo, o do Voxy ou o do Distant Horizons. Elas não flutuam mais no vazio.
- **Mundos de um jogador abrem de novo no Forge** — Um mundo no modo um jogador não fecha mais assim que abre, com a mensagem "mismatched mod channel list". (Forge 1.20.2 e posteriores.)
- **Criaturas distantes firmes no terreno do Distant Horizons** — No modo um jogador, as criaturas distantes não somem mais nem param de andar nas colinas e montanhas do Distant Horizons.

## 0.11.0 — 2026-10-22

### Servidor

#### Melhorias
- **Mais leve em servidores grandes** — O servidor gasta menos tempo escolhendo quais skins e montarias enviar a cada jogador. As listas longas de jogadores offline agora são enviadas em várias partes, em vez de uma única mensagem grande demais para o jogo.
- **Atualização necessária para os jogadores** — Num servidor com esta versão, os jogadores também precisam dela para ver os jogadores distantes.

#### Correções
- **Arquivo dos jogadores offline protegido** — Se o servidor não conseguir ler `livinghorizon-positions.dat`, o arquivo não é mais sobrescrito: ele é guardado como `livinghorizon-positions.dat.bad` e o console avisa.

### Cliente

#### Novidades
- **Benchmark** — O comando `/livinghorizon benchmark` mede quanto custa cada parte do mod no seu mundo e gera um relatório para anexar a um relatório de bug.
- **Mundo de benchmark** — `/livinghorizon benchmark world` cria um mundo de teste sempre igual, cheio de criaturas e jogadores offline, para que as medições possam ser comparadas de uma vez para outra. O seu próprio mundo não é alterado.
- **Máx. offline** — Um novo ajuste limita quantos jogadores offline são exibidos ao mesmo tempo, os mais próximos primeiro (256 por padrão). Os outros reaparecem conforme você se aproxima.

#### Melhorias
- **Mensagem mais clara quando as versões diferem** — Quando o mod do servidor é antigo ou novo demais para o seu, o registro agora diz qual dos dois atualizar, em vez de afirmar que o servidor não tem o mod.
- **Mais fluido com muitos jogadores offline** — Os jogadores offline custam menos para exibir ao longe, e os que estão escondidos atrás de colinas não são mais desenhados.

#### Correções
- **Distância dos jogadores respeitada** — Os jogadores offline não são mais exibidos além da *Distância dos jogadores*.
- **Manequins distantes com a sua skin de verdade** — Os manequins distantes agora mantêm a skin que usam de perto, seja de um jogador ou de um pacote de recursos, em vez de virarem a Alex. (Minecraft 1.21.9 e posteriores.)
- **Água preservada no Photon com o Distant Horizons** — Com *Ocultar atrás do relevo* ativado, a água agora continua normal quando uma criatura ou um jogador distante está à vista, em vez de deixar à mostra o fundo nu e sombreado. (Minecraft 26.3.)
- **Capas dos manequins caem com naturalidade** — As capas dos manequins distantes agora caem como de perto, em vez de ficarem levantadas no ar. (Minecraft 1.21.9 e posteriores.)
- **Arquivo de ajustes protegido** — Um erro ao editar `livinghorizon.json` à mão não apaga mais o arquivo: ele é guardado como `livinghorizon.json.bad` e o registro avisa.
- **Dados salvos protegidos** — Se o jogo fechar durante um salvamento, os arquivos das criaturas lembradas (aquelas que o Living Horizon continua exibindo ao longe) e dos jogadores offline não são mais danificados. Um arquivo danificado é guardado como `.bad` em vez de ser esvaziado.
- **Entrada mais fluida em servidores grandes** — Entrar num servidor com muitos jogadores offline não faz mais o jogo engasgar.
- **Imagens danificadas refeitas automaticamente** — Uma imagem simplificada salva e danificada não pode mais fazer o jogo ficar sem memória: ela é simplesmente refeita.
- **Criaturas distantes visíveis diante do terreno do Distant Horizons com o Oculus** — Com o Oculus e um pacote de shaders, as criaturas distantes não somem mais atrás do terreno do Distant Horizons, um problema que piorava conforme a distância de renderização aumentava. (Forge.)
- **Movimentos de cabeça naturais com o Entity Culling** — As criaturas e os jogadores distantes não balançam mais a cabeça muito rápido com o Entity Culling, sozinho ou com o Chloride.
- **Jogadores na dimensão de mod correta** — Os jogadores online e offline de uma dimensão de mod, como o Aether, não são mais exibidos em outra, como a Twilight Forest. Num servidor, ele também precisa desta versão.
- **Neblina do Voxy nas criaturas** — As criaturas distantes no terreno do Voxy agora recebem a mesma neblina que a paisagem ao redor, em vez de se apagarem na cor da neblina além de certa distância. (Sem shaders.)

## 0.10.0 — 2026-10-20

### Servidor

#### Novidades
- **Living Horizon nos servidores** — O mod agora também pode ser instalado no servidor: coloque o jar na pasta `mods` dele (Fabric, Quilt, NeoForge ou Forge, com a Fabric API no Fabric e no Quilt). Não é preciso configurar nada, e ele substitui o pacote de dados.
- **Jogadores compartilhados em tempo real** — O servidor envia a posição de cada jogador cinco vezes por segundo aos jogadores que têm o mod, com a skin, a armadura, os itens na mão e a montaria.
- **Criaturas compartilhadas** — O servidor envia as criaturas a até 2.048 blocos de cada jogador, mesmo as que esse jogador nunca encontrou: animais, aldeões, golens, serenos e botes, com sela, arreio, armadura ou baú.
- **Jogadores offline compartilhados** — O servidor lembra onde cada jogador saiu, a aparência, a montaria e quando saiu, e repassa isso a todos, inclusive aos jogadores que entrarem depois.
- **Jogadores de antes da instalação** — Na primeira inicialização, o servidor encontra os jogadores que o mundo já conhecia e os mostra como jogadores offline onde saíram, com o seu equipamento.
- **Salvo com o mundo** — Tudo é salvo em `livinghorizon-positions.dat`, na pasta do mundo. Uma criatura é esquecida quando o seu chunk carrega e ela não está mais lá.
- **Mundos em LAN** — Um mundo aberto em LAN compartilha tudo pelo jogo do anfitrião, sem precisar instalar mais nada.
- **Jogadores sem o mod são bem-vindos** — Os jogadores sem o mod ainda podem entrar; o servidor simplesmente não envia nada a eles.
- **Mesma versão dos dois lados** — Mantenha o mod atualizado tanto no servidor quanto no jogo dos jogadores: um jogo que não entende as mensagens do servidor as ignora.
- **Privacidade: as posições são compartilhadas** — O servidor compartilha a posição de cada jogador com todos os jogadores que têm o mod, a qualquer distância. Não deixe de avisar os seus jogadores.
- **Fim do pacote de dados** — O pacote de dados não é mais usado: remova-o da pasta `datapacks` do mundo. As coordenadas não passam mais pelo placar, então não aparecem mais nas pontuações nem na barra lateral.

#### Correções
- **Jogadores mantidos quando um mundo fecha** — Quando um servidor ou um mundo em LAN fecha, os jogadores que ainda estavam nele agora ficam onde estavam, como jogadores offline, em vez de sumirem.

### Cliente

#### Novidades
- **Todos os jogadores, a qualquer distância** — Num servidor com o mod, agora você vê todos os outros jogadores, onde quer que estejam, com a skin, a armadura, os itens na mão e a montaria.
- **Criaturas que você nunca encontrou** — Num servidor com o mod, as criaturas distantes aparecem na hora, mesmo aquelas de que você nunca chegou perto, com sela, arreio, armadura ou baú.
- **Jogadores offline visíveis ao entrar** — Você vê os jogadores offline onde saíram, com a aparência e a montaria deles, mesmo que tenham saído antes de você chegar.
- **Jogadores offline no modo um jogador** — No modo um jogador, os jogadores offline do seu mundo aparecem assim que você o abre, inclusive os de antes da instalação do mod.
- **Um horizonte vivo desde o início** — As criaturas distantes e os jogadores offline aparecem assim que você entra, e não alguns segundos depois.
- **Servidores sem o mod** — Você ainda pode entrar neles. Só as criaturas que você já encontrou são exibidas ali, sem jogadores distantes, e o registro avisa uma vez.

#### Melhorias
- **Posições dos jogadores distantes mais confiáveis** — Os jogadores distantes agora são posicionados apenas pelas posições que o servidor compartilha. A estimativa pela barra localizadora deixou de existir, assim como o ajuste *Tempo de jogador perdido*.

#### Correções
- **Ghasts felizes exibidos corretamente** — Um jogador distante montado num ghast feliz não tem mais um segundo ghast ao lado, e quem já estava montado quando você entrou não flutua mais no ar.
- **Ghasts felizes ficam onde foram deixados** — Quando um jogador distante desce do seu ghast feliz, ele agora fica onde foi deixado, em vez de reaparecer onde foi montado pela primeira vez e voltar voando.
- **Skins novas visíveis ao longe** — Um jogador distante que troca de skin agora aparece com a nova, em vez de manter a antiga ao longe.
- **Jogadores bem sentados nos botes** — Um jogador que sai do jogo num bote agora fica virado para o mesmo lado que o bote, em vez de sentado de lado.
- **Cavaleiros bem sentados** — Um jogador distante montado num cavalo agora fica sentado corretamente, como modelo completo ou como imagem simplificada, em vez de afundar no cavalo ou se sentar na garupa.
- **Um só cavalo depois de sair** — Um jogador que sai do jogo montado num cavalo não deixa mais uma cópia do cavalo ao lado daquele em que está sentado.
- **Botões Website e Issues funcionando** — Os botões Website e Issues na página do mod no Mod Menu não ficam mais acinzentados. (Fabric.)

## 0.9.0 — 2026-10-19

### Correções
- **Jogadores offline fora da água** — Os jogadores que saíram enquanto nadavam não ficam mais de pé dentro da água: eles esperam na margem mais próxima, ou remam num bote se a margem estiver a mais de 10 blocos.
- **Água intacta ao redor dos botes** — A água não fica mais transparente ao redor dos jogadores offline que remam num bote com um pacote de shaders como o Photon, e não desaparece mais num grande quadrado ao redor dos botes distantes.
- **Remadores mantêm o seu bote** — Ao longe, os jogadores offline que remam num bote agora mantêm o bote enquanto a sua imagem simplificada é criada, em vez de aparecerem de pé sem ele.

## 0.8.0 — 2026-10-18

### Novidades
- **Jogadores simplificados** — Um novo ajuste permite manter os jogadores distantes como modelos 3D completos, enquanto as criaturas distantes continuam virando imagens simplificadas.
- **12 novos idiomas** — O Living Horizon agora está disponível em alemão, espanhol, italiano, português do Brasil, polonês, turco, russo, ucraniano, chinês simplificado e tradicional, japonês e coreano.
- **Ajustes no Sodium** — Os ajustes do Living Horizon agora podem ser acessados pelos *Ajustes gráficos* do Sodium, em páginas próprias. (Sodium 0.8 e posteriores.)
- **Jogadores offline com a sua montaria** — Um jogador que saiu montado num cavalo, num bote ou num ghast feliz continua montado perto de onde saiu.
- **Controle da memória de vídeo** — A tela *Entidades simplificadas...* mostra quanta memória de vídeo as imagens das criaturas e dos jogadores usam, e dois novos ajustes definem um limite para cada uma.

### Melhorias
- **Ajustes mais claros** — Cada ajuste agora tem um nome e uma descrição mais claros, no estilo das opções do próprio Minecraft, e os controles deslizantes de distância ocupam toda a largura da tela.
- **Jogadores offline mais animados** — Os jogadores offline agora dão uma pequena volta ao redor de onde saíram. Ao longe, eles viram imagens simplificadas como os jogadores online, seja em pé, sentados, deitados ou na cama.
- **Cavaleiros mais leves** — Ao longe, um jogador montado num cavalo, num bote, num carrinho de mina ou em qualquer outra montaria agora forma uma única imagem simplificada com ela, muito mais leve que dois modelos completos.
- **Grandes multidões de jogadores mais fluidas** — Acima de uns cem jogadores distantes, os excedentes agora também viram imagens simplificadas, em vez de continuarem como modelos completos.

### Correções
- **Criaturas bem ocultas depois de redimensionar** — As criaturas distantes não aparecem mais através das colinas, incluindo as do Voxy e do Distant Horizons, depois que a janela do jogo é redimensionada.
- **Jogadores e slimes visíveis com o Distant Horizons** — Os jogadores distantes, os jogadores offline e os slimes não somem mais sob o terreno do Distant Horizons. (Minecraft 26.1 e posteriores.)
- **Criaturas inteiras diante do terreno do Distant Horizons** — O terreno do Distant Horizons não corta mais as criaturas e os jogadores que estão na frente dele. (Minecraft 26.1 e posteriores.)
- **Criaturas estáveis com o balanço da visão** — Com o balanço da visão ativado, as criaturas distantes não piscam mais e o terreno do Distant Horizons não oscila mais na frente delas a cada passo. (Minecraft 26.1 e posteriores.)

## 0.7.0 — 2026-10-17

### Novidades
- **Jogadores offline ganham vida** — Os jogadores offline agora vivem ao redor de onde saíram: passeiam, se sentam, olham baús e estações de trabalho, vão para a cama à noite e levantam o escudo quando são atingidos ou quando monstros se aproximam. Eles sempre ficam no chão: caem se você quebrar o bloco embaixo deles, e sobem de volta se você os enterrar.
- **Marca "(offline)"** — Os jogadores offline mostram *(offline)* depois do nome, e olhar para um deles de perto mostra quando ele foi visto pela última vez. Os nomes deles, ou os próprios jogadores offline, podem ser ocultados nos ajustes.
- **Aves em 3D** — As aves agora são pequenos modelos 3D como as criaturas do Minecraft: batem as asas, ficam de pé nas patas quando pousadas e viram imagens simplificadas ao longe. O ajuste *Estilo das aves* traz de volta as antigas aves em 2D.
- **Hmm…** — Acho que vi alguma coisa no céu. 😉

### Melhorias
- **Criaturas saltitantes** — Os slimes, cubos de magma e coelhos distantes agora se movem aos pulos em vez de deslizarem. Os slimes se achatam ao cair, e as galinhas batem as asas ao descer um bloco.
- **Patos mais naturais** — Os patos agora levantam voo juntos quando você se aproxima e pousam mais adiante na água, em vez de irem embora de vez.

### Correções
- **Criaturas animadas pela luneta** — As criaturas distantes vistas pela luneta agora contam pelo seu tamanho ampliado e continuam andando quando parecem grandes, em vez de congelarem. (Ajuste *Tam. mín. animado*.)
- **Criaturas animadas ao abrir um mundo** — As criaturas distantes agora andam e pastam desde o início, em vez de girarem no lugar.
- **Aves pousadas mais naturais** — Chapins, piscos e patos não vão mais para a frente e para trás, não flutuam mais no ar, não ficam mais dentro das folhas e não nadam mais em terra firme. Os patos não aparecem mais bem ao seu lado só para sair voando e agora nadam pela sua lagoa, e os piscos e chapins saltitam mais e voam menos.
- **Licença correta na lista de mods** — O Living Horizon agora mostra a licença correta na lista de mods. (Forge, NeoForge e Quilt.)

## 0.6.0 — 2026-10-15

### Novidades
- **Imagens das criaturas guardadas** — As imagens simplificadas das criaturas distantes agora são salvas no disco e reaproveitadas de uma sessão para outra. Elas só são refeitas depois de uma mudança de pacotes de recursos, de mods ou de versão do jogo.

### Melhorias
- **Novo ícone** — O Living Horizon tem um novo ícone em pixel art: Steve em primeiro plano, cercado de criaturas, aldeões, jogadores e aves até as montanhas.
- **Criaturas distantes mais coerentes quando você volta** — No modo um jogador, as criaturas distantes se teletransportam ou correm para outro lugar com muito menos frequência quando você volta: elas acompanham as criaturas reais enquanto o jogo continua a movê-las, e somem quando estas morrem.

### Correções
- **Tela de Entidades simplificadas sem piscar** — Duas criaturas com o mesmo nome, como aldeões com roupas diferentes, não piscam mais trocando de lugar.
- **Imagens das criaturas mais limpas** — As criaturas distantes exibidas como imagens simplificadas não têm mais linhas escuras finas nem pontinhos pretos na borda de cima ou de baixo. As criaturas compridas, como os cavalos e os farejadores, não ficam mais cortadas no focinho ou na cauda.
- **Travamento com criaturas do GeckoLib corrigido** — Um mundo não trava mais alguns segundos depois de carregar quando uma criatura de um mod que usa o GeckoLib foi vista ao longe. Essas criaturas ainda não são exibidas ao longe. (Minecraft 1.21.2 a 1.21.8.)
- **Entidades simplificadas mais rápidas** — O jogo não fica mais lento com as imagens simplificadas: as mesmas criaturas não são mais redesenhadas sem parar, e as criaturas parecidas dividem uma única imagem.

## 0.5.0 — 2026-10-13

### Novidades
- **Entidades simplificadas no Minecraft 1.20.1 a 1.21.8** — As criaturas e os jogadores muito distantes agora também podem ser exibidos como imagens simplificadas nessas versões, o que alivia bastante o trabalho do seu computador.

### Correções
- **Travamento ao iniciar corrigido** — O jogo não trava mais ao iniciar com o mod instalado. (Forge, Minecraft 1.20.1 a 1.20.4.)
- **Travamento da tela de Entidades simplificadas corrigido** — Abrir a tela *Entidades simplificadas* não faz mais o jogo travar. (Minecraft 1.21.8 e anteriores.)
- **Criaturas presentes ao entrar** — As criaturas que já estavam ao seu redor quando você entrou num mundo agora também aparecem no horizonte, e não só as que você encontra depois.

## 0.4.2 — 2026-10-12

### Correções
- **Criaturas estáveis ao se afastarem** — Uma criatura que vai além da sua distância de renderização não some mais por um instante nem se vira para outro lado.
- **Criaturas totalmente escondidas pelas colinas** — As roupas de um aldeão, um item na mão ou a camada externa de um slime não aparecem mais através das colinas do Voxy ou do Distant Horizons enquanto o resto da criatura está escondido. (Minecraft 1.21.9 e posteriores, com o Distant Horizons, ou com o Voxy e shaders.)

## 0.4.1 — 2026-10-11

### Correções
- **Criaturas bem iluminadas com shaders** — As criaturas distantes exibidas como imagens simplificadas agora são iluminadas pelo sol como os modelos completos, em vez de ficarem escuras demais com pacotes de shaders do Iris, como o Photon ou o Complementary.
- **Sombras do terreno corrigidas** — As criaturas distantes não mostram mais a sombra do terreno atrás delas, e a água do Voxy fica como deveria. (Photon com Voxy.)
- **Criaturas bem escondidas pelas colinas** — As criaturas distantes não aparecem mais através das colinas do Voxy ou do Distant Horizons. (Minecraft 26.1; Minecraft 26.2 e posteriores com shaders, incluindo versões antigas do Iris; Minecraft 26.3 com o Iris instalado.)
- **Suporte ao Voxy 0.2.18** — O mod volta a funcionar com o Voxy 0.2.18: as criaturas distantes ficam sobre o seu terreno e não aparecem mais através das suas colinas.
- **Criaturas escondidas pelas colinas certas com o Distant Horizons** — As criaturas distantes não são mais escondidas por colinas que não estão na frente delas. (Distant Horizons com o upscaling TAAU do Photon.)

## 0.4.0 — 2026-10-10

### Novidades
- **Primeira busca automática** — Na primeira vez que você entra num mundo no modo um jogador, o Living Horizon procura na hora as criaturas num raio de 32 chunks, para que o horizonte fique vivo desde o início. O raio da busca agora é definido em chunks, como a distância de renderização.
- **Ajustes de animação** — Dois novos controles deslizantes param de animar as criaturas distantes que estão pequenas demais na tela ou longe demais, para melhorar o desempenho.
- **Predefinições de qualidade** — Baixa, Normal, Alta e Ultra definem com um clique quantas criaturas distantes e aves você vê, e até que distância. Alta e Ultra são pensadas para computadores potentes.

### Correções
- **Criaturas mantêm as suas cores** — Sem shaders, as criaturas distantes não ficam mais pretas por um instante, principalmente enquanto voam.

## 0.3.2 — 2026-10-09

### Correções
- **Criaturas escondidas pelas colinas certas com o Photon** — Com o Voxy e o upscaling TAAU do Photon, as criaturas distantes voltam a ser escondidas pelas colinas certas.

## 0.3.1 — 2026-10-08

### Correções
- **Colinas levadas em conta nas versões antigas** — As criaturas distantes agora se escondem atrás das colinas do Voxy e do Distant Horizons, como nas versões mais recentes, em vez de aparecerem através delas. (Minecraft 1.20 a 1.21.4.)
- **Suporte a versões antigas do Distant Horizons** — O mod agora funciona com o Distant Horizons 2.3 e posteriores, e não só com a versão mais recente.

## 0.3.0 — 2026-10-07

### Novidades
- **Suporte ao Vulkan** — Com o renderizador Vulkan do jogo, as criaturas distantes também se escondem atrás das colinas, incluindo o terreno do Distant Horizons.

### Correções
- **Criaturas visíveis diante do terreno do Distant Horizons** — Sem shaders, as criaturas distantes não se apagam mais no terreno do Distant Horizons.
- **Fim dos borrões na borda da distância de renderização** — Os borrões que apareciam onde termina a distância de renderização normal sumiram. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### Novidades
- **Criaturas distantes** — Os animais, aldeões e golens continuam vivos no horizonte além da sua distância de renderização, pastando e passeando.
- **Jogadores distantes** — Os outros jogadores continuam andando ao longe, com a skin, a armadura e a montaria. Os jogadores que saem do jogo ficam dormindo onde saíram.
- **Aves** — Bandos, gansos voando em V, gaivotas sobre o litoral e morcegos à noite dão vida ao céu.
- **Suporte ao Distant Horizons** — O Living Horizon funciona com o Distant Horizons, além do Voxy.
- **Criaturas escondidas atrás das colinas** — As criaturas distantes atrás de uma colina não são desenhadas, o que mantém o jogo fluido.
- **Imagens simplificadas ao longe** — As criaturas e os jogadores muito distantes podem ser exibidos como imagens simplificadas, muito mais leves de desenhar quando são muitos. Essa opção vem desativada por padrão.
- **Busca nos arredores** — No modo um jogador, uma busca lê as criaturas dos chunks ao seu redor para que apareçam no horizonte na hora, sem precisar encontrá-las antes.
- **Tela de ajustes** — Um botão *Living Horizon...* no menu Opções abre os ajustes do mod, com o mundo ainda visível atrás, e um botão restaura os padrões.
- **Quilt, NeoForge e Forge** — O Living Horizon também funciona no Quilt, no NeoForge e no Forge, do Minecraft 1.20 ao 26.3.
- **Logo do mod** — O Living Horizon agora tem um logo na lista de mods e na lista de pacotes de dados.
