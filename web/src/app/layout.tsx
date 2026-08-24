import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Arena',
  description: '결정론적 트론 봇 아레나 — 세대 루프 발표용 시각화',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
