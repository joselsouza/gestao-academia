import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../lib/api.js'
import { brl, cpfMask, data, hojeISO } from '../../lib/format.js'
import { Cabecalho, Erro, Spinner, useCarregar } from '../../components/ui.jsx'
import { AvisoConfig, BotaoCopiar, BotaoWhatsapp, FORMAS, tipoLabel } from './asaas.jsx'

const VAZIO = {
  alunoId: '', tipo: 'UNICA', forma: 'INDEFINIDA', valor: '', vencimento: hojeISO(), descricao: '', cpf: '', email: '',
}

export default function NovaCobranca() {
  const [form, setForm] = useState(VAZIO)
  const [erro, setErro] = useState(null)
  const [salvando, setSalvando] = useState(false)
  const [criada, setCriada] = useState(null)

  const { dados, erro: erroCarga } = useCarregar(async () => {
    const [alunos, config] = await Promise.all([
      api.get('/api/alunos?ativo=true'), api.get('/api/cobrancas/config'),
    ])
    return { alunos, config }
  }, [], 'nova-cobranca')

  const aluno = useMemo(() => dados?.alunos.find((a) => String(a.id) === String(form.alunoId)), [dados, form.alunoId])
  const set = (campo) => (e) => setForm((f) => ({ ...f, [campo]: e.target.value }))

  function escolherAluno(id) {
    const a = dados?.alunos.find((x) => String(x.id) === String(id))
    setForm((f) => ({
      ...f,
      alunoId: id,
      cpf: a?.cpf ? cpfMask(a.cpf) : '',
      email: a?.email || '',
    }))
  }

  async function salvar(e) {
    e.preventDefault()
    setErro(null); setSalvando(true)
    try {
      const c = await api.post('/api/cobrancas', {
        alunoId: Number(form.alunoId),
        tipo: form.tipo,
        forma: form.forma,
        ciclo: null,
        valor: Number(String(form.valor).replace(/\./g, '').replace(',', '.')),
        vencimento: form.vencimento,
        dataFim: null,
        descricao: form.descricao || null,
        // Com responsável, o Asaas usa os dados dele: não mexe no CPF/e-mail do aluno.
        cpf: aluno?.responsavel ? null : form.cpf || null,
        email: aluno?.responsavel ? null : form.email || null,
      })
      setCriada(c)
    } catch (err) {
      setErro(err.message)
    } finally {
      setSalvando(false)
    }
  }

  if (criada) {
    return (
      <>
        <Cabecalho titulo="Cobrança criada" />
        <div className="card" style={{ display: 'grid', gap: 14, maxWidth: 640 }}>
          <div>
            <strong>{criada.alunoNome}</strong><br />
            <span className="muted">{tipoLabel(criada)} · {brl(criada.valor)} · vence {data(criada.vencimento)}</span>
          </div>
          {criada.link ? (
            <>
              <div className="campo">
                <label htmlFor="lk">Link de pagamento</label>
                <input id="lk" readOnly value={criada.link} onFocus={(e) => e.target.select()} />
              </div>
              <div className="linha">
                <BotaoCopiar link={criada.link} pequeno={false} />
                <BotaoWhatsapp c={criada} pequeno={false} />
              </div>
            </>
          ) : (
            <div className="alerta-box info">A cobrança foi criada no Asaas. O link aparece na lista em instantes (clique em "Atualizar situação").</div>
          )}
          <div className="linha" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-secundario" onClick={() => { setCriada(null); setForm(VAZIO) }}>Nova cobrança</button>
            <Link className="btn btn-primario" to="/financeiro/cobrancas">Ver cobranças</Link>
          </div>
        </div>
      </>
    )
  }

  const responsavel = aluno?.responsavel
  const precisaCpf = aluno && !responsavel && !aluno.cpf

  return (
    <>
      <Cabecalho titulo="Nova cobrança avulsa" sub={<>Para cobranças fora da mensalidade (exame de faixa, kimono, evento). Gera um link de pagamento no Asaas para enviar ao aluno.</>} />
      <AvisoConfig config={dados?.config} />
      <Erro erro={erroCarga} />

      <form onSubmit={salvar} className="card" style={{ display: 'grid', gap: 16, maxWidth: 760 }}>
        <Erro erro={erro} />

        <div className="form-grade">
          <div className="campo largo">
            <label htmlFor="al">Aluno *</label>
            <select id="al" required value={form.alunoId} onChange={(e) => escolherAluno(e.target.value)}>
              <option value="">Selecione…</option>
              {dados?.alunos.map((a) => <option key={a.id} value={a.id}>{a.nome}{a.bolsista ? ' (bolsista)' : ''}</option>)}
            </select>
          </div>

          {responsavel ? (
            <div className="largo alerta-box info">
              Cobrança no nome do responsável: <strong>{responsavel.nome}</strong> · CPF {cpfMask(responsavel.cpf)}.
              Para mudar, edite o responsável no cadastro do aluno.
            </div>
          ) : (<>
          <div className="campo">
            <label htmlFor="cpf">CPF do aluno {precisaCpf ? '*' : ''}</label>
            <input id="cpf" inputMode="numeric" placeholder="000.000.000-00" required={Boolean(precisaCpf)}
              value={form.cpf} onChange={(e) => setForm((f) => ({ ...f, cpf: cpfMask(e.target.value) }))} />
            <span className="ajuda">Exigido pelo Asaas. Fica salvo no cadastro do aluno.</span>
          </div>
          <div className="campo">
            <label htmlFor="em">E-mail do aluno</label>
            <input id="em" type="email" maxLength={160} value={form.email} onChange={set('email')} />
            <span className="ajuda">Opcional.</span>
          </div>
          </>)}

          <div className="campo">
            <label htmlFor="vl">Valor (R$) *</label>
            <input id="vl" inputMode="decimal" required placeholder="150,00" value={form.valor} onChange={set('valor')} />
          </div>
          <div className="campo">
            <label htmlFor="vc">Vencimento *</label>
            <input id="vc" type="date" required min={hojeISO()} value={form.vencimento} onChange={set('vencimento')} />
          </div>


          <div className="campo largo">
            <label htmlFor="fp">Forma de pagamento</label>
            <select id="fp" value={form.forma} onChange={set('forma')}>
              {Object.entries(FORMAS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </div>
          <div className="campo largo">
            <label htmlFor="ds">Descrição *</label>
            <input id="ds" required maxLength={255} placeholder="Ex.: Exame de faixa" value={form.descricao} onChange={set('descricao')} />
            <span className="ajuda">Aparece para o aluno na página de pagamento.</span>
          </div>
        </div>

        <div className="linha" style={{ justifyContent: 'flex-end' }}>
          <Link className="btn btn-secundario" to="/financeiro/cobrancas">Cancelar</Link>
          <button className="btn btn-primario" disabled={salvando || !dados?.config?.configurado}>
            {salvando ? <Spinner /> : 'Emitir cobrança'}
          </button>
        </div>
      </form>
    </>
  )
}
