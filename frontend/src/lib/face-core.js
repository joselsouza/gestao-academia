// Núcleo do reconhecimento facial — usado tanto no Web Worker quanto (como reserva) na página.
// Recebe o módulo face-api já importado, para funcionar nos dois ambientes.
//
// OTIMIZAÇÃO PRINCIPAL — tamanhos fixos:
// A GPU (WebGL) compila um "programa" para cada tamanho diferente de imagem que processa. No
// fluxo padrão do face-api cada rosto é recortado no seu próprio tamanho, então TODA foto nova
// obrigava a compilar dezenas de programas — lento no Safari do iPhone. Aqui a foto vai sempre
// para um quadro de tamanho fixo e cada rosto é recortado direto no tamanho que as redes usam
// (112 e 150 px). Depois do aquecimento, nenhuma foto nova compila nada.
// O resultado é o mesmo do face-api (mesmo recorte, mesmo preenchimento preto e mesma
// amostragem), para manter compatibilidade com os rostos já cadastrados.

/** Lado do quadro fixo: foto da turma e foto do cadastro. */
export const LADO = { turma: 1600, unico: 800 }

// Detector leve (TinyFaceDetector). inputSize deve ser múltiplo de 32.
const OPCOES = {
  turma: { inputSize: 704, scoreThreshold: 0.3 },
  unico: { inputSize: 416, scoreThreshold: 0.5 },
}

function criarCanvas(lado) {
  if (typeof OffscreenCanvas !== 'undefined') return new OffscreenCanvas(lado, lado)
  const c = document.createElement('canvas')
  c.width = lado
  c.height = lado
  return c
}

/** Desenha a imagem no canto superior esquerdo de um quadro preto LADO×LADO. */
function quadroFixo(faceapi, imagem, lado) {
  const canvas = criarCanvas(lado)
  const g = canvas.getContext('2d')
  g.fillStyle = '#000'
  g.fillRect(0, 0, lado, lado)
  const escala = imagem ? Math.min(1, lado / Math.max(imagem.width, imagem.height)) : 1
  if (imagem) g.drawImage(imagem, 0, 0, Math.round(imagem.width * escala), Math.round(imagem.height * escala))
  return { tensor: faceapi.tf.browser.fromPixels(canvas), escala }
}

/** Igual ao Box.clipAtImageBorders().floor() do face-api. */
function recortarNaBorda(box, lado) {
  const x = Math.max(box.x, 0)
  const y = Math.max(box.y, 0)
  const w = Math.min(box.x + box.width - x, lado - x)
  const h = Math.min(box.y + box.height - y, lado - y)
  return { x: Math.floor(x), y: Math.floor(y), w: Math.floor(w), h: Math.floor(h) }
}

/**
 * Recorta a caixa e devolve um quadrado `tamanho`×`tamanho`, reproduzindo o face-api
 * (fatia da caixa → completa com preto, centralizado, até virar quadrado → redimensiona),
 * mas com operações de tamanho fixo.
 */
function recorteQuadrado(tf, img4, lado, caixa, tamanho) {
  const s = Math.max(caixa.w, caixa.h)
  const diff = s - Math.min(caixa.w, caixa.h)
  const antes = diff - Math.round(diff * 0.5) // preenchimento antes do conteúdo (como no padToSquare)
  const preencheLargura = caixa.h > caixa.w
  const ox = preencheLargura ? caixa.x - antes : caixa.x
  const oy = preencheLargura ? caixa.y : caixa.y - antes
  // cropAndResize com coordenadas que reproduzem resizeBilinear(alignCorners=false): origem + i*s/tamanho
  const den = lado - 1
  const passo = (s * (tamanho - 1)) / (tamanho * den)
  const y1 = oy / den
  const x1 = ox / den
  // Máscara: zera o que fica fora do conteúdo (equivale ao preenchimento preto)
  const cx0 = caixa.x - ox
  const cy0 = caixa.y - oy
  const mascara = new Float32Array(tamanho * tamanho)
  for (let i = 0; i < tamanho; i++) {
    const sy = (i * s) / tamanho
    if (sy < cy0 || sy >= cy0 + caixa.h) continue
    for (let j = 0; j < tamanho; j++) {
      const sx = (j * s) / tamanho
      if (sx >= cx0 && sx < cx0 + caixa.w) mascara[i * tamanho + j] = 1
    }
  }
  const geometria = { ox, oy, s }
  const tensor = tf.tidy(() => {
    const recorte = tf.image.cropAndResize(img4, tf.tensor2d([[y1, x1, y1 + passo, x1 + passo]]), tf.tensor1d([0], 'int32'), [tamanho, tamanho], 'bilinear', 0)
    return recorte.mul(tf.tensor4d(mascara, [1, tamanho, tamanho, 1])).squeeze([0])
  })
  return { tensor, geometria }
}

