// Publica o site de uma academia no Cloudflare Pages.
//   npm run deploy                      → academia padrão (exemplo)
//   ACADEMIA=<id> npm run deploy        → outra academia
// O projeto do Cloudflare e a URL da API vêm de academias/<id>/academia.json.
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { carregarAcademia } from '../academia.plugin.js'

const raiz = path.resolve(import.meta.dirname, '..')
const a = carregarAcademia()
const api = process.env.VITE_API_URL || a.apiUrl

if (!a.cloudflareProjeto) throw new Error(`academias/${a.id}/academia.json: informe "cloudflareProjeto".`)
if (!api || api.includes('localhost')) throw new Error(`academias/${a.id}/academia.json: informe "apiUrl" (endereço público do back-end).`)

// VITE_API_URL vai explícita: variável de ambiente vence o .env, que costuma apontar para localhost.
const rodar = (cmd) => execSync(cmd, { cwd: raiz, stdio: 'inherit', env: { ...process.env, ACADEMIA: a.id, VITE_API_URL: api } })

console.log(`\n▸ ${a.nome}: build com a API ${api}\n`)
rodar('npx vite build')

// Confere se a URL da API foi embutida no JS (sem ela o site tentaria falar com localhost).
const js = fs.readdirSync(path.join(raiz, 'dist/assets')).filter((f) => f.endsWith('.js'))
  .map((f) => fs.readFileSync(path.join(raiz, 'dist/assets', f), 'utf8')).join('\n')
if (!js.includes(api.replace(/\/$/, ''))) throw new Error(`O build não contém a URL da API (${api}). Deploy cancelado.`)

console.log(`\n▸ Publicando no projeto Cloudflare Pages "${a.cloudflareProjeto}"\n`)
rodar(`npx wrangler pages deploy dist --project-name ${a.cloudflareProjeto} --branch main`)
