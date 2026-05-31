import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useAuthStore } from '../store/authStore'
import { logout } from '../api/auth'
import { getClothes } from '../api/clothes'
import { getOutfits, getCalendar } from '../api/outfit'

export default function MyPage() {
  const { user } = useAuth()
  const { refreshToken, clearAuth } = useAuthStore()
  const navigate = useNavigate()

  const [stats, setStats] = useState({ clothes: 0, outfits: 0, worn: 0 })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const today = new Date()
    Promise.all([
      getClothes(),
      getOutfits(),
      getCalendar(today.getFullYear(), today.getMonth() + 1),
    ]).then(([clothesRes, outfitsRes, calRes]) => {
      setStats({
        clothes: clothesRes.data.data.length,
        outfits: outfitsRes.data.data.length,
        worn: calRes.data.data.length,
      })
    }).finally(() => setLoading(false))
  }, [])

  const handleLogout = async () => {
    try {
      await logout(refreshToken)
    } catch {
      // 실패해도 로컬 로그아웃
    } finally {
      clearAuth()
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="page">
      <div className="page-header">마이페이지</div>

      {/* 프로필 */}
      <div style={profileSection}>
        {user?.profileImageUrl ? (
          <img src={user.profileImageUrl} alt="프로필" style={avatar} />
        ) : (
          <div style={{ ...avatar, background: '#FF6B6B', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 }}>
            {user?.nickname?.[0] ?? '?'}
          </div>
        )}
        <div>
          <div style={{ fontWeight: 700, fontSize: 18 }}>{user?.nickname ?? '...'}</div>
          <div style={{ color: '#888', fontSize: 13, marginTop: 2 }}>{user?.email}</div>
          <div style={{ marginTop: 4 }}>
            <span className="chip">
              {user?.provider === 'GOOGLE' ? '🔵 Google' : '🟡 Kakao'}
            </span>
          </div>
        </div>
      </div>

      {/* 통계 */}
      <div style={statsRow}>
        {[
          { label: '보유 옷', value: stats.clothes, unit: '벌' },
          { label: '저장 코디', value: stats.outfits, unit: '개' },
          { label: '이번 달 착용', value: stats.worn, unit: '회' },
        ].map(({ label, value, unit }) => (
          <div key={label} style={statCard}>
            {loading ? (
              <div style={{ color: '#ccc', fontSize: 20 }}>...</div>
            ) : (
              <div style={{ fontSize: 28, fontWeight: 700, color: '#FF6B6B' }}>
                {value}<span style={{ fontSize: 14, fontWeight: 400 }}>{unit}</span>
              </div>
            )}
            <div style={{ fontSize: 13, color: '#888', marginTop: 4 }}>{label}</div>
          </div>
        ))}
      </div>

      {/* 메뉴 */}
      <div style={menuSection}>
        <MenuItem label="공지사항" icon="📢" onClick={() => {}} />
        <MenuItem label="앱 버전" icon="ℹ️" right="1.0.0" onClick={() => {}} />
        <MenuItem label="로그아웃" icon="🚪" onClick={handleLogout} danger />
      </div>
    </div>
  )
}

function MenuItem({ label, icon, right, onClick, danger }) {
  return (
    <button onClick={onClick} style={{ ...menuItem, color: danger ? '#FF6B6B' : '#1a1a1a' }}>
      <span>{icon} {label}</span>
      <span style={{ color: '#bbb', fontSize: 14 }}>{right ?? '›'}</span>
    </button>
  )
}

const profileSection = {
  display: 'flex', alignItems: 'center', gap: 16,
  padding: '24px 20px', borderBottom: '1px solid #f0f0f0',
}
const avatar = {
  width: 64, height: 64, borderRadius: '50%', objectFit: 'cover',
}
const statsRow = {
  display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
  borderBottom: '8px solid #f5f5f5',
}
const statCard = {
  textAlign: 'center', padding: '20px 8px',
  borderRight: '1px solid #f0f0f0',
}
const menuSection = {
  display: 'flex', flexDirection: 'column',
}
const menuItem = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
  padding: '16px 20px', background: 'white', border: 'none',
  borderBottom: '1px solid #f5f5f5', cursor: 'pointer',
  fontSize: 15, textAlign: 'left', width: '100%',
}
