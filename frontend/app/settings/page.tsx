'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { ArrowLeft, BellRing, LogOut, Mail, MoonStar, Users } from 'lucide-react'

import { useSession } from '@/hooks/useSession'
import { ErrorState, Skeleton } from '@/components/screen-state'
import { errorMessage } from '@/lib/api/client'
import { updateProfile } from '@/lib/api/auth/authApi'
import { addMember, removeMember } from '@/lib/api/garden/gardenApi'
import type { Garden, User } from '@/lib/api/types'

export default function SettingsPage() {
  const router = useRouter()
  const { user, gardens, gardenId, isBootstrapping, isAuthenticated, signOut, refresh } = useSession()

  const [profile, setProfile] = useState<User | null>(null)
  const [garden, setGarden] = useState<Garden | null>(null)
  const [inviteEmail, setInviteEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) router.replace('/login')
  }, [isBootstrapping, isAuthenticated, router])

  useEffect(() => setProfile(user), [user])
  useEffect(() => setGarden(gardens.find((g) => g.id === gardenId) ?? null), [gardens, gardenId])

  async function savePreference(patch: Partial<User>) {
    if (!profile) return
    setError(null)
    setMessage(null)
    setIsSaving(true)
    // Optimistic — a toggle that waits on the network reads as broken.
    const previous = profile
    setProfile({ ...profile, ...patch })
    try {
      const updated = await updateProfile({
        notifyByPush: patch.notifyByPush ?? profile.notifyByPush,
        notifyByEmail: patch.notifyByEmail ?? profile.notifyByEmail,
        quietHoursStart: patch.quietHoursStart !== undefined ? patch.quietHoursStart : profile.quietHoursStart,
        quietHoursEnd: patch.quietHoursEnd !== undefined ? patch.quietHoursEnd : profile.quietHoursEnd,
      })
      setProfile(updated)
      setMessage('Đã lưu')
    } catch (e) {
      setProfile(previous)
      setError(errorMessage(e))
    } finally {
      setIsSaving(false)
    }
  }

  async function handleInvite(event: React.FormEvent) {
    event.preventDefault()
    if (!gardenId || !inviteEmail.trim()) return
    setError(null)
    setMessage(null)
    try {
      const updated = await addMember(gardenId, inviteEmail.trim())
      setGarden(updated)
      setInviteEmail('')
      setMessage('Đã chia sẻ khu vườn')
      refresh()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function handleRemove(memberUserId: string) {
    if (!gardenId) return
    setError(null)
    try {
      const updated = await removeMember(gardenId, memberUserId)
      setGarden(updated)
      refresh()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const quietHoursOn = Boolean(profile?.quietHoursStart && profile?.quietHoursEnd)

  return (
    <main className="sensor-detail-shell">
      <div className="sensor-detail-content">
        <header className="sensor-detail-header">
          <Link href="/" className="sensor-back" aria-label="Quay lại trang chủ">
            <ArrowLeft size={20} />
          </Link>
          <h1>Cài đặt</h1>
          <span style={{ width: 34 }} />
        </header>

        {isBootstrapping || !profile ? (
          <div className="gs-skeleton-stack">
            <Skeleton height={90} />
            <Skeleton height={150} />
          </div>
        ) : (
          <>
            <article className="ph-card">
              <div className="ph-card-heading">
                <div className="ph-heading-icon">
                  <BellRing size={18} />
                </div>
                <div>
                  <h2>Thông báo</h2>
                  <p>{profile.email}</p>
                </div>
              </div>

              <label className="gs-switch-row">
                <span>
                  <BellRing size={14} /> Thông báo đẩy
                </span>
                <input
                  type="checkbox"
                  checked={profile.notifyByPush}
                  disabled={isSaving}
                  onChange={(e) => savePreference({ notifyByPush: e.target.checked })}
                />
              </label>

              <label className="gs-switch-row">
                <span>
                  <Mail size={14} /> Thông báo qua email
                </span>
                <input
                  type="checkbox"
                  checked={profile.notifyByEmail}
                  disabled={isSaving}
                  onChange={(e) => savePreference({ notifyByEmail: e.target.checked })}
                />
              </label>

              <label className="gs-switch-row">
                <span>
                  <MoonStar size={14} /> Giờ yên tĩnh (22:00 – 06:00)
                </span>
                <input
                  type="checkbox"
                  checked={quietHoursOn}
                  disabled={isSaving}
                  onChange={(e) =>
                    savePreference(
                      e.target.checked
                        ? { quietHoursStart: '22:00:00', quietHoursEnd: '06:00:00' }
                        : { quietHoursStart: null, quietHoursEnd: null }
                    )
                  }
                />
              </label>
              {/* Worth stating plainly, or someone silences alerts they needed. */}
              <p className="gs-note">
                Giờ yên tĩnh chỉ tạm hoãn cảnh báo thường. Cảnh báo nghiêm trọng vẫn được gửi ngay.
              </p>
            </article>

            {garden && (
              <article className="ph-card">
                <div className="ph-card-heading">
                  <div className="ph-heading-icon">
                    <Users size={18} />
                  </div>
                  <div>
                    <h2>Chia sẻ khu vườn</h2>
                    <p>{garden.name}</p>
                  </div>
                </div>

                {garden.members.length === 0 ? (
                  <p className="gs-note">Chưa chia sẻ với ai.</p>
                ) : (
                  <ul className="gs-member-list">
                    {garden.members.map((member) => (
                      <li key={member.userId}>
                        <span>
                          <b>{member.fullName ?? member.email}</b>
                          <small>{member.email}</small>
                        </span>
                        {garden.viewerIsOwner && (
                          <button onClick={() => handleRemove(member.userId)}>Gỡ</button>
                        )}
                      </li>
                    ))}
                  </ul>
                )}

                {/* Owner-only, so the form is hidden rather than shown and then 403'd. */}
                {garden.viewerIsOwner && (
                  <form className="gs-invite" onSubmit={handleInvite}>
                    <input
                      type="email"
                      placeholder="email@thanhvien.vn"
                      value={inviteEmail}
                      onChange={(e) => setInviteEmail(e.target.value)}
                      required
                    />
                    <button type="submit">Mời</button>
                  </form>
                )}
                <p className="gs-note">
                  Thành viên có thể xem và điều khiển khu vườn, nhưng không sửa được thiết bị, quy tắc hay lịch tưới.
                </p>
              </article>
            )}

            {message && <p className="gs-inline-ok">{message}</p>}
            {error && <p className="gs-inline-error" role="alert">{error}</p>}

            <button
              className="export-button"
              onClick={async () => {
                await signOut()
                router.replace('/login')
              }}
            >
              <LogOut size={16} /> Đăng xuất
            </button>
          </>
        )}
      </div>
    </main>
  )
}
