import { useState, useEffect } from 'react'
import { getCalendar, addCalendarEntry, deleteCalendarEntry, getOutfits } from '../api/outfit'

const DAYS = ['일', '월', '화', '수', '목', '금', '토']

function buildCalendarDays(year, month) {
  const first = new Date(year, month - 1, 1).getDay()
  const last = new Date(year, month, 0).getDate()
  const days = []
  for (let i = 0; i < first; i++) days.push(null)
  for (let d = 1; d <= last; d++) days.push(d)
  return days
}

export default function Calendar() {
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null) // {day, entry}
  const [outfits, setOutfits] = useState([])
  const [addModal, setAddModal] = useState(null) // day number
  const [chosenOutfitId, setChosenOutfitId] = useState('')
  const [memo, setMemo] = useState('')
  const [saving, setSaving] = useState(false)

  const fetchCalendar = () => {
    setLoading(true)
    getCalendar(year, month)
      .then((res) => setEntries(res.data.data))
      .finally(() => setLoading(false))
  }

  useEffect(() => { fetchCalendar() }, [year, month])

  const entryByDay = entries.reduce((acc, e) => {
    const day = parseInt(e.wornDate.split('-')[2])
    acc[day] = e
    return acc
  }, {})

  const prevMonth = () => {
    if (month === 1) { setYear(y => y - 1); setMonth(12) }
    else setMonth(m => m - 1)
  }
  const nextMonth = () => {
    if (month === 12) { setYear(y => y + 1); setMonth(1) }
    else setMonth(m => m + 1)
  }

  const openAddModal = async (day) => {
    setAddModal(day)
    setChosenOutfitId('')
    setMemo('')
    if (outfits.length === 0) {
      const res = await getOutfits()
      setOutfits(res.data.data)
    }
  }

  const handleAdd = async () => {
    if (!chosenOutfitId) return
    setSaving(true)
    try {
      const wornDate = `${year}-${String(month).padStart(2, '0')}-${String(addModal).padStart(2, '0')}`
      await addCalendarEntry({ outfitId: chosenOutfitId, wornDate, memo })
      fetchCalendar()
      setAddModal(null)
    } catch {
      alert('저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('기록을 삭제할까요?')) return
    try {
      await deleteCalendarEntry(id)
      fetchCalendar()
      setSelected(null)
    } catch {
      alert('삭제에 실패했습니다.')
    }
  }

  const days = buildCalendarDays(year, month)

  return (
    <div className="page">
      {/* 헤더 + 월 이동 */}
      <div className="page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <button onClick={prevMonth} style={navBtn}>‹</button>
          <span>{year}년 {month}월</span>
          <button onClick={nextMonth} style={navBtn}>›</button>
        </div>
      </div>

      {/* 요일 헤더 */}
      <div style={dayHeader}>
        {DAYS.map((d, i) => (
          <div key={d} style={{ ...dayCell, color: i === 0 ? '#FF6B6B' : i === 6 ? '#4285F4' : '#999', fontWeight: 600 }}>
            {d}
          </div>
        ))}
      </div>

      {/* 달력 그리드 */}
      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : (
        <div style={calGrid}>
          {days.map((day, idx) => {
            const entry = day ? entryByDay[day] : null
            const isToday = day === today.getDate() && month === today.getMonth() + 1 && year === today.getFullYear()
            const colIdx = idx % 7
            return (
              <div key={idx} style={dayCell}>
                {day && (
                  <>
                    <span style={{
                      fontSize: 13, fontWeight: isToday ? 700 : 400,
                      color: isToday ? 'white' : colIdx === 0 ? '#FF6B6B' : colIdx === 6 ? '#4285F4' : '#1a1a1a',
                      background: isToday ? '#FF6B6B' : 'transparent',
                      borderRadius: '50%', width: 26, height: 26,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      margin: '0 auto 4px',
                    }}>{day}</span>
                    {entry ? (
                      <img
                        src={entry.outfit.clothes[0]?.imageUrl}
                        alt="outfit"
                        style={calThumb}
                        onClick={() => setSelected({ day, entry })}
                      />
                    ) : (
                      <div style={addDot} onClick={() => openAddModal(day)}>+</div>
                    )}
                  </>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* 날짜 상세 모달 */}
      {selected && (
        <div style={overlayStyle} onClick={() => setSelected(null)}>
          <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
            <div style={{ padding: '20px 20px 0' }}>
              <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 12 }}>
                {month}월 {selected.day}일 코디
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, padding: '0 20px 12px', overflowX: 'auto' }}>
              {selected.entry.outfit.clothes.map((c) => (
                <img key={c.id} src={c.imageUrl} alt={c.category} style={{ width: 80, height: 80, objectFit: 'cover', borderRadius: 8 }} />
              ))}
            </div>
            {selected.entry.memo && (
              <p style={{ padding: '0 20px 12px', color: '#555', fontSize: 14 }}>{selected.entry.memo}</p>
            )}
            <div style={{ padding: '0 20px 20px' }}>
              <button className="btn-secondary" onClick={() => handleDelete(selected.entry.id)}>
                기록 삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 코디 추가 모달 */}
      {addModal && (
        <div style={overlayStyle} onClick={() => setAddModal(null)}>
          <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
            <div style={{ padding: 20 }}>
              <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 16 }}>
                {month}월 {addModal}일 코디 기록
              </div>
              <select
                value={chosenOutfitId}
                onChange={(e) => setChosenOutfitId(e.target.value)}
                style={selectStyle}
              >
                <option value="">코디를 선택하세요</option>
                {outfits.map((o) => (
                  <option key={o.id} value={o.id}>{o.name}</option>
                ))}
              </select>
              <textarea
                placeholder="메모 (선택)"
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                style={textareaStyle}
              />
              <button className="btn-primary" onClick={handleAdd} disabled={!chosenOutfitId || saving}>
                {saving ? '저장 중...' : '기록하기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const navBtn = {
  background: 'none', border: 'none', fontSize: 24,
  cursor: 'pointer', color: '#1a1a1a', padding: '0 8px',
}
const dayHeader = {
  display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
  padding: '8px 0', borderBottom: '1px solid #f0f0f0',
  textAlign: 'center',
}
const calGrid = {
  display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
}
const dayCell = {
  textAlign: 'center', padding: '6px 2px',
  minHeight: 56,
}
const calThumb = {
  width: 36, height: 36, objectFit: 'cover',
  borderRadius: 6, cursor: 'pointer', margin: '0 auto',
  display: 'block',
}
const addDot = {
  width: 36, height: 36, border: '1.5px dashed #ddd',
  borderRadius: 6, display: 'flex', alignItems: 'center',
  justifyContent: 'center', color: '#ccc', fontSize: 18,
  cursor: 'pointer', margin: '0 auto',
}
const overlayStyle = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
  zIndex: 200, display: 'flex', alignItems: 'flex-end',
}
const modalStyle = {
  background: 'white', borderRadius: '20px 20px 0 0',
  width: '100%', maxWidth: 480, margin: '0 auto',
  maxHeight: '70vh', overflowY: 'auto',
}
const selectStyle = {
  width: '100%', padding: '12px 14px', borderRadius: 8,
  border: '1.5px solid #e0e0e0', fontSize: 15,
  marginBottom: 12, outline: 'none',
}
const textareaStyle = {
  width: '100%', padding: '12px 14px', borderRadius: 8,
  border: '1.5px solid #e0e0e0', fontSize: 14,
  marginBottom: 12, resize: 'none', height: 80,
  outline: 'none', fontFamily: 'inherit',
}
