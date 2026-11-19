import academia from 'virtual:academia'

/** Estrutura do menu lateral. `base: true` vai para o rodapé do menu (como Configurações). */
export const MENU = [
  { titulo: 'Painel', icone: 'painel', to: '/' },
  {
    titulo: 'Alunos', icone: 'alunos', itens: [
      { titulo: 'Lista de alunos', to: '/alunos' },
      { titulo: 'Novo aluno', to: '/alunos/novo' },
    ],
  },
  {
    titulo: 'Presença', icone: 'presenca', itens: [
      { titulo: 'Reconhecimento facial', to: '/presenca/reconhecimento' },
      { titulo: 'Chamada manual', to: '/presenca/chamada' },
      { titulo: 'Histórico', to: '/presenca/historico' },
    ],
  },
  {
    titulo: 'Graduação', icone: 'graduacao', itens: [
      { titulo: 'Aptos a graduar', to: '/graduacao/aptos' },
      { titulo: 'Frequência mínima', to: '/graduacao/regras' },
      { titulo: 'Histórico', to: '/graduacao/historico' },
    ],
  },
  {
    titulo: 'Financeiro', icone: 'financeiro', itens: [
      { titulo: 'Mensalidades', to: '/financeiro/mensalidades' },
      { titulo: 'Inadimplentes', to: '/financeiro/inadimplentes' },
      { titulo: 'Cobranças avulsas', to: '/financeiro/cobrancas' },
    ],
  },
  {
    titulo: 'Planos', icone: 'planos', itens: [
      { titulo: 'Planos de mensalidade', to: '/planos' },
    ],
  },
  {
    titulo: 'Configurações', icone: 'config', somenteAdmin: true, base: true, itens: [
      { titulo: 'Usuários de acesso', to: '/usuarios' },
    ],
  },
]

/** Portal do aluno: só o financeiro dele e o próprio perfil. */
export const MENU_ALUNO = [
  { titulo: 'Financeiro', icone: 'financeiro', to: '/financeiro' },
  { titulo: 'Editar perfil', icone: 'perfil', to: '/perfil' },
]

const casa = (pathname, to) => pathname === to || pathname.startsWith(`${to}/`)

/** Título do grupo do menu que contém a rota atual. */
export function grupoAtivo(pathname) {
  return MENU.find((g) => g.itens?.some((i) => casa(pathname, i.to)))?.titulo
}

/** Rótulo pequeno ("eyebrow") exibido acima do título de cada página. */
export function secaoDaRota(pathname) {
  if (pathname === '/') return 'Visão geral'
  return grupoAtivo(pathname) || academia.nome
}
