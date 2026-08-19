import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import App from './App.jsx'
import Login from './pages/Login.jsx'
import CustomerList from './pages/CustomerList.jsx'
import CustomerForm from './pages/CustomerForm.jsx'
import { getToken } from './api.js'
import './styles.css'

function RequireToken({ children }) {
  return getToken() ? children : <Navigate to="/login" replace />
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<RequireToken><App /></RequireToken>}>
          <Route path="/" element={<Navigate to="/customers" replace />} />
          <Route path="/customers" element={<CustomerList />} />
          <Route path="/customers/new" element={<CustomerForm />} />
          <Route path="/customers/:id/edit" element={<CustomerForm />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
)
