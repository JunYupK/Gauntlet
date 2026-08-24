import path from 'node:path';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // `server-only`는 Next의 react-server 조건 밖에서는 항상 throw
      // 하도록 만들어진 마커 패키지다. vitest는 그 조건을 모르므로,
      // 테스트 안에서는 "서버 환경에서 정상 import됐다"와 같은 뜻인
      // no-op 스텁으로 바꿔치기한다.
      'server-only': path.resolve(__dirname, 'src/test/mocks/server-only.ts'),
    },
  },
  test: {
    environment: 'node',
    include: ['src/test/**/*.test.ts', 'src/test/**/*.test.tsx'],
  },
});
