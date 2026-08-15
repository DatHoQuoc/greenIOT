'use client'

// Onboarding: tài khoản mới chưa có khu vườn nào.
//
// Trước đây màn hình chủ rơi thẳng vào `return null` khi `gardenId` là null — người dùng
// đăng nhập xong thấy một trang TRẮNG, không thông báo, không cách nào đi tiếp. Đây là
// đường thoát cho trạng thái đó, và cũng là chỗ duy nhất trong ứng dụng tạo được vườn.

import { useEffect, useState } from 'react'
import { Flower2, Loader2, Plus } from 'lucide-react'
import { createGarden } from '@/lib/api/garden/gardenApi'
import { client, errorMessage } from '@/lib/api/client'

interface PlantProfile {
  id: string
  name: string
  notes: string | null
}

export function CreateGarden({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('Vườn Nhà')
  const [description, setDescription] = useState('Garden Outdoor')
  const [profiles, setProfiles] = useState<PlantProfile[]>([])
  const [profileId, setProfileId] = useState<string>('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Ngưỡng cảnh báo của vườn được gieo từ hồ sơ cây trồng. Không chọn cũng được — backend
  // rơi về bộ mặc định cho rau ăn lá.
  useEffect(() => {
    let alive = true
    client
      .get<PlantProfile[]>('/api/v1/plant-profiles')
      .then((data) => alive && setProfiles(data))
      .catch(() => {
        // Danh sách này là tuỳ chọn; hỏng thì vẫn tạo vườn được.
      })
    return () => {
      alive = false
    }
  }, [])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await createGarden({
        name: name.trim(),
        description: description.trim() || undefined,
        type: 'OUTDOOR',
        timezone: 'Asia/Ho_Chi_Minh',
        plantProfileId: profileId || undefined,
      })
      onCreated()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="gs-auth-shell">
      <form className="gs-auth-card" onSubmit={handleSubmit}>
        <div className="gs-auth-mark">
          <Flower2 size={26} />
        </div>
        <h1>Tạo khu vườn</h1>
        <p className="gs-auth-sub">Bạn chưa có khu vườn nào. Tạo một cái để bắt đầu theo dõi.</p>

        <label className="gs-field">
          <span>Tên khu vườn</span>
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
        </label>

        <label className="gs-field">
          <span>Mô tả</span>
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Ví dụ: sân thượng, ban công..."
            maxLength={120}
          />
        </label>

        {profiles.length > 0 && (
          <label className="gs-field">
            <span>Loại cây trồng</span>
            <select value={profileId} onChange={(e) => setProfileId(e.target.value)}>
              <option value="">Mặc định (rau ăn lá)</option>
              {profiles.map((profile) => (
                <option key={profile.id} value={profile.id}>
                  {profile.name}
                </option>
              ))}
            </select>
          </label>
        )}

        {error && (
          <p className="gs-auth-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="gs-auth-submit" disabled={isSubmitting}>
          {isSubmitting ? <Loader2 size={16} className="gs-spin" /> : <Plus size={16} />}
          {isSubmitting ? 'Đang tạo...' : 'Tạo khu vườn'}
        </button>

        <p className="gs-auth-hint">
          Sau khi tạo, thêm cảm biến và thiết bị rồi cho chúng gửi dữ liệu về.
        </p>
      </form>
    </main>
  )
}
