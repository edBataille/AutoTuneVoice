# AutoTune Voice — projeto Android

Aplicativo experimental para Galaxy A15 e aparelhos Android 10 ou superiores.

## Funções
- Entrada de áudio eclética: usa o microfone do próprio celular, ou um microfone conectado via plugue P2 (entrada com fio), ou um microfone USB-C, detectando e priorizando automaticamente o que estiver plugado (USB-C > P2 > celular).
- VU meter na tela, mostrando em tempo real que o áudio está entrando durante a gravação.
- Grava voz mono em WAV, 44,1 kHz/16 bits.
- Preserva e salva a gravação original.
- Gera uma segunda gravação com correção de afinação offline.
- Permite selecionar escala cromática, maior ou menor e intensidade da correção.
- Salva dois arquivos — original e corrigido — na pasta `Música/AutoTuneVoice`.

## Abrir e gerar o APK
1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Use **Build > Build APK(s)**.
4. O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Teste de entrada de áudio
Basta abrir o app: se nenhum microfone externo estiver conectado, ele usa o microfone do celular normalmente. Ao conectar um microfone com fio (plug P2) ou um microfone/receptor USB-C, o texto da tela é atualizado na hora para indicar qual entrada está em uso — não é preciso reabrir o aplicativo. Se o Android identificar o acessório apenas como dispositivo de áudio genérico, o app ainda tentará usá-lo como entrada.

## Limitação da versão 1.0
O corretor é um algoritmo offline leve e experimental, sem biblioteca comercial de Auto-Tune. Ele funciona melhor com voz isolada, pouco ruído e sem instrumentos vazando no microfone. O deslocamento de afinação é limitado a cerca de ±3,86 semitons por trecho, então desafinações muito grandes são corrigidas apenas parcialmente.

## Apoie o projeto (doação via PIX)
Este app é gratuito e de código aberto. Se ele foi útil pra você, considere uma doação via PIX:

**Chave aleatória:** `fce13f3c-310d-4c5a-8816-efb2e0458842`
**Beneficiário:** Claudio Jose Toldo — Criciúma/SC

![QR Code PIX](pix_qrcode.png)

Ou copie o código abaixo e cole no Pix Copia e Cola do app do seu banco:

```
00020101021126580014br.gov.bcb.pix0136fce13f3c-310d-4c5a-8816-efb2e04588425204000053039865802BR5918CLAUDIO JOSE TOLDO6008CRICIUMA62070503***6304BDB9
```
