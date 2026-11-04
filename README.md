# Explorador ZIP 2.0 — pacote para GitHub Actions / ZiptoGit

Projeto Android nativo em Java. **Arquivos na raiz do ZIP**, inclusive `.github/workflows/main.yml`; sem pasta principal externa.

## Incluído nesta versão experimental
- Navegação em diretórios autorizados pelo seletor do Android; restauração da última pasta quando a autorização persistir.
- Exibir/ocultar arquivos iniciados por ponto, inclusive `.github`.
- Abrir e navegar arquivos ZIP sem extrair; extrair ZIP inteiro em subpasta com limites de segurança.
- Seleção múltipla (toque prolongado), selecionar todos, inverter, desmarcar e selecionar por extensão.
- Criar pastas e arquivos vazios, renomear, excluir com confirmação e mostrar detalhes (quando fornecidos pelo provedor).
- Copiar pelo DocumentsContract.copyDocument e mover pelo DocumentsContract.moveDocument, ambos **dependentes do provedor Android**. Não se faz cópia+exclusão como alternativa ao MOVE.
- Busca por nome na pasta atual; classificação por nome, tipo, tamanho e data.
- Criar ZIP com arquivos **e pastas recursivamente**, incluindo entradas ocultas acessíveis. Opções ZIP sem pasta principal (útil para ZiptoGit) ou com pasta `Projeto/`.
- Favoritos básicos de árvores autorizadas e histórico da sessão (histórico informativo).
- Compartilhar arquivos pelo seletor Android; visualizar imagens comuns; abrir/editar arquivos de texto pequenos (limite de 1 MB), confirmar gravação ou salvar cópia.
- Ícone próprio; offline; identificador do app preservado e versão 2.0.0 / versionCode 3.

## Recursos do planejamento que AINDA NÃO estão completos
- **Painel duplo real** de navegação, recortar/colar interno, navegação pelo histórico, busca recursiva e histórico persistente de operações.
- **Progressos/cancelamento em tempo real** de todas as operações, verificação automática da integridade depois do ZIP, prévia detalhada da árvore antes de compactar, extração seletiva de entradas dentro do ZIP.
- Visualização integrada de PDF/vídeo, miniaturas em grade, editor avançado com destaque de sintaxe e comparador de arquivos.
- Lixeira/recuperação; regras de cópia/movimentação em múltiplos provedores e operações de pastas em provedores que não as suportam.

## Uso rápido
1. Toque em **Pasta** e conceda acesso a uma pasta. Pressione e segure itens para marcá-los; toque normalmente para abrir pasta/ZIP.
2. **☰ Arquivos**: criar, renomear, copiar, mover, excluir, ver detalhes, compartilhar e editar texto.
3. **🗜 ZIP+**: selecione o modo sem pasta principal (ZiptoGit) ou com pasta `Projeto/`, selecione pasta de destino. A própria compactação percorre as subpastas autorizadas.
4. **🔎 Buscar** filtra o diretório atual; deixe a busca vazia para listar tudo. **↕ Ordem** e **☑ Seleção** controlam a lista.
5. **★ Locais** guarda favoritos de pastas-raiz autorizadas. A última pasta é restaurada quando a permissão persistente ainda estiver válida.

## Limites e segurança
- Use **arquivos de teste**, principalmente para exclusão, edição e movimentação. A exclusão pode ser definitiva.
- O Storage Access Framework restringe partes do armazenamento. Copiar/mover/renomear pastas pode ser recusado pelo provedor; datas também dependem dele.
- Não há garantia de que providers preservem datas ao mover ou copiar. Nenhuma permissão irrestrita de armazenamento é solicitada.
- ZIP: limite de aproximadamente 1 GB na compactação; limites de profundidade e quantidade de itens. Extração também usa limites de tamanho e protege contra `../`.
- Nova compilação GitHub Actions pode gerar assinatura de debug diferente; talvez seja necessário desinstalar a versão antiga. Não remova dados de que precise.
- **Não compilado nem testado em aparelho nesta sessão**. Se o GitHub Actions falhar, consulte a etapa vermelha para a mensagem exata.

## Compilar
Envie o ZIP sem extrair ao ZiptoGit. Confirme que `.github/workflows/main.yml` está no repositório. Na aba **Actions**, abra a execução; depois **Artifacts → ExploradorZIP-APK**, baixe o ZIP do artefato e extraia `app-debug.apk`. Se `.github` não for importado, crie manualmente `.github/workflows/main.yml` com o conteúdo de `YAML_PARA_COPIAR.yml`.
