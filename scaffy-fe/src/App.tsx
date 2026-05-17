import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Initializer } from './pages/Initializer'
import { DesignSystem } from './pages/DesignSystem'
import { Analyzer } from './pages/Analyzer'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Initializer />} />
        <Route path="/analyze" element={<Analyzer />} />
        <Route path="/design" element={<DesignSystem />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
