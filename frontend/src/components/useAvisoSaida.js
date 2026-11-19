import { useEffect } from 'react'

/** Avisa o navegador antes de sair/recarregar a página com alterações não salvas. */
export function useAvisoSaida(ativo) {
  useEffect(() => {
    if (!ativo) return undefined
    const handler = (e) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [ativo])
}
