import { useState, useEffect, useRef } from 'react'
import { getClothes, uploadClothes, deleteClothes } from '../api/clothes'

const CATEGORIES = ['전체', '상의', '하의', '아우터', '신발', '가방', '액세서리']

export default function Wardrobe() {
  const [clothes, setClothes] = useState([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [filter, setFilter] = useState('전체')
  const [selected, setSelected] = useState(null)
  const fileRef = useRef()

  useEffect(() => {
    getClothes()
      .then((res) => setClothes(res.data.data))
      .finally(() => setLoading(false))
  }, [])

  const handleUpload = async (e) => {
    const file = e.target.files[0]
    if (!file) return

    setUploading(true)
    try {
      const res = await uploadClothes(file)
      setClothes((prev) => [res.data.data, ...prev])
    } catch {
      alert('업로드에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('이 옷을 삭제할까요?')) return
    try {
      await deleteClothes(id)
      setClothes((prev) => prev.filter((c) => c.id !== id))
      setSelected(null)
    } catch {
      alert('삭제에 실패했습니다.')
    }
  }

  const filtered = filter === '전체' ? clothes : clothes.filter((c) => c.category === filter)

  return (
    <div className="page">
      <div className="page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>내 옷장 {clothes.length > 0 && <span style={{ color: '#FF6B6B' }}>{clothes.length}</span>}</span>
          <button
            onClick={() => fileRef.current.click()}
            disabled={uploading}
            style={addBtnStyle}
          >
            {uploading ? '분류 중...' : '+ 추가'}
          </button>
        </div>
        <input ref={fileRef} type="file" accept="image/*" hidden onChange={handleUpload} />
      </div>

      {/* 카테고리 필터 */}
      <div style={filterWrap}>
        {CATEGORIES.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilter(cat)}
            className={`chip ${filter === cat ? 'active' : ''}`}
            style={{ cursor: 'pointer', border: 'none' }}
          >
            {cat}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">👕</div>
          <p>옷이 없어요. 사진을 추가해보세요!</p>
        </div>
      ) : (
        <div style={gridStyle}>
          {filtered.map((item) => (
            <div key={item.id} style={cardStyle} onClick={() => setSelected(item)}>
              <img src={item.imageUrl} alt={item.category} style={imgStyle} />
              <div style={labelStyle}>
                <span>{item.category}</span>
                <span style={{ color: '#999', fontSize: 11 }}>{item.color}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 상세 모달 */}
      {selected && (
        <div style={overlay} onClick={() => setSelected(null)}>
          <div style={modal} onClick={(e) => e.stopPropagation()}>
            <img src={selected.imageUrl} alt={selected.category} style={modalImg} />
            <div style={{ padding: '16px 20px' }}>
              <div style={metaGrid}>
                {[
                  ['카테고리', selected.category],
                  ['색상', selected.color],
                  ['패턴', selected.pattern],
                  ['계절', selected.season],
                  ['스타일', selected.styleTag],
                ].map(([k, v]) => (
                  <div key={k}>
                    <div style={{ color: '#999', fontSize: 12 }}>{k}</div>
                    <div style={{ fontWeight: 600, marginTop: 2 }}>{v}</div>
                  </div>
                ))}
              </div>
              <button className="btn-secondary" style={{ marginTop: 16 }} onClick={() => handleDelete(selected.id)}>
                삭제하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const addBtnStyle = {
  background: '#FF6B6B', color: 'white', border: 'none',
  borderRadius: 8, padding: '8px 14px', fontSize: 14, fontWeight: 600, cursor: 'pointer',
}
const filterWrap = {
  display: 'flex', gap: 8, padding: '12px 20px', overflowX: 'auto',
  WebkitOverflowScrolling: 'touch',
}
const gridStyle = {
  display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
  gap: 2, padding: '0 2px',
}
const cardStyle = {
  aspectRatio: '1', overflow: 'hidden', cursor: 'pointer',
  background: '#f5f5f5', position: 'relative',
}
const imgStyle = {
  width: '100%', height: '100%', objectFit: 'cover',
}
const labelStyle = {
  position: 'absolute', bottom: 0, left: 0, right: 0,
  background: 'linear-gradient(transparent, rgba(0,0,0,0.5))',
  padding: '8px 6px 4px', color: 'white', fontSize: 11,
  display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end',
}
const overlay = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 200,
  display: 'flex', alignItems: 'flex-end',
}
const modal = {
  background: 'white', borderRadius: '20px 20px 0 0',
  width: '100%', maxWidth: 480, margin: '0 auto',
  maxHeight: '80vh', overflowY: 'auto',
}
const modalImg = {
  width: '100%', maxHeight: 300, objectFit: 'cover',
}
const metaGrid = {
  display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12,
}
