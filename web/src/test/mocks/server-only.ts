// vitest용 스텁. 실제 `server-only` 패키지는 default export가 항상
// throw 하는 마커 모듈이다 — Next.js가 클라이언트 번들을 만들 때
// `react-server` 조건이 없는 걸로 감지해서 에러를 던지는 방식이다.
// vitest는 Next의 웹팩 조건을 모르므로 그대로 두면 bundle.ts를
// import하는 모든 테스트가 이 코드와 무관하게 실패한다. 여기서는
// "서버 환경에서 정상적으로 import된 것"과 동일하게 취급해 아무것도
// 하지 않는다 — vitest.config.ts의 resolve.alias가 이 파일로 바꿔치기한다.
export {};
