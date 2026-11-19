import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../../lib/api.js'
import { carregarModelos, descritorUnico, lerImagem } from '../../lib/face.js'
import { FAIXAS, MAIORIDADE, cpfMask, data, grausMaximos, hojeISO, idade, nomeFaixa, telefoneMask } from '../../lib/format.js'
import { Avatar, Cabecalho, Erro, Faixa, Spinner } from '../../components/ui.jsx'
import Icon from '../../components/Icon.jsx'
import BarraGraduacao from '../../components/BarraGraduacao.jsx'
import { CampoResponsavel, ModalResponsavel, responsavelDoServidor, responsavelParaServidor } from './Responsavel.jsx'

/** Acesso do aluno ao portal (login com CPF): situação e redefinição para a senha padrão. */
function AcessoPortal({ alunoId, temCpf }) {
  const [acesso, setAcesso] = useState(null)
  const [erro, setErro] = useState(null)
  const [msg, setMsg] = useState(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    api.get(`/api/alunos/${alunoId}/acesso`).then(setAcesso).catch((e) => setErro(e.message))
  }, [alunoId])

  async function redefinir() {
    if (!window.confirm('Voltar a senha do portal deste aluno para a senha padrão?')) return
    setOcupado(true); setErro(null); setMsg(null)
    try {
      setAcesso(await api.post(`/api/alunos/${alunoId}/acesso/redefinir`))
      setMsg('Senha redefinida. O aluno entra com o CPF e a senha padrão.')
    } catch (e) { setErro(e.message) } finally { setOcupado(false) }
  }

  return (
    <div className="card">
      <h2>Acesso ao portal do aluno</h2>
      <Erro erro={erro} />
      {msg && <div className="alerta-box ok">{msg}</div>}
      {!temCpf && <div className="alerta-box aviso">Sem CPF cadastrado: o aluno não consegue entrar no portal.</div>}
      {acesso && (
        <div className="linha" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <span>
            {acesso.senhaPadrao ? <span className="selo alerta">Senha padrão</span> : <span className="selo ok">Senha própria</span>}
            <small className="muted" style={{ marginLeft: 10 }}>
              {acesso.ultimoLogin ? `Último acesso em ${data(acesso.ultimoLogin)}` : 'Nunca acessou'}
            </small>
          </span>
          {!acesso.senhaPadrao && (
            <button type="button" className="btn btn-secundario btn-pequeno" onClick={redefinir} disabled={ocupado}>
              {ocupado ? <Spinner /> : 'Redefinir para a senha padrão'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

const VAZIO = {
  nome: '', telefone: '', cpf: '', email: '', dataNascimento: '', sexo: '', foto: null, faixa: 'BRANCA', graus: 0,
  dataUltimaGraduacao: '', planoId: '', diaVencimento: 10, dataInicio: '', ativo: true, consentimentoBiometria: false,
  responsavel: null,
}

export default function AlunoForm() {
  const { id } = useParams()
  const editando = Boolean(id)
  const navigate = useNavigate()
  const inputCamera = useRef(null)
  const inputGaleria = useRef(null)

  const [form, setForm] = useState(VAZIO)
  const [descritor, setDescritor] = useState(null) // novo vetor facial, se a foto mudou
  const [temRosto, setTemRosto] = useState(false)  // já existe vetor salvo no servidor
  const [planos, setPlanos] = useState([])
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [processandoFoto, setProcessandoFoto] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [carregando, setCarregando] = useState(editando)
  const [graduacao, setGraduacao] = useState(null)
  const [informarResponsavel, setInformarResponsavel] = useState(false)
  const [editandoResponsavel, setEditandoResponsavel] = useState(null) // dados abertos no modal

  useEffect(() => {
    api.get('/api/planos').then(setPlanos).catch((e) => setErro(e.message))
  }, [])

  /** Plano ativo vinculado à faixa (configurado em Planos). */
  const planoDaFaixa = (faixa, lista = planos) => lista.find((p) => p.ativo && p.faixas?.includes(faixa))

  // Aluno novo: assim que os planos chegam, já escolhe o plano da faixa inicial.
  useEffect(() => {
    if (editando || !planos.length) return
    setForm((f) => {
      if (f.planoId) return f
      const auto = planoDaFaixa(f.faixa)
      return auto ? { ...f, planoId: auto.id } : f
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planos, editando])

  /**
   * Ao trocar a faixa, troca também o plano para o plano da nova faixa — se o aluno estiver sem
   * plano ou num plano "de faixa". Planos sem faixa vinculada (ex.: bolsista) são mantidos.
   */
  function trocarFaixa(faixa) {
    setForm((f) => {
      const atual = planos.find((p) => String(p.id) === String(f.planoId))
      const auto = planoDaFaixa(faixa)
      const podeTrocar = !atual || (atual.faixas?.length ?? 0) > 0
      return {
        ...f,
        faixa,
        graus: Math.min(f.graus, grausMaximos(faixa)),
        planoId: podeTrocar && auto ? auto.id : f.planoId,
      }
    })
  }

  useEffect(() => {
    if (!editando) { setForm(VAZIO); setDescritor(null); setTemRosto(false); setInformarResponsavel(false); return }
    setCarregando(true)
    api.get(`/api/alunos/${id}`)
      .then((a) => {
        setForm({
          nome: a.nome, telefone: a.telefone || '', cpf: a.cpf ? cpfMask(a.cpf) : '', email: a.email || '',
          dataNascimento: a.dataNascimento || '', sexo: a.sexo || '', foto: a.foto, faixa: a.faixa, graus: a.graus,
          dataUltimaGraduacao: a.dataUltimaGraduacao || '',
          planoId: a.planoId ?? '', diaVencimento: a.diaVencimento, dataInicio: a.dataInicio || '',
          ativo: a.ativo, consentimentoBiometria: a.consentimentoBiometria,
          responsavel: responsavelDoServidor(a.responsavel),
        })
        setInformarResponsavel(Boolean(a.responsavel))
        setTemRosto(a.reconhecimentoFacialAtivo)
        api.get(`/api/graduacao/status/aluno/${id}`).then(setGraduacao).catch(() => setGraduacao(null))
      })
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false))
  }, [id, editando])

  const set = (campo) => (e) => {
    const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setForm((f) => ({ ...f, [campo]: v }))
  }

  /** Ao informar uma data de nascimento de menor de idade, já abre a opção de responsável. */
  function mudarNascimento(e) {
    const v = e.target.value
    setForm((f) => ({ ...f, dataNascimento: v }))
    const anos = idade(v)
    if (anos !== null && anos < MAIORIDADE) setInformarResponsavel(true)
  }

  async function escolherFoto(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setErro(null); setAviso(null); setProcessandoFoto(true)
    try {
      // Foto para exibição/cadastro: 320px. Vetor facial: calculado numa versão maior (mais precisa),
      // em segundo plano, sem travar a tela.
      const pequena = await lerImagem(file, 320, 0.8)
      setForm((f) => ({ ...f, foto: pequena.dataUrl }))
      const vetor = await descritorUnico(file)
      setDescritor(vetor)
      if (!vetor) setAviso('Nenhum rosto encontrado nesta foto. Ela será salva, mas o reconhecimento facial não funcionará para este aluno. Use uma foto de frente, bem iluminada.')
    } catch (err) {
      setErro(err.message)
    } finally {
      setProcessandoFoto(false)
    }
  }

  async function salvar(e) {
    e.preventDefault()
    setErro(null)
    if (informarResponsavel && !form.responsavel) {
      setErro('Cadastre o responsável — ou desmarque "Informar responsável".')
      document.getElementById('responsavel')?.focus()
      return
    }
    setSalvando(true)
    const plano = planos.find((p) => String(p.id) === String(form.planoId))
    const body = {
      ...form,
      telefone: form.telefone.replace(/\D/g, '') || null,
      cpf: form.cpf.replace(/\D/g, '') || null,
      email: form.email.trim() || null,
      planoId: plano ? plano.id : null,
      graus: Number(form.graus),
      diaVencimento: Number(form.diaVencimento),
      dataInicio: form.dataInicio || null,
      dataNascimento: form.dataNascimento || null,
      sexo: form.sexo || null,
      dataUltimaGraduacao: form.dataUltimaGraduacao || null,
      responsavel: informarResponsavel && form.responsavel ? responsavelParaServidor(form.responsavel) : null,
      faceDescriptor: descritor, // null = mantém o que já existe no servidor
    }
    try {
      await (editando ? api.put(`/api/alunos/${id}`, body) : api.post('/api/alunos', body))
      navigate('/alunos')
    } catch (err) {
      setErro(err.message)
      setSalvando(false)
    }
  }

  async function desativar() {
    if (!window.confirm(`Desativar ${form.nome}? O histórico será mantido.`)) return
    try { await api.del(`/api/alunos/${id}`); navigate('/alunos') } catch (err) { setErro(err.message) }
  }

  if (carregando) return <div className="carregando">Carregando…</div>

  const planoSel = planos.find((p) => String(p.id) === String(form.planoId))
  const rostoOk = Boolean(descritor) || (temRosto && Boolean(form.foto))
  const anos = idade(form.dataNascimento)
  const menor = anos !== null && anos < MAIORIDADE
  const mostrarResponsavel = menor || informarResponsavel || Boolean(form.responsavel)

  return (
    <>
      <Cabecalho titulo={editando ? 'Editar aluno' : 'Novo aluno'}>
        {editando && form.ativo && <button className="btn btn-secundario" onClick={desativar}>Desativar</button>}
      </Cabecalho>
      <Erro erro={erro} />
      {aviso && <div className="alerta-box aviso">{aviso}</div>}

      <form onSubmit={salvar}>
        <div className="card">
          <div className="linha" style={{ gap: 20, alignItems: 'center' }}>
            <Avatar nome={form.nome || '?'} foto={form.foto} grande />
            <div style={{ display: 'grid', gap: 8 }}>
              <div className="linha">
                {processandoFoto ? (
                  <span className="btn btn-escuro" aria-live="polite"><Spinner /> Analisando rosto…</span>
                ) : (
                  <>
                    <button type="button" className="btn btn-escuro" onClick={() => { carregarModelos().catch(() => {}); inputCamera.current.click() }}>
                      <Icon nome="camera" /> {form.foto ? 'Nova foto' : 'Tirar foto'}
                    </button>
                    <button type="button" className="btn btn-secundario" onClick={() => { carregarModelos().catch(() => {}); inputGaleria.current.click() }}>
                      <Icon nome="galeria" /> Galeria
                    </button>
                  </>
                )}
                {form.foto && (
                  <button type="button" className="btn btn-texto" onClick={() => { setForm((f) => ({ ...f, foto: null })); setDescritor(null); setTemRosto(false) }}>
                    Remover
                  </button>
                )}
              </div>
              {/* Câmera (frontal) ou galeria */}
              <input ref={inputCamera} type="file" accept="image/*" capture="user" hidden onChange={escolherFoto} />
              <input ref={inputGaleria} type="file" accept="image/*" hidden onChange={escolherFoto} />
              <small className="muted">
                {descritor ? '✔ Rosto identificado na foto.' : rostoOk ? '✔ Rosto já cadastrado para reconhecimento.' : 'Foto de frente, rosto bem visível, sem boné ou óculos escuros.'}
              </small>
            </div>
          </div>
        </div>

        <div className="card">
          <h2>Dados</h2>
          <div className="form-grade">
            <div className="campo largo">
              <label htmlFor="nome">Nome completo *</label>
              <input id="nome" required maxLength={120} value={form.nome} onChange={set('nome')} />
            </div>
            <div className="campo">
              <label htmlFor="nascimento">Data de nascimento *</label>
              <input id="nascimento" type="date" required max={hojeISO()} value={form.dataNascimento} onChange={mudarNascimento} />
              {anos !== null && (
                <span className="ajuda">{anos} {anos === 1 ? 'ano' : 'anos'}{menor ? ' · menor de idade' : ''}</span>
              )}
            </div>
            <div className="campo">
              <label htmlFor="sexo">Sexo *</label>
              <select id="sexo" required value={form.sexo} onChange={set('sexo')}>
                <option value="">Selecione…</option>
                <option value="MASCULINO">Masculino</option>
                <option value="FEMININO">Feminino</option>
              </select>
            </div>
            <div className="campo">
              <label htmlFor="tel">Telefone / WhatsApp</label>
              <input id="tel" inputMode="tel" placeholder="(24) 99999-9999" value={telefoneMask(form.telefone)}
                onChange={(e) => setForm((f) => ({ ...f, telefone: e.target.value }))} />
            </div>
            <div className="campo">
              <label htmlFor="cpf">CPF</label>
              <input id="cpf" inputMode="numeric" placeholder="000.000.000-00" value={form.cpf}
                onChange={(e) => setForm((f) => ({ ...f, cpf: cpfMask(e.target.value) }))} />
              <span className="ajuda">
                {informarResponsavel ? 'Opcional: as cobranças saem no nome e CPF do responsável.' : 'Necessário para emitir cobranças pelo Asaas.'}
              </span>
            </div>
            <div className="campo">
              <label htmlFor="email">E-mail</label>
              <input id="email" type="email" maxLength={160} value={form.email} onChange={set('email')} />
            </div>
            <div className="campo">
              <label htmlFor="inicio">Data de início</label>
              <input id="inicio" type="date" value={form.dataInicio} onChange={set('dataInicio')} />
              <span className="ajuda">Em branco = hoje.</span>
            </div>
            <div className="campo">
              <label htmlFor="faixa">Faixa</label>
              <select id="faixa" value={form.faixa}
                onChange={(e) => trocarFaixa(e.target.value)}>
                {FAIXAS.map((x) => <option key={x} value={x}>{nomeFaixa(x)}</option>)}
              </select>
            </div>
            <div className="campo">
              <label htmlFor="graus">Graus</label>
              <select id="graus" value={form.graus} onChange={set('graus')}>
                {Array.from({ length: grausMaximos(form.faixa) + 1 }, (_, i) => <option key={i} value={i}>{i}</option>)}
              </select>
            </div>
            <div className="campo" style={{ justifyContent: 'center' }}>
              <Faixa faixa={form.faixa} graus={Number(form.graus)} />
            </div>
            <div className="campo">
              <label htmlFor="graduacao">Data da graduação</label>
              <input id="graduacao" type="date" max={hojeISO()} value={form.dataUltimaGraduacao} onChange={set('dataUltimaGraduacao')} />
              <span className="ajuda">Quando recebeu a faixa/grau atual. Em branco = conta a frequência desde a data de início.</span>
            </div>
          </div>
        </div>

        {mostrarResponsavel && (
          <div className="card">
            <h2>Responsável</h2>
            {menor && !informarResponsavel && (
              <div className="alerta-box aviso">Aluno menor de idade: recomendamos informar o responsável.</div>
            )}
            <label className="checkbox">
              <input type="checkbox" checked={informarResponsavel} onChange={(e) => setInformarResponsavel(e.target.checked)} />
              <span>Informar responsável</span>
            </label>
            <p className="ajuda muted" style={{ margin: '8px 0 0 30px' }}>
              Com responsável, as cobranças (Asaas) e os links de pagamento no WhatsApp vão no nome, CPF e celular dele.
            </p>
            {informarResponsavel && (
              <div className="campo" style={{ marginTop: 16 }}>
                <label htmlFor="responsavel">Responsável *</label>
                <CampoResponsavel
                  valor={form.responsavel}
                  onChange={(r) => setForm((f) => ({ ...f, responsavel: r }))}
                  onEditar={setEditandoResponsavel}
                />
              </div>
            )}
          </div>
        )}

        {editando && graduacao && (
          <div className="card">
            <h2>Progresso para a próxima graduação</h2>
            <BarraGraduacao status={graduacao} />
          </div>
        )}

        <div className="card">
          <h2>Plano e mensalidade</h2>
          <div className="form-grade">
            <div className="campo">
              <label htmlFor="plano">Plano</label>
              <select id="plano" value={form.planoId} onChange={set('planoId')}>
                <option value="">— Sem plano —</option>
                {planos.filter((p) => p.ativo || String(p.id) === String(form.planoId)).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nome} {p.tipo === 'BOLSISTA' ? '(isento)' : `· R$ ${Number(p.valor).toFixed(2).replace('.', ',')}`}
                  </option>
                ))}
              </select>
              {planoSel?.faixas?.includes(form.faixa) && <span className="ajuda">✔ Plano da faixa {nomeFaixa(form.faixa)} (escolhido automaticamente).</span>}
              {planoSel?.tipo === 'BOLSISTA' && <span className="ajuda">Aluno bolsista: não gera mensalidade.</span>}
            </div>
          </div>
        </div>

        {editando && <AcessoPortal alunoId={id} temCpf={Boolean(form.cpf.replace(/\D/g, ''))} />}

        <div className="card">
          <h2>Reconhecimento facial (LGPD)</h2>
          <label className="checkbox">
            <input type="checkbox" checked={form.consentimentoBiometria} onChange={set('consentimentoBiometria')} />
            <span>
              O aluno (ou responsável, se menor de idade) <strong>autorizou</strong> o uso da foto para registro automático de presença
              por reconhecimento facial. Sem esta autorização o vetor facial não é armazenado.
            </span>
          </label>
          {editando && (
            <label className="checkbox" style={{ marginTop: 12 }}>
              <input type="checkbox" checked={form.ativo} onChange={set('ativo')} />
              <span>Aluno ativo</span>
            </label>
          )}
        </div>

        <div className="linha" style={{ justifyContent: 'flex-end', marginTop: 16 }}>
          <button type="button" className="btn btn-secundario" onClick={() => navigate('/alunos')}>Cancelar</button>
          <button className="btn btn-primario" disabled={salvando || processandoFoto}>{salvando ? <Spinner /> : 'Salvar'}</button>
        </div>
      </form>

      {editandoResponsavel && (
        <ModalResponsavel
          inicial={editandoResponsavel}
          onFechar={() => setEditandoResponsavel(null)}
          onSalvar={(r) => { setForm((f) => ({ ...f, responsavel: r })); setEditandoResponsavel(null) }}
        />
      )}
    </>
  )
}
