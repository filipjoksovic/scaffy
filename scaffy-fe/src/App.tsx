import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Initializer } from './pages/Initializer'
import { DesignSystem } from './pages/DesignSystem'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Initializer />} />
        <Route path="/design" element={<DesignSystem />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
