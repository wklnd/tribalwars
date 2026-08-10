import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin API so the app also works when opened from another device
    // (e.g. over Tailscale) instead of assuming the browser is on this machine.
    // The browser talks to this origin only, so the backend never needs CORS: drop the Origin header
    // (browsers add it to POST/DELETE) or the backend's localhost-only CORS rule answers 403.
    proxy: {
      '/api': {
        target: process.env.TWLAN_BACKEND || 'http://localhost:8080',
        configure: (proxy) => proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
      },
    },
    allowedHosts: ['.ts.net'],
  },
})
