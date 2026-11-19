// Reconhecimento facial no navegador (face-api / TensorFlow.js).
// Roda num Web Worker para a tela não travar (iPhone com iOS 16.4+ e navegadores atuais).
// Sem suporte, roda na própria página como reserva.
// As fotos não saem do aparelho para identificação: só os vetores numéricos vão ao servidor.

let motor = null

function suportaWorkerComWebGL() {
  try {
    if (typeof Worker === 'undefined' || typeof OffscreenCanvas === 'undefined' || typeof createImageBitmap === 'undefined') return false
    const c = new OffscreenCanvas(1, 1)
    return !!(c.getContext('webgl2') || c.getContext('webgl'))
  } catch {
    return false
  }
}

// Prazo para o worker dar sinal de vida (inclui baixar o script dele em rede lenta).
const PRAZO_WORKER_MS = 30000

function motorWorker() {
  const worker = new Worker(new URL('./face.worker.js', import.meta.url), { type: 'module' })
  const pendentes = new Map()
  let seq = 0
  let avisarVivo
  const vivo = new Promise((resolve, reject) => {
    avisarVivo = resolve
    setTimeout(() => reject(new Error('O reconhecimento em segundo plano não respondeu.')), PRAZO_WORKER_MS)
  })
  vivo.catch(() => {})
  worker.onmessage = ({ data }) => {
    if (data.tipo === 'vivo') { avisarVivo(); return }
    const p = pendentes.get(data.id)
    if (!p) return
    pendentes.delete(data.id)
    if (data.ok) p.resolve(data.resultado)
    else p.reject(new Error(data.erro))
  }
  worker.onerror = (e) => {
    const erro = new Error(e.message || 'Falha no reconhecimento facial')
    pendentes.forEach((p) => p.reject(erro))
    pendentes.clear()
  }
  const chamar = (msg, transferir = []) => new Promise((resolve, reject) => {
    const id = ++seq
    pendentes.set(id, { resolve, reject })
    worker.postMessage({ ...msg, id }, transferir)
  })
  return {
    tipo: 'worker',
    carregar: async () => { await vivo; return chamar({ tipo: 'carregar' }) },
    analisar: async (canvas, unico) => {
      const imagem = await createImageBitmap(canvas)
      return chamar({ tipo: 'analisar', imagem, unico }, [imagem])
    },
    encerrar: () => worker.terminate(),
  }
}

async function motorPagina() {
  const [faceapi, core] = await Promise.all([import('@vladmandic/face-api'), import('./face-core.js')])
  await faceapi.tf.setBackend('webgl').catch(() => faceapi.tf.setBackend('cpu'))
  await faceapi.tf.ready()
  await core.carregarRedes(faceapi)
  return { tipo: 'pagina', analisar: (canvas, unico) => core.analisar(faceapi, canvas, unico) }
}

function obterMotor() {
  if (!motor) {
    motor = (async () => {
      if (suportaWorkerComWebGL()) {
        const w = motorWorker()
        try {
          await w.carregar()
          return w
        } catch (e) {
          console.warn('Reconhecimento em segundo plano indisponível; usando a página.', e)
          w.encerrar()
        }
      }
      return motorPagina()
    })()
    motor.catch(() => { motor = null })
  }
  return motor
}

/** Prepara o reconhecimento (download dos modelos + aquecimento). Pode ser chamado antecipadamente. */
export const carregarModelos = () => obterMotor().then((m) => m.tipo)

/** Decodifica a foto (respeitando a orientação) e reduz para no máximo `maxLado` px num canvas. */
export async function prepararImagem(file, maxLado) {
  const url = URL.createObjectURL(file)
  try {
    // onload (e não img.decode()): decode() pode não terminar com a página em segundo plano.
    const img = await new Promise((resolve, reject) => {
      const el = new Image()
      el.onload = () => resolve(el)
      el.onerror = () => reject(new Error('Arquivo não é uma imagem válida.'))
      el.src = url
    })
    const escala = Math.min(1, maxLado / Math.max(img.naturalWidth, img.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(img.naturalWidth * escala)
    canvas.height = Math.round(img.naturalHeight * escala)
    canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
    return { canvas, width: canvas.width, height: canvas.height }
  } finally {
    URL.revokeObjectURL(url)
  }
}

/** Libera a memória do canvas (importante no Safari do iPhone, que tem limite baixo). */
export function liberar(prep) {
  if (prep?.canvas) { prep.canvas.width = 0; prep.canvas.height = 0 }
}

/** Foto reduzida em data URL (para salvar no cadastro do aluno). */
export async function lerImagem(file, maxLado = 1280, qualidade = 0.85) {
  const prep = await prepararImagem(file, maxLado)
  try {
    return { dataUrl: prep.canvas.toDataURL('image/jpeg', qualidade), width: prep.width, height: prep.height }
  } finally {
    liberar(prep)
  }
}

/** Rosto único (cadastro do aluno). Retorna o vetor ou null se não houver rosto. */
export async function descritorUnico(file) {
  const [m, prep] = await Promise.all([obterMotor(), prepararImagem(file, 800)])
  try {
    const r = await m.analisar(prep.canvas, true)
    return r.length ? r[0].descritor : null
  } finally {
    liberar(prep)
  }
}

/** Todos os rostos da foto da turma (já preparada com prepararImagem), com a caixa de cada um. */
export async function detectarRostos(prep) {
  const m = await obterMotor()
  return m.analisar(prep.canvas, false)
}
