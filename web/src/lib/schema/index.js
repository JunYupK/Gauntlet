// CommonJS require() 호환 셔틀. `schema.contract.test.ts`의 마지막 케이스가
// (브리프 원문 그대로) `require('../lib/schema')`를 확장자 없이 호출한다.
// Vitest가 노출하는 require()는 확장자 없는 상대경로를 Node의 기본
// 확장자 목록(.js/.json/.node)으로만 찾고 .ts는 시도하지 않는다 — 반면
// 확장자를 명시한 require('../schema.ts')는 vite-node 모듈 그래프를 타고
// 정상 로드된다. 그래서 이 디렉터리(자기 이름은 파일 확장자 없는
// 'schema'라서, 형제 파일 `../schema.ts`와 충돌하지 않는다)가 그 다리
// 역할을 한다. ESM import('./schema')는 확장자 있는 파일을 디렉터리보다
// 먼저 찾으므로 항상 진짜 schema.ts로 간다 — 이 파일은 require()
// 호출 경로에서만 밟힌다. 타입의 단일 출처는 여전히 schema.ts 하나다 —
// 이 파일은 손으로 쓴 타입/스키마를 담지 않고 재수출만 한다.
module.exports = require('../schema.ts');
