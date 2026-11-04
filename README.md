# 👑 NINE KINGDOM 8.0 — PROJETO APK ANDROID

Este ZIP contém um **projeto Android completo na raiz**, preparado para o fluxo
**Android → ZiptoGit → GitHub Actions → baixar APK**. O jogo existente é o
`nine_kingdom_v80_android.html` enviado pelo usuário, sem reorganização de mapa,
NPCs, interface ou inventário. O empacotamento **não realiza as mudanças de design**
discutidas em outra etapa da conversa.

## Escolha técnica

- **HTML/CSS/JavaScript original + WebView Android + Java nativo.** O jogo já está
  construído em Canvas e JavaScript. Reescrever em Kotlin, Flutter ou Godot
  mudaria a lógica e criaria risco desnecessário de regressão.
- `WebViewAssetLoader` abre o conteúdo interno do APK em origem HTTPS local,
  permitindo `localStorage` sem exigir internet ou servidor.
- Java nativo abre o seletor de documentos do Android para importar e exportar
  o backup JSON, sem pedir permissão ampla de armazenamento.
- **Offline:** o jogo, gráficos, menus e lógica estão embutidos no APK. Não há
  URLs externas no HTML original. O GitHub Actions precisa de internet para
  **construir** o APK; o jogo instalado não precisa de conexão.
- Requisitos: **Android 6.0/API 23 ou superior**, WebView do sistema funcional.
  Para melhor desempenho, mantenha Android System WebView/Chrome atualizado.
- Configuração do build: AGP 8.13.2, Gradle 8.13, Java 17, compileSdk 36,
  targetSdk 36, minSdk 23, androidx.webkit 1.15.0.
- Aplicativo `Nine Kingdom`, ID `com.ninekingdom.mobile`, `versionCode=80`,
  `versionName=8.0`.

## 📥 Como gerar o APK sem computador

1. No celular, crie um repositório GitHub vazio na branch `main` (sem README
   automático, se o ZiptoGit publicar melhor assim).
2. Entre no ZiptoGit, escolha esse repositório e envie **o ZIP ORIGINAL,
   sem extrair nem recompactar**. Os arquivos devem aparecer diretamente na
   raiz do repositório (`app/`, `build.gradle`, `.github/` ...).
3. Confira no GitHub se o arquivo `.github/workflows/main.yml` está presente.
4. Abra **Actions** e procure **Compilar APK Android - Nine Kingdom**. Se o
   envio não iniciou o workflow, abra-o e toque em **Run workflow** (`main`).
5. Aguarde o job `build-apk` concluir com indicador verde. Abra a execução,
   em **Artifacts**, baixe `nine-kingdom-v8-android-debug-apk`.
6. O GitHub entrega um ZIP de artefato; abra ou extraia **esse ZIP de artefato**
   (não o ZIP original do projeto). Encontre `nine-kingdom-v8-debug.apk`.
7. Instale o APK no Android. O sistema pode exigir autorizar a instalação
   por este navegador/gerenciador de arquivos. Abra o jogo, teste os botões,
   controle por toque, salvar, exportar JSON e importar JSON.

**Contingência para arquivo oculto `.github`**: se o ZiptoGit não publicar
`.github/workflows/main.yml`, no site do GitHub abra o repositório, toque em
**Add file → Create new file**, digite o caminho completo
`.github/workflows/main.yml` e cole o conteúdo do arquivo avulso `main.yml`
fornecido junto com este ZIP. Faça commit na branch `main`. Isso também dispara
um workflow. Se a interface não permitir diretórios, digite todo o caminho
no campo de nome do novo arquivo; o GitHub cria as pastas.

## Arquivos e pastas

- `.github/workflows/main.yml`: workflow automático por `push` em `main` e manual.
- `app/src/main/assets/game.html`: jogo 8.0 incorporado, com uma adaptação
  mínima no método `exportSave()` para chamar o seletor de documentos Android.
- `original/nine_kingdom_v80_android.html`: HTML **intacto** como referência.
- `app/src/main/java/com/ninekingdom/mobile/MainActivity.java`: WebView, pontes
  de backup JSON, diálogo de saída, erros e caixas de confirmação das missões.
- `app/src/main/res/drawable/ic_launcher.xml`: ícone simples de coroa.
- `app/src/main/AndroidManifest.xml`: configuração do aplicativo, sem permissões.
- `app/build.gradle`, `build.gradle`, `settings.gradle`: build Gradle.

## 💾 Backup e salvamentos

- O jogo mantém o progresso via `localStorage` do WebView no armazenamento
  privado do aplicativo; não precisa de conta nem de internet.
- **Exportar**: toque em Backup (ou Bolsa → Baixar salvamento) e escolha pelo
  seletor Android o local/pasta e nome do arquivo JSON. O Android só autoriza
  gravar **o arquivo escolhido pelo usuário**.
- **Importar**: toque em Recuperar ou Bolsa → Importar arquivo e selecione um
  JSON no seletor do Android. Leia a confirmação antes de substituir progresso.
- Desinstalar/limpar dados do aplicativo **apaga** os salvamentos locais. Guarde
  um backup JSON antes. Reinstalar em outro aparelho não transfere `localStorage`.
- Nenhuma permissão `MANAGE_EXTERNAL_STORAGE`, internet ou acesso a toda a
  galeria/arquivos é solicitada.

## Versões e atualizações

O APK `assembleDebug` é **de depuração** e assinado pelo ambiente de build.
A assinatura debug gerada no GitHub Actions pode variar entre execuções;
portanto o Android pode recusar uma atualização sobre APK anterior mesmo que
`versionCode` aumente. **Exporte o backup JSON antes de trocar versão.**
Para publicar ou atualizar regularmente sem desinstalar, configure futuramente
um APK/AAB **release** com chave de assinatura persistente guardada em um
cofre de segredos do GitHub, **nunca no ZIP/repositório**. A cada nova versão,
aumente `versionCode` e mantenha `applicationId` e assinatura consistentes.

## Limitações e testes

- O presente projeto **foi preparado para compilar no GitHub Actions**. O APK
  não foi compilado, instalado ou testado em um aparelho neste ambiente.
- A execução verde do GitHub Actions confirma compilação, **não** a ausência
  de bugs em execução. Teste em Android real (vertical/horizontal), sobretudo
  toque/joystick, missões, sistema de arquivos e retomada do aplicativo.
- O HTML pode conservar limitações visuais/funcionais do original V8.0.
- `gradlew` não é necessário neste fluxo: a Action `gradle/actions/setup-gradle`
  instala **Gradle 8.13** automaticamente no GitHub antes de rodar `gradle
  :app:assembleDebug`.
- Se um build falhar, copie o trecho vermelho do log em Actions e envie para
  diagnóstico; a correção deverá vir como novo ZIP completo para ZiptoGit.
