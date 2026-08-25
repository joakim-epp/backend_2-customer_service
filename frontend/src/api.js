const TOKEN_KEY = 'customer-service-token'
const LOGIN_PATH = '/api/auth/login'

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

/**
 * Thrown for every non-2xx response. Carries the parsed problem+json body so callers can
 * branch on errorCode without re-reading the response.
 */
export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || `Request failed with status ${status}`)
    this.status = status
    this.errorCode = problem?.errorCode
    this.problem = problem || {}
  }
}

async function request(path, options = {}) {
  const token = getToken()
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  })

  // Every 401 means the same thing to every caller: the token is gone or stale, start over.
  // Except on login itself, where 401 is wrong credentials and the form shows the message.
  if (response.status === 401) {
    clearToken()
    if (path !== LOGIN_PATH) location.assign('/login')
  }

  if (!response.ok) {
    let problem = null
    try {
      problem = await response.json()
    } catch {
      // A gateway or proxy may answer with something that is not problem+json.
    }
    throw new ApiError(response.status, problem)
  }

  return response.status === 204 ? null : response.json()
}

export const login = async (username, password) => {
  const { token } = await request(LOGIN_PATH, {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
  setToken(token)
}

export const listCustomers = () => request('/api/customers')
export const getCustomer = (id) => request(`/api/customers/${id}`)

export const createCustomer = (customer) =>
  request('/api/customers', { method: 'POST', body: JSON.stringify(customer) })

export const updateCustomer = (id, customer) =>
  request(`/api/customers/${id}`, { method: 'PUT', body: JSON.stringify(customer) })

export const deleteCustomer = (id) =>
  request(`/api/customers/${id}`, { method: 'DELETE' })
