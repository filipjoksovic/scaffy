import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Initializer } from './pages/Initializer'
import { DesignSystem } from './pages/DesignSystem'
import { Analyzer } from './pages/Analyzer'
import { Dashboard } from './pages/Dashboard'
import { Landing } from './pages/Landing'
import { Login } from './pages/Login'
import { WorkspaceMembers } from './pages/WorkspaceMembers'
import { WorkspaceSettings } from './pages/WorkspaceSettings'
import { AuthProvider } from './lib/auth'
import { WorkspaceProvider } from './lib/workspace'
import { AuthErrorBanner } from './components/AuthErrorBanner'
import './App.css'

function App() {
  return (
    <AuthProvider>
      <WorkspaceProvider>
        <BrowserRouter>
          <AuthErrorBanner />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/init" element={<Initializer />} />
            <Route path="/analyze" element={<Analyzer />} />
            <Route path="/design" element={<DesignSystem />} />
            <Route path="/workspace" element={<WorkspaceSettings />} />
            <Route path="/workspace/members" element={<WorkspaceMembers />} />
          </Routes>
        </BrowserRouter>
      </WorkspaceProvider>
    </AuthProvider>
  )
}

export default App
