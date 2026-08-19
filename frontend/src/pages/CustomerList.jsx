import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { deleteCustomer, listCustomers } from '../api.js'

export default function CustomerList() {
  const [customers, setCustomers] = useState([])
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  const load = async () => {
    setLoading(true)
    try {
      setCustomers(await listCustomers())
    } catch (e) {
      if (e.status === 401) return navigate('/login', { replace: true })
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const remove = async (customer) => {
    setMessage(null)
    setError(null)
    try {
      await deleteCustomer(customer.id)
      setMessage(`${customer.firstName} ${customer.lastName} was deleted`)
      await load()
    } catch (e) {
      // The three failure modes the contract defines, each worth its own wording.
      if (e.errorCode === 'CUSTOMER_HAS_ACTIVE_BOOKINGS') {
        setError(`${customer.firstName} ${customer.lastName} has ${e.problem.activeBookingCount} active bookings and cannot be deleted`)
      } else if (e.errorCode === 'BOOKING_SERVICE_UNAVAILABLE') {
        setError('The booking service is unavailable, so we cannot verify bookings right now. Try again later.')
      } else if (e.status === 401) {
        navigate('/login', { replace: true })
      } else {
        setError(e.message)
      }
    }
  }

  if (loading) return <p>Loading…</p>

  return (
    <>
      <div className="toolbar">
        <h1>Customers</h1>
        <Link className="button" to="/customers/new">New customer</Link>
      </div>

      {message && <p className="success" role="status">{message}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      {customers.length === 0 ? (
        <p className="empty">No customers yet.</p>
      ) : (
        <table>
          <thead>
            <tr><th>Name</th><th>Email</th><th>Phone</th><th></th></tr>
          </thead>
          <tbody>
            {customers.map((c) => (
              <tr key={c.id}>
                <td>{c.firstName} {c.lastName}</td>
                <td>{c.email || <span className="muted">—</span>}</td>
                <td>{c.phone || <span className="muted">—</span>}</td>
                <td className="actions">
                  <Link to={`/customers/${c.id}/edit`}>Edit</Link>
                  <button type="button" className="danger" onClick={() => remove(c)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
