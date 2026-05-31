import { NavLink } from 'react-router-dom'

const TABS = [
  { to: '/wardrobe',  icon: '👕', label: '옷장' },
  { to: '/recommend', icon: '✨', label: '추천' },
  { to: '/calendar',  icon: '📅', label: '캘린더' },
  { to: '/mypage',    icon: '👤', label: '마이' },
]

export default function BottomNav() {
  return (
    <nav className="bottom-nav">
      {TABS.map(({ to, icon, label }) => (
        <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'active' : ''}>
          <span className="nav-icon">{icon}</span>
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}
