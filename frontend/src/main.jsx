import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './App.css'
import App from './App.jsx'
import { ErrorBoundary } from './lib/ErrorBoundary.jsx'

// Last-resort catch: without this, an uncaught render error anywhere below <App/> unmounts the whole
// React tree and leaves only body's CSS background visible (see lib/ErrorBoundary.jsx for the full story).
// App.jsx also wraps its per-screen content in its own boundary, so this one is only for a crash in App's
// own top-level render, before any chrome even gets a chance to show.
function AppCrashed({ error }) {
  return (
    <div style={{ padding: 24, font: '14px Verdana, Arial, sans-serif', color: '#3b2308' }}>
      Something went wrong loading the game.{' '}
      <a href="#" onClick={(e) => { e.preventDefault(); window.location.reload(); }}>Reload</a>.
      {error?.message && (
        <div style={{ marginTop: 8, fontSize: 11, color: '#8a7a5f' }}>({error.message})</div>
      )}
    </div>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ErrorBoundary fallback={(error) => <AppCrashed error={error} />}>
      <App />
    </ErrorBoundary>
  </StrictMode>,
)
