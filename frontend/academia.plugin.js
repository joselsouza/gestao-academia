// Identidade de cada academia (nome, logo, ícones, cores e endereço da API).
//
// Cada academia tem uma pasta em academias/<id>/ com academia.json e as imagens da marca.
// A academia do build é escolhida pela variável ACADEMIA (padrão: exemplo):
//   ACADEMIA=exemplo npm run dev
//   ACADEMIA=exemplo npm run build
//
// O código lê a configuração com: import academia from 'virtual:academia'
import fs from 'node:fs'
import path from 'node:path'

const PASTA = path.resolve(import.meta.dirname, 'academias')
const PADRAO = 'exemplo'
const MODULO = 'virtual:academia'

/** Imagens que toda academia precisa ter na sua pasta (servidas na raiz do site). */
export const IMAGENS = ['logo.png', 'favicon-32.png', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png', 'apple-touch-icon.png']

const CORES_PADRAO = { destaque: '#0b0b0b', destaqueTexto: '#ffffff', fundo: '#f5f5f2' }

/** Largura ÷ altura de um PNG (lida do cabeçalho IHDR). */
function proporcaoPng(arquivo) {
  const b = fs.readFileSync(arquivo)
  return Number((b.readUInt32BE(16) / b.readUInt32BE(20)).toFixed(4))
}

const TIPOS = { '.png': 'image/png', '.woff2': 'font/woff2', '.woff': 'font/woff', '.txt': 'text/plain; charset=utf-8' }

/** Arquivos opcionais da academia que também vão para a raiz do site. */
function extrasDaAcademia(a) {
  return [a.logotipo, a.fonteTitulos?.arquivo].filter(Boolean)
}

export function idAcademia() {
  return (process.env.ACADEMIA || PADRAO).trim()
}

export function carregarAcademia(id = idAcademia()) {
  const dir = path.join(PASTA, id)
  const arquivo = path.join(dir, 'academia.json')
  if (!fs.existsSync(arquivo)) {
    const existentes = fs.readdirSync(PASTA).filter((d) => fs.existsSync(path.join(PASTA, d, 'academia.json')))
    throw new Error(`Academia "${id}" não encontrada (${arquivo}). Disponíveis: ${existentes.join(', ')}`)
  }
  const cfg = JSON.parse(fs.readFileSync(arquivo, 'utf8'))

  const faltando = ['nome', 'marca'].filter((k) => !cfg[k])
  if (!Array.isArray(cfg.marca) || cfg.marca.length !== 2) faltando.push('marca (2 partes, ex.: ["EXEMPLO", "BJJ"])')
  const semImagem = IMAGENS.filter((f) => !fs.existsSync(path.join(dir, f)))
  if (faltando.length || semImagem.length) {
    throw new Error(`academias/${id}: ${[
      faltando.length && `campos faltando em academia.json: ${faltando.join(', ')}`,
      semImagem.length && `imagens faltando: ${semImagem.join(', ')}`,
    ].filter(Boolean).join(' · ')}`)
  }

  // Opcionais: logotipo em PNG transparente e fonte própria para os títulos.
  const extras = []
  if (cfg.logotipo && !fs.existsSync(path.join(dir, cfg.logotipo))) extras.push(`logotipo: ${cfg.logotipo}`)
  const fonte = cfg.fonteTitulos
  if (fonte && (!fonte.nome || !fonte.arquivo)) extras.push('fonteTitulos precisa de "nome" e "arquivo"')
  else if (fonte && !fs.existsSync(path.join(dir, fonte.arquivo))) extras.push(`fonteTitulos: ${fonte.arquivo}`)
  if (extras.length) throw new Error(`academias/${id}: arquivo(s) não encontrado(s) ou inválido(s): ${extras.join(' · ')}`)

  return {
    id,
    dir,
    logotipo: cfg.logotipo || null,
    logotipoProporcao: cfg.logotipo ? proporcaoPng(path.join(dir, cfg.logotipo)) : null,
    fonteTitulos: fonte ? { nome: fonte.nome, arquivo: fonte.arquivo, italico: !!fonte.italico, escala: Number(fonte.escala) || 1 } : null,
    nome: cfg.nome,
    nomeCompleto: cfg.nomeCompleto || cfg.nome,
    marca: cfg.marca,
    subtitulo: cfg.subtitulo || '',
    despedida: cfg.despedida || '',
    apiUrl: cfg.apiUrl || '',
    cloudflareProjeto: cfg.cloudflareProjeto || '',
    cores: { ...CORES_PADRAO, ...(cfg.cores || {}) },
  }
}

/** Manifesto do app. `aluno`: versão do portal do aluno (o ícone abre direto em /aluno). */
function manifesto(a, aluno = false) {
  return JSON.stringify({
    ...(aluno ? { id: '/aluno' } : {}),
    name: aluno ? `${a.nome} · Aluno` : `${a.nome} · Gestão`,
    short_name: a.nome,
    description: aluno ? `Portal do aluno da ${a.nomeCompleto}` : `Sistema de gestão da ${a.nomeCompleto}`,
    lang: 'pt-BR',
    start_url: aluno ? '/aluno' : '/',
    scope: '/',
    display: 'standalone',
    background_color: a.cores.fundo,
    theme_color: a.cores.fundo,
    icons: [
      { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
      { src: '/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  }, null, 2)
}

const escapar = (s) => String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')

export default function academiaPlugin() {
  let a

  return {
    name: 'academia',

    configResolved() {
      a = carregarAcademia()
    },

    resolveId(id) {
      if (id === MODULO) return '\0' + MODULO
    },

    load(id) {
      if (id !== '\0' + MODULO) return
      // Só o que o navegador precisa (sem caminhos locais nem dados de deploy).
      const { nome, nomeCompleto, marca, subtitulo, despedida, apiUrl, logotipoProporcao } = a
      const logotipo = a.logotipo ? `/${a.logotipo}` : null
      return `export default ${JSON.stringify({ id: a.id, nome, nomeCompleto, marca, subtitulo, despedida, apiUrl, logotipo, logotipoProporcao })}`
    },

    transformIndexHtml(html) {
      // :root:root vence o :root do index.css, que no modo dev é injetado depois deste <style>.
      const { destaque, destaqueTexto, fundo } = a.cores
      const cores = `:root:root{--destaque:${destaque};--tinta:${destaque};--destaque-hover:color-mix(in srgb, ${destaque} 82%, #fff);`
        + `--destaque-texto:${destaqueTexto};--papel:${destaqueTexto};--fundo:${fundo}}`
      let fonte = ''
      let preload = ''
      const f = a.fonteTitulos
      if (f) {
        const formato = f.arquivo.endsWith('.woff') ? 'woff' : 'woff2'
        fonte = `@font-face{font-family:${JSON.stringify(f.nome)};src:url("/${f.arquivo}") format("${formato}");font-weight:400;font-style:normal;font-display:swap}`
          + `:root:root{--fonte-display:${JSON.stringify(f.nome)},var(--fonte);--titulo-estilo:${f.italico ? 'italic' : 'normal'};`
          + `--titulo-escala:${f.escala};--titulo-espaco:0;--titulo-peso:400}`
        preload = `    <link rel="preload" href="/${f.arquivo}" as="font" type="font/${formato}" crossorigin />\n`
      }
      if (a.logotipo) preload += `    <link rel="preload" href="/${a.logotipo}" as="image" />\n`
      return html
        .replaceAll('%ACADEMIA_NOME%', escapar(a.nome))
        .replaceAll('%ACADEMIA_COR_FUNDO%', escapar(a.cores.fundo))
        .replace('</head>', `${preload}    <style id="cores-academia">${cores}${fonte}</style>\n  </head>`)
    },

    // npm run dev: serve as imagens e o manifesto da academia escolhida.
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url.split('?')[0].slice(1)
        if (url === 'site.webmanifest' || url === 'site-aluno.webmanifest') {
          res.setHeader('Content-Type', 'application/manifest+json')
          return res.end(manifesto(a, url === 'site-aluno.webmanifest'))
        }
        if (IMAGENS.includes(url) || extrasDaAcademia(a).includes(url)) {
          res.setHeader('Content-Type', TIPOS[path.extname(url)] || 'application/octet-stream')
          return res.end(fs.readFileSync(path.join(a.dir, url)))
        }
        next()
      })
    },

    // npm run build: copia as imagens e o manifesto para a raiz do dist.
    generateBundle() {
      for (const f of [...IMAGENS, ...extrasDaAcademia(a)]) {
        this.emitFile({ type: 'asset', fileName: f, source: fs.readFileSync(path.join(a.dir, f)) })
      }
      this.emitFile({ type: 'asset', fileName: 'site.webmanifest', source: manifesto(a) })
      this.emitFile({ type: 'asset', fileName: 'site-aluno.webmanifest', source: manifesto(a, true) })
    },
  }
}
