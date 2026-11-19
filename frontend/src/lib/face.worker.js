// Web Worker: roda o reconhecimento facial fora da página, para a tela não travar.
import * as faceapi from '@vladmandic/face-api'
import { analisar, carregarRedes } from './face-core.js'

// O face-api espera um navegador "normal"; no worker configuramos o ambiente manualmente.
const naoDisponivel = () => { throw new Error('não disponível no worker') }
faceapi.env.setEnv({
  Canvas: OffscreenCanvas,
  CanvasRenderingContext2D: self.OffscreenCanvasRenderingContext2D,
  Image: class {},
  ImageData,
  Video: class {},
  createCanvasElement: () => new OffscreenCanvas(1, 1),
  createImageElement: naoDisponivel,
  createVideoElement: naoDisponivel,
  fetch: (url, init) => fetch(url, init),
  readFile: naoDisponivel,
})

let pronto = null

function carregar() {
  if (!pronto) {
    pronto = (async () => {
      await faceapi.tf.setBackend('webgl')
      await faceapi.tf.ready()
      if (faceapi.tf.getBackend() !== 'webgl') throw new Error('WebGL indisponível no worker')
      await carregarRedes(faceapi)
      return faceapi.tf.getBackend()
    })()
    pronto.catch(() => { pronto = null })
  }
  return pronto
}

self.onmessage = async ({ data }) => {
  const { id, tipo } = data
  try {
    if (tipo === 'carregar') {
      self.postMessage({ id, ok: true, resultado: await carregar() })
    } else if (tipo === 'analisar') {
      await carregar()
      try {
        self.postMessage({ id, ok: true, resultado: await analisar(faceapi, data.imagem, data.unico) })
      } finally {
        data.imagem.close?.()
      }
    }
  } catch (e) {
    self.postMessage({ id, ok: false, erro: String(e?.message || e) })
  }
}

// Avisa a página que o worker subiu (a página desiste dele se este aviso não chegar).
self.postMessage({ tipo: 'vivo' })
