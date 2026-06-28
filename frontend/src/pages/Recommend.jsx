import { useState } from 'react'
import { useWeather } from '../hooks/useWeather'
import { getClothes } from '../api/clothes'
import { recommendOutfits, createOutfit } from '../api/outfit'

const CONDITION_EMOJI = {
  '맑음': '☀️', '구름많음': '⛅', '흐림': '☁️',
  '비': '🌧️', '비/눈': '🌨️', '눈': '❄️', '소나기': '⛈️',
}

export default function Recommend() {
  const { weather, loading: weatherLoading } = useWeather()
  const [recommendations, setRecommendations] = useState(null)
  const [allClothes, setAllClothes] = useState(null)
  const [loading, setLoading] = useState(false)
  const [savingId, setSavingId] = useState(null)
  const [savedIds, setSavedIds] = useState(new Set())

  const handleRecommend = async () => {
    setLoading(true)
    try {
      const clothesRes = allClothes ?? (await getClothes()).data.data
      setAllClothes(clothesRes)

      if (clothesRes.length === 0) {
        alert('옷장이 비어있어요. 먼저 옷을 추가해주세요!')
        return
      }

      const res = await recommendOutfits(weather)
      setRecommendations(res.data.data.outfits)
    } catch {
      alert('추천 요청에 실패했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async (outfit, idx) => {
    setSavingId(idx)
    try {
      await createOutfit({
        name: `AI 추천 코디 ${new Date().toLocaleDateString('ko-KR')}`,
        styleTag: outfit.styleTag,
        weatherCondition: weather.condition,
        clothesIds: outfit.clothesIds,
      })
      setSavedIds((prev) => new Set([...prev, idx]))
    } catch {
      alert('저장에 실패했습니다.')
    } finally {
      setSavingId(null)
    }
  }

  const getClothesById = (ids) =>
    (allClothes ?? []).filter((c) => ids.includes(c.id))

  return (
    <div className="page">
      <div className="page-header">오늘의 코디 추천</div>

      {/* 날씨 카드 */}
      <div style={weatherCard}>
        {weatherLoading ? (
          <div className="spinner" />
        ) : weather ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <span style={{ fontSize: 48 }}>{CONDITION_EMOJI[weather.condition] ?? '🌤️'}</span>
            <div>
              <div style={{ fontSize: 32, fontWeight: 700 }}>{weather.temperature}°C</div>
              <div style={{ color: '#666', fontSize: 14 }}>
                {weather.condition} · 습도 {weather.humidity}% · 바람 {weather.windSpeed}m/s
              </div>
            </div>
          </div>
        ) : (
          <p style={{ color: '#999' }}>날씨 정보를 불러올 수 없습니다.</p>
        )}
      </div>

      {/* 추천 버튼 */}
      <div style={{ padding: '0 20px 20px' }}>
        <button
          className="btn-primary"
          onClick={handleRecommend}
          disabled={loading || weatherLoading || !weather}
        >
          {loading ? '분석 중...' : recommendations ? '다시 추천받기' : '✨ 코디 추천받기'}
        </button>
      </div>

      {/* 추천 결과 */}
      {recommendations && (
        <div style={{ padding: '0 20px', display: 'flex', flexDirection: 'column', gap: 16 }}>
          {recommendations.map((outfit, idx) => {
            const outfitClothes = getClothesById(outfit.clothesIds)
            const saved = savedIds.has(idx)
            return (
              <div key={idx} className="card" style={{ padding: 16 }}>
                {/* 옷 썸네일 */}
                <div style={thumbRow}>
                  {outfitClothes.map((c) => (
                    <img key={c.id} src={c.imageUrl} alt={c.category} style={thumb} />
                  ))}
                </div>

                {/* 스타일 태그 */}
                <div style={{ marginBottom: 8 }}>
                  <span className="chip active">{outfit.styleTag}</span>
                </div>

                {/* 추천 이유 */}
                <p style={{ fontSize: 14, color: '#444', lineHeight: 1.6, marginBottom: 14 }}>
                  {outfit.reason}
                </p>

                <button
                  className={saved ? 'btn-secondary' : 'btn-primary'}
                  onClick={() => !saved && handleSave(outfit, idx)}
                  disabled={savingId === idx || saved}
                >
                  {saved ? '저장됨 ✓' : savingId === idx ? '저장 중...' : '코디 저장하기'}
                </button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

const weatherCard = {
  margin: '16px 20px',
  padding: '20px 24px',
  background: 'linear-gradient(135deg, #FF6B6B22, #FF6B6B11)',
  borderRadius: 16,
  border: '1px solid #FF6B6B33',
}
const thumbRow = {
  display: 'flex', gap: 8, marginBottom: 12,
  overflowX: 'auto', WebkitOverflowScrolling: 'touch',
}
const thumb = {
  width: 72, height: 72, objectFit: 'cover',
  borderRadius: 8, flexShrink: 0, background: '#f0f0f0',
}
