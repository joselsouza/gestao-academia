import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, qs } from '../../lib/api.js'
import { telefoneMask, whatsappLink } from '../../lib/format.js'
import { Cabecalho, Erro, Faixa, Pessoa, useCarregar } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'
import BarraGraduacao from '../../components/BarraGraduacao.jsx'

export default function AlunosLista() {
  const navigate = useNavigate()
  const [busca, setBusca] = useState('')
  const [situacao, setSituacao] = useState('true')
  const { dados, erro, carregando } = useCarregar(
    async () => {
      const [alunos, graduacao] = await Promise.all([
        api.get(`/api/alunos${qs({ ativo: situacao })}`),
        api.get('/api/graduacao/status?somenteAptos=false').catch(() => []),
      ])
      return { alunos, graduacao: new Map(graduacao.map((g) => [g.alunoId, g])) }
    },
    [situacao],
    `alunos:${situacao}`,
  )
  const termo = busca.trim().toLowerCase()
  const lista = (dados?.alunos || []).filter((a) => !termo || a.nome.toLowerCase().includes(termo) || (a.telefone || '').includes(termo))

  return (
    <>
      <Cabecalho titulo="Alunos">
        <Link className="btn btn-primario" to="/alunos/novo"><Icon nome="mais" /> Novo aluno</Link>
      </Cabecalho>
      <div className="filtros">
        <input className="input" placeholder="Buscar por nome ou telefone" value={busca} onChange={(e) => setBusca(e.target.value)} />
        <select className="input" style={{ flex: '0 1 180px' }} value={situacao} onChange={(e) => setSituacao(e.target.value)}>
          <option value="true">Ativos</option>
          <option value="false">Inativos</option>
          <option value="">Todos</option>
        </select>
      </div>
      <Erro erro={erro} />
      <div className="card" style={{ padding: 0 }}>
        {carregando && !dados ? <div className="carregando">Carregando…</div> : lista.length === 0 ? (
          <div className="vazio">Nenhum aluno encontrado.</div>
        ) : (
          <table className="tabela">
            <thead>
              <tr><th>Aluno</th><th>Faixa</th><th>Graduação</th><th>Plano</th><th>Telefone</th><th>Rosto</th><th /></tr>
            </thead>
            <tbody>
              {lista.map((a) => (
                <tr key={a.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/alunos/${a.id}`)}>
                  <td className="sem-rotulo"><Pessoa nome={a.nome} foto={a.foto} sub={a.ativo ? null : 'Inativo'} /></td>
                  <td data-label="Faixa"><Faixa faixa={a.faixa} graus={a.graus} /></td>
                  <td data-label="Graduação">{dados.graduacao.get(a.id) ? <BarraGraduacao compacta status={dados.graduacao.get(a.id)} /> : <span className="muted">—</span>}</td>
                  <td data-label="Plano">
                    {a.planoNome || <span className="muted">—</span>}
                  </td>
                  <td data-label="Telefone">
                    {a.telefone ? (
                      <a href={whatsappLink(a.telefone)} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}
                        className="linha" style={{ textDecoration: 'none' }}>
                        {telefoneMask(a.telefone)}
                      </a>
                    ) : '—'}
                  </td>
                  <td data-label="Rosto">
                    {a.reconhecimentoFacialAtivo ? <span className="selo ok">Cadastrado</span> : <span className="selo neutro">Não</span>}
                  </td>
                  <td className="acoes-linha"><span className="btn btn-texto btn-pequeno">Editar <Icon nome="seta-dir" /></span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <p className="muted" style={{ fontFamily: 'var(--fonte-mono)', fontSize: 12, letterSpacing: '.08em', textTransform: 'uppercase' }}>{lista.length} aluno(s)</p>
    </>
  )
}
