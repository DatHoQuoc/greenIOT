'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Flower2, Loader2 } from 'lucide-react'
import { useSession } from '@/hooks/useSession'
import { errorMessage } from '@/lib/api/client'

export default function LoginPage() {
  const router = useRouter()
  const { signIn, isAuthenticated, isBootstrapping } = useSession()

  const [email, setEmail] = useState('demo@greensense.vn')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // A still-valid refresh cookie resumes the session during bootstrap; landing here with
  // one already resolved means the user does not need to type anything.
  useEffect(() => {
    if (!isBootstrapping && isAuthenticated) router.replace('/')
  }, [isBootstrapping, isAuthenticated, router])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await signIn(email.trim(), password)
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
        <h1>GreenSense</h1>
        <p className="gs-auth-sub">Đăng nhập để theo dõi khu vườn của bạn</p>

        <label className="gs-field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </label>

        <label className="gs-field">
          <span>Mật khẩu</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error && (
          <p className="gs-auth-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="gs-auth-submit" disabled={isSubmitting}>
          {isSubmitting ? <Loader2 size={16} className="gs-spin" /> : null}
          {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>

        <p className="gs-auth-hint">Tài khoản demo: demo@greensense.vn / Green@123</p>
      </form>
    </main>
  )
}
