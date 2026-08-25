import type { Metadata } from 'next';
import './globals.css';
import { Deck } from '@/components/Deck';

export const metadata: Metadata = {
  title: 'Arena',
  description: '결정론적 트론 봇 아레나 — 세대 루프 발표용 시각화',
};

/**
 * `Deck`을 여기 한 번만 마운트한다 — 여섯 화면과 목차 전부가 이 레이아웃
 * 아래에서 렌더되므로, ←/→ 키보드 내비게이션이 어느 화면에서 눌러도
 * 살아있다. 본문에 `pb-14`를 줘서 하단에 고정된 `Deck` 바가 화면 끝의
 * 콘텐츠(예: 표의 마지막 행)를 가리지 않게 한다.
 */
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="pb-14">
        {children}
        <Deck />
      </body>
    </html>
  );
}