/** Marcos do rosto (68 pontos) → caixa alinhada para o descritor, como no face-api. */
async function caixaAlinhada(faceapi, img4, lado, caixa) {
  const { tensor, geometria } = recorteQuadrado(faceapi.tf, img4, lado, caixa, 112)
  try {
    const marcos = await faceapi.nets.faceLandmark68Net.detectLandmarks(tensor)
    const { ox, oy, s } = geometria
    const relativos = marcos.positions.map((p) => new faceapi.Point((ox + (p.x * s) / 112) / lado, (oy + (p.y * s) / 112) / lado))
    // Mesmo alinhamento que o face-api usa para o descritor (olhos + boca, estilo dlib)
    return new faceapi.FaceLandmarks68(relativos, { width: lado, height: lado }).align(null, { useDlibAlignment: true })
  } finally {
    tensor.dispose()
  }
}

async function descritor(faceapi, img4, lado, caixa) {
  const { tensor } = recorteQuadrado(faceapi.tf, img4, lado, caixa, 150)
  try {
    return await faceapi.nets.faceRecognitionNet.computeFaceDescriptor(tensor)
  } finally {
    tensor.dispose()
  }
}

/** Detecta rostos e devolve [{ descritor: number[128], box: {x,y,w,h} }] nas coordenadas da imagem recebida. */
export async function analisar(faceapi, imagem, unico) {
  const { tf } = faceapi
  const lado = unico ? LADO.unico : LADO.turma
  const { tensor, escala } = quadroFixo(faceapi, imagem, lado)
  const img4 = tf.tidy(() => tensor.expandDims(0).toFloat())
  try {
    let deteccoes = await faceapi.detectAllFaces(tensor, new faceapi.TinyFaceDetectorOptions(unico ? OPCOES.unico : OPCOES.turma))
    if (unico) deteccoes = deteccoes.sort((a, b) => b.score - a.score).slice(0, 1)
    const resultados = []
    for (const det of deteccoes) {
      const caixa = recortarNaBorda(det.box, lado)
      if (caixa.w <= 0 || caixa.h <= 0) continue
      const alinhada = recortarNaBorda(await caixaAlinhada(faceapi, img4, lado, caixa), lado)
      if (alinhada.w <= 0 || alinhada.h <= 0) continue
      const d = await descritor(faceapi, img4, lado, alinhada)
      resultados.push({
        descritor: Array.from(d),
        box: { x: det.box.x / escala, y: det.box.y / escala, w: det.box.width / escala, h: det.box.height / escala },
      })
    }
    return resultados
  } finally {
    img4.dispose()
    tensor.dispose()
  }
}

/** Carrega as redes e aquece a GPU nos tamanhos exatos usados (cadastro e turma). */
export async function carregarRedes(faceapi, base = '/models') {
  await Promise.all([
    faceapi.nets.tinyFaceDetector.loadFromUri(base),
    faceapi.nets.faceLandmark68Net.loadFromUri(base),
    faceapi.nets.faceRecognitionNet.loadFromUri(base),
  ])
  const { tf } = faceapi
  for (const [tipo, lado] of [['unico', LADO.unico], ['turma', LADO.turma]]) {
    const { tensor } = quadroFixo(faceapi, null, lado)
    const img4 = tf.tidy(() => tensor.expandDims(0).toFloat())
    try {
      await faceapi.detectAllFaces(tensor, new faceapi.TinyFaceDetectorOptions(OPCOES[tipo]))
      const caixa = { x: 100, y: 100, w: 120, h: 150 }
      await caixaAlinhada(faceapi, img4, lado, caixa)
      await descritor(faceapi, img4, lado, caixa)
    } finally {
      img4.dispose()
      tensor.dispose()
    }
  }
}

