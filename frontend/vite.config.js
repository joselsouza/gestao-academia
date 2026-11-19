import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import academia from './academia.plugin.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), academia()],
})
