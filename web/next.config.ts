import type { NextConfig } from 'next';

// R4: 발표 당일 백엔드가 없다. 정적 export만 만든다 —
// API 라우트도 서버 액션도 런타임 데이터 요청도 두지 않는다.
const nextConfig: NextConfig = {
  output: 'export',
  images: { unoptimized: true },
};

export default nextConfig;
