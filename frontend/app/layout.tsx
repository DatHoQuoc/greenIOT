import { Analytics } from '@vercel/analytics/next'
import type { Metadata, Viewport } from 'next'
import { SessionProvider } from '@/components/session-provider'
import './globals.css'
import './api-states.css'

export const metadata: Metadata = {
  title: 'GreenSense | My Garden',
  description: 'Theo dõi và chăm sóc khu vườn thông minh của bạn với GreenSense.',
  generator: 'v0.app',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
}

export const viewport: Viewport = {
  colorScheme: 'light dark',
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: 'white' },
    { media: '(prefers-color-scheme: dark)', color: 'black' },
  ],
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="vi" className="bg-background">
      <body className="antialiased">
        {/* Owns the access token and the selected garden for every screen below. */}
        <SessionProvider>{children}</SessionProvider>
        {/*
          Chỉ nạp khi thực sự chạy trên Vercel. Ứng dụng đang được phục vụ từ droplet, ở đó
          /_vercel/insights/script.js không tồn tại — script hỏng, trình chặn quảng cáo báo
          ERR_BLOCKED_BY_CLIENT, và console đỏ lên vì một thứ chẳng liên quan gì đến ứng dụng.
        */}
        {process.env.NEXT_PUBLIC_VERCEL_ENV && <Analytics />}
      </body>
    </html>
  )
}
