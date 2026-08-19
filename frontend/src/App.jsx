import { Link, Outlet, useNavigate } from 'react-router-dom'
import { clearToken } from './api.js'

export default function App() {
  const navigate = useNavigate()

  const logout = () => {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <div className="layout">
      <header>
        <Link to="/customers" className="brand">Customer Service</Link>
        <nav>
          <a href="/swagger-ui/index.html" target="_blank" rel="noreferrer">API docs</a>
          <button type="button" className="link" onClick={logout}>Log out</button>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
      <footer>Pensionat &middot; customer service</footer>
    </div>
  )
}
