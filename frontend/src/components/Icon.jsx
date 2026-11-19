// Ícones em traço fino (24×24, stroke = currentColor).
const tracos = {
  painel: 'M3.5 3.5h7v7h-7zM13.5 3.5h7v7h-7zM3.5 13.5h7v7h-7zM13.5 13.5h7v7h-7z',
  alunos: 'M15.5 20v-1.2a3.8 3.8 0 0 0-3.8-3.8H7.3a3.8 3.8 0 0 0-3.8 3.8V20M9.5 11.5a3.3 3.3 0 1 0 0-6.6 3.3 3.3 0 0 0 0 6.6zM16 5.1a3.3 3.3 0 0 1 0 6.3M20.5 20v-1.2a3.8 3.8 0 0 0-2.7-3.6',
  planos: 'M6 3.5h8.5l4 4v13H6zM14 3.5v4.5h4.5M9 12.5h6.5M9 16.5h4.5',
  financeiro: 'M4 7.5a2 2 0 0 1 2-2h11v3M4 7.5v11a2 2 0 0 0 2 2h14v-12H6a2 2 0 0 1-2-2zM16 14.5h1.5',
  presenca: 'M4 5.5h16v15H4zM4 10h16M8.5 3v4M15.5 3v4M9 15.2l2.2 2.2 4-4',
  graduacao: 'M3 9.5h18v4.5H3zM10.5 9.5v4.5M13.5 9.5v4.5M11 14l-2 6.5M13 14l2 6.5',
  sino: 'M18 16.5H6l1.5-2.2V10a4.5 4.5 0 0 1 9 0v4.3l1.5 2.2zM10 19.5a2 2 0 0 0 4 0',
  perfil: 'M12 11.5a3.8 3.8 0 1 0 0-7.6 3.8 3.8 0 0 0 0 7.6zM4.5 20.5v-1a4.5 4.5 0 0 1 4.5-4.5h6a4.5 4.5 0 0 1 4.5 4.5v1',
  config: 'M4 7h9M17 7h3M4 17h3M11 17h9M15 9.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4zM9 19.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4z',
  sair: 'M10 4H5v16h5M14.5 8l4 4-4 4M18.5 12H9.5',
  camera: 'M3.5 8h4L9 5.5h6L16.5 8h4v11.5h-17zM12 16.5a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4z',
  galeria: 'M4 4.5h16v15H4zM4 16l4.5-4.5L13 16M12 15l3-3 5 5M15.5 9h.01',
  'seta-cima': 'M7 17 17 7M9 7h8v8',
  'seta-baixo': 'M7 7l10 10M17 9v8H9',
  'seta-dir': 'M5 12h14M13 6l6 6-6 6',
  chevron: 'M9.5 6l6 6-6 6',
  fixar: 'M12 16.5V21M8.5 3.5h7M9.5 3.5V10L6 14.5h12L14.5 10V3.5',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  fechar: 'M6 6l12 12M18 6 6 18',
  mais: 'M12 5v14M5 12h14',
  cobrancas: 'M3.5 6h17v12.5h-17zM3.5 10h17M7 15h4',
  link: 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1.2 1.2M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1.2-1.2',
  atualizar: 'M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4',
  busca: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4',
  editar: 'M4 20h4.5L19.5 9 15 4.5 4 15.5zM12.5 7l4.5 4.5',
}


export default function Icon({ nome, className = 'icone' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"
      strokeLinecap="square" strokeLinejoin="miter" aria-hidden="true">
      <path d={tracos[nome]} />
    </svg>
  )
}
