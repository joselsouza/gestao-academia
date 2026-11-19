# Academias

Cada academia é uma instalação separada do mesmo código: **site próprio, back-end próprio e banco
de dados próprio**. Nome, logo e cores ficam nesta pasta. Senhas e chaves ficam em variáveis de
ambiente do back-end.

```
academias/
└── exemplo/             ← academia padrão e modelo para copiar (logo provisório)
    ├── academia.json
    ├── logotipo.png, fonte-titulos.woff2 (+ licença)   ← opcionais
    └── logo.png, favicon-32.png, icon-192.png, icon-512.png,
        icon-maskable-512.png, apple-touch-icon.png
```

## academia.json

| Campo | Obrigatório | Para que serve |
|---|---|---|
| `nome` | sim | Nome curto: título da aba, ícone do celular, mensagens do WhatsApp |
| `marca` | sim | Logotipo em texto no menu, em duas partes: `["EXEMPLO", "BJJ"]` (a 2ª fica em cinza) |
| `nomeCompleto` | não | Nome por extenso (texto alternativo do logo, descrição do app) |
| `subtitulo` | não | Linha em itálico abaixo do nome na tela de login |
| `despedida` | não | Fecho das mensagens enviadas ao aluno (ex.: `"Oss!"`). Vazio = sem fecho |
| `apiUrl` | para publicar | Endereço público do back-end desta academia (Render) |
| `cloudflareProjeto` | para publicar | Nome do projeto no Cloudflare Pages |
| `cores` | não | `destaque` (botões, menu ativo), `destaqueTexto` (texto sobre o destaque), `fundo` |
| `logotipo` | não | PNG transparente com o nome desenhado (ex.: `"logotipo.png"`). Substitui o texto da `marca` no menu e no login; a cor segue o tema |
| `fonteTitulos` | não | Fonte própria dos títulos: `{ "nome": "Audiowide", "arquivo": "fonte-titulos.woff2", "italico": true, "escala": 0.62 }`. `escala` reduz/aumenta os títulos quando a fonte é mais larga ou mais estreita que a padrão. Guarde a licença da fonte na mesma pasta |

## Imagens

Todas em PNG, com estes nomes:

| Arquivo | Tamanho | Observação |
|---|---|---|
| `logo.png` | 640×640 | Login e menu |
| `favicon-32.png` | 32×32 | Ícone da aba |
| `icon-192.png`, `icon-512.png` | 192 e 512 | Ícone do app no Android |
| `icon-maskable-512.png` | 512×512 | Android: logo com margem, pois o sistema recorta em círculo |
| `apple-touch-icon.png` | 180×180 | iPhone: fundo sólido (sem transparência) e um pouco de margem |

O build para com uma mensagem clara se faltar algum campo ou imagem.

## Rodar e publicar

```bash
ACADEMIA=exemplo npm run dev       # testar no computador (padrão: exemplo)
ACADEMIA=exemplo npm run build     # gerar o site em dist/
ACADEMIA=exemplo npm run deploy    # build + publicação no Cloudflare Pages
```

O `deploy` usa o `apiUrl` do academia.json, confere se essa URL foi embutida no site e só então
publica. Assim o site nunca sai apontando para `localhost`.

## Nova academia, passo a passo

1. **Marca:** copie `academias/exemplo/` para `academias/<id>/`, troque as imagens e preencha
   o `academia.json`.
2. **Banco:** crie um projeto novo no Supabase. As tabelas são criadas sozinhas na primeira
   subida do back-end (Flyway).
3. **Back-end:** crie um serviço novo no Render a partir deste repositório, com as variáveis
   (modelo em `backend/.env.example`):
   - `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`: banco desta academia
   - `APP_JWT_SECRET`: um segredo novo (`openssl rand -base64 48`), nunca o de outra academia
   - `APP_ACADEMIA_NOME`: nome usado nas cobranças do Asaas
   - `APP_ADMIN_NOME`, `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`: primeiro administrador
   - `APP_ALUNO_SENHA_PADRAO` (opcional): senha inicial do portal do aluno (padrão `123456`)
   - `APP_CORS_ORIGINS`: endereço do site desta academia (ex.: `https://<projeto>.pages.dev`)
   - `ASAAS_API_KEY`, `ASAAS_BASE_URL`, `ASAAS_WEBHOOK_TOKEN`: conta Asaas **da academia**
4. **Site:** crie o projeto no Cloudflare Pages (`npx wrangler pages project create <projeto>`),
   coloque o nome em `cloudflareProjeto`, a URL do Render em `apiUrl` e rode o `deploy`.
5. **Asaas:** cadastre o webhook apontando para `<apiUrl>/api/asaas/webhook`.
