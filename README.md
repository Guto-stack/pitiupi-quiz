# Plugin Quiz ao vivo

Quiz em tempo real para a sala inteira, no estilo de programa de auditório. Um peer é o **apresentador** e projeta a tela; os demais entram como **jogadores** e respondem contra o relógio pelo próprio Pitiupi. Não exige nenhuma mudança no núcleo.

## Como jogar

1. O apresentador abre **Quiz ao vivo** na aba **Plugins** e clica em **Criar sala**. No lobby, escolhe o tempo por pergunta, quantas perguntas e, se quiser, carrega um `.txt` próprio.
2. Os jogadores abrem **Quiz ao vivo**. A sala aparece sozinha em **Salas abertas na rede**; basta clicar em **Entrar**.
3. O apresentador clica em **Começar**. Cada pergunta mostra quatro alternativas (▲ vermelho, ◆ azul, ● amarelo, ■ verde) e uma barra de tempo.
4. A pergunta encerra quando todos respondem ou quando o tempo acaba. Aparecem a alternativa certa, os votos de cada uma e o placar.
5. Depois da última pergunta aparece o pódio. **Jogar de novo** volta ao lobby com os mesmos jogadores.

**Pontuação:** acerto vale 500 pontos mais até 500 pela rapidez (`500 × tempo restante / tempo total`). A partir de 3 acertos seguidos, cada acerto ganha +100. Erro ou falta de resposta vale 0.

**Robôs de teste:** no lobby, **+ Robô de teste** põe um jogador automático na sala. Cada robô tem id, ACKs e retransmissões próprios. Os datagramas dele saem pelo socket multicast real e voltam pelo loopback. Servem para demonstrar o jogo num único computador: no Linux, duas janelas do Pitiupi não abrem a mesma porta TCP, então não dá para ter dois peers na mesma máquina.

## Perguntas próprias

Uma pergunta por linha, campos separados por `|`, e o último campo é o número da alternativa correta (1 a 4). Linhas vazias e linhas iniciadas com `#` são ignoradas.

```
Qual protocolo de transporte NÃO garante entrega nem ordem? | TCP | UDP | HTTP | ARP | 2
```

O banco embutido (25 perguntas de Redes de Computadores) está em `src/pitiupi/quiz/resources/perguntas-redes.txt` e serve de exemplo.

## Protocolo

**Teoria:** UDP não garante entrega nem ordem e pode duplicar. Multicast entrega um datagrama a todos do grupo com um único envio. Para ter confiabilidade sobre UDP, a aplicação confirma (ACK), retransmite o que não foi confirmado e descarta duplicatas.

**No Pitiupi:** tudo é uma `QuizMessage`, enviada por UDP multicast (`224.0.0.3`, a porta do login), o mesmo caminho do heartbeat: se o colega aparece na lista, o quiz chega até ele. Cada janela gera um id aleatório. O campo `to` indica o destinatário (`null` = todos) e quem não é o destinatário descarta a mensagem.

| Tipo | Sentido | Entrega |
| --- | --- | --- |
| `STATE` | apresentador → todos | Retrato completo da sala (`RoomState`): fase, pergunta, alternativas, tempo restante, votos, placar. Vai a cada 1 s e, quando algo muda, em 3 cópias |
| `JOIN` | jogador → apresentador | Confiável: retransmitido a cada 500 ms até o `ACK` |
| `ANSWER` | jogador → apresentador | Confiável: a primeira resposta é a que vale |
| `LEAVE` | jogador → apresentador | Simples; se perder, o jogador só fica parado no placar |
| `ACK` | destinatário → remetente | Confirma o `msgId` recebido |

Decisões:

- **Estado completo em vez de eventos.** Cada `STATE` descreve a sala inteira. Um datagrama perdido é corrigido pelo seguinte, e quem entra no meio da partida se sincroniza sozinho. Uma `version` crescente descarta cópias atrasadas de retratos antigos, que fariam a tela voltar no tempo.
- **Tempo relativo.** O apresentador manda quanto falta, não a hora do fim. O jogador soma ao próprio relógio, então as máquinas não precisam estar sincronizadas.
- **Pontuação pelo tempo do jogador.** O jogador mede quanto tempo levou para responder e envia junto. Uma retransmissão não tira pontos de quem respondeu rápido. O apresentador limita o valor ao tempo da pergunta e recusa respostas que chegam mais de 1,5 s depois do fim.
- **Atraso de rede medido.** O apresentador mede do envio da pergunta até a resposta chegar, e o jogador mede só o tempo de pensar. A diferença é o caminho de ida (pergunta) e volta (resposta) pela rede, exibido como *atraso médio das respostas*.
- **RTT pelo algoritmo de Karn.** O tempo até o `ACK` só é amostrado quando não houve retransmissão; com retransmissão, não dá para saber a qual envio o `ACK` responde.
- **Uma thread de protocolo.** Toda a lógica roda numa única thread (`ReliableChannel.execute`). Os controllers não precisam de trava, e o Swing só é tocado via `invokeLater`.

O rodapé **Rede** mostra datagramas enviados e recebidos, retransmissões, duplicatas descartadas, ACKs, RTT e atraso das respostas. **Simular perda de 30% dos pacotes** descarta de propósito parte dos envios daquela janela, para mostrar ao vivo o ACK e a retransmissão recuperando.

**Limitações:** não há criptografia nem autenticação; qualquer peer da sala pode ler as mensagens, e um jogador modificado poderia mentir o tempo de resposta. O apresentador é ponto único: se a janela dele fechar, a sala some para todos após 5 s.

## Build e execução

```bash
./scripts/pitiupi.sh run --plugins chat,quiz   # compila núcleo + plugins e abre
# ou só o plugin:
cd plugins/quiz && ant jar                      # requer pitiupi/dist/pitiupi.jar
```

Todos precisam da **mesma versão do JAR**, da **mesma porta** no login e da mesma sub-rede.

| Pacote | Conteúdo |
| --- | --- |
| `model` | `Question`, `QuestionBank`, `RoomState`, `PlayerScore`, `Phase` — sem rede nem Swing |
| `net` | `QuizMessage`, `ReliableChannel` (ACK, retransmissão, duplicatas, perda simulada), `NetStats` |
| `control` | `HostController` (dono da sala e do placar), `PlayerController`, `BotPlayer`, `QuizCore` |
| `GUI` | `QuizPanel` e as telas: início, apresentador, jogador, pódio |
| `plugin` | `QuizPlugin`, a integração com o núcleo |
