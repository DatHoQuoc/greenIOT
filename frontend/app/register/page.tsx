'use client'

// Đăng ký. Trước đây chỉ có màn hình đăng nhập, nên trên production (profile `prod`, seeder
// tắt) không có cách nào tạo tài khoản từ giao diện — phải gọi API bằng curl.

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Flower2, Loader2 } from 'lucide-react'
import { useSession } from '@/hooks/useSession'
import { register as registerApi } from '@/lib/api/auth/authApi'
import { errorMessage } from '@/lib/api/client'

export default function RegisterPage() {
  const router = useRouter()
  const { isAuthenticated, isBootstrapping, refresh } = useSession()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!isBootstrapping && isAuthenticated) router.replace('/')
  }, [isBootstrapping, isAuthenticated, router])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await registerApi({ email: email.trim(), password, fullName: fullName.trim() })
      // registerApi đã cất access token vào bộ nhớ; refresh() để provider nạp hồ sơ và
      // danh sách vườn, rồi trang chủ tự đưa sang màn hình tạo vườn.
      refresh()
      router.replace('/')
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
        <h1>Tạo tài khoản</h1>
        <p className="gs-auth-sub">Bắt đầu theo dõi khu vườn của bạn</p>

        <label className="gs-field">
          <span>Họ tên</span>
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} required autoComplete="name" />
        </label>

        <label className="gs-field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
        </label>

        <label className="gs-field">
          <span>Mật khẩu</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
          {/* Nói trước điều kiện, thay vì để backend trả 400 sau khi bấm gửi. */}
          <small className="gs-field-hint">Tối thiểu 8 ký tự</small>
        </label>

        {error && (
          <p className="gs-auth-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="gs-auth-submit" disabled={isSubmitting}>
          {isSubmitting ? <Loader2 size={16} className="gs-spin" /> : null}
          {isSubmitting ? 'Đang tạo...' : 'Đăng ký'}
        </button>

        <p className="gs-auth-hint">
          Đã có tài khoản?{' '}
          <Link href="/login" className="gs-auth-link">
            Đăng nhập
          </Link>
        </p>
      </form>
    </main>
  )
}
