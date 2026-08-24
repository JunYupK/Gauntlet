import { z } from 'zod';

export const PointSchema = z.object({ x: z.number().int(), y: z.number().int() }).strict();

export const DirectionSchema = z.enum(['UP', 'DOWN', 'LEFT', 'RIGHT']);

export const DeathReasonSchema = z.enum([
  'P0_OUT_OF_BOUNDS', 'P0_HIT_OWN_WALL', 'P0_HIT_OPPONENT_WALL',
  'P1_OUT_OF_BOUNDS', 'P1_HIT_OWN_WALL', 'P1_HIT_OPPONENT_WALL',
  'HEAD_ON_COLLISION', 'BOTH_DIED', 'MAX_TURNS',
]);

export const MatchResultSchema = z.object({
  winner: z.number().int(),   // 좌석 인덱스. -1이 무승부
  turns: z.number().int().positive(),
  reason: DeathReasonSchema,
}).strict();

export const ReplaySchema = z.object({
  schema: z.literal(1),
  matchId: z.string(),
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  seed: z.number().int(),
  swapped: z.boolean(),
  bot0Id: z.string(), start0: PointSchema, dir0: DirectionSchema,
  bot1Id: z.string(), start1: PointSchema, dir1: DirectionSchema,
  moves: z.string(),
  result: MatchResultSchema,
  hash: z.string().startsWith('sha256:'),
}).strict()
  // moves는 턴당 2글자다. 이게 깨지면 디코더가 조용히 어긋난 방향을
  // 읽으므로, 데이터를 받는 자리에서 잡는다.
  .refine((r) => r.moves.length === r.result.turns * 2,
    { message: 'moves 길이가 턴 수 × 2가 아니다' });

export const GenerationStatSchema = z.object({
  generation: z.number().int().nonnegative(),
  botName: z.string(),
  avgSurvivalTurns: z.number(),
  occupancy: z.number(),
  suicideRate: z.number(),
  scoreRate: z.number(),
  // 승격한 시도가 없으면 NaN이 실린다. Jackson이 따옴표 붙은 "NaN"
  // 문자열로 내보내므로 두 형태를 다 받아 number로 정규화한다 —
  // 화면은 Number.isNaN()으로 "아직 승격 못 함"을 판정한다.
  holdoutScoreRate: z.union([z.number(), z.literal('NaN').transform(() => NaN)]),
  attempts: z.number().int().nonnegative(),
}).strict();

export const AttemptRecordSchema = z.object({
  generation: z.number().int().nonnegative(),
  attempt: z.number().int().positive(),
  verdict: z.enum(['PASSED', 'PROMOTED', 'REJECTED']),
  stage: z.enum(['GATE', 'CHAMPIONSHIP']),
  failedGate: z.string().nullable(),
  detail: z.string(),
}).strict();

export const MoveAnalysisSchema = z.object({
  turn: z.number().int().positive(),        // 1-based
  chose: DirectionSchema,
  best: DirectionSchema,
  reachAfterChosen: z.number().int(),
  reachAfterBest: z.number().int(),
  loss: z.number().int(),
  suicide: z.boolean(),
  fatal: z.boolean(),
}).strict();

export const MatchDiagnosisSchema = z.object({
  matchId: z.string(),
  reach: z.array(z.array(z.number().int())),   // [봇][턴], 턴은 0-based
  loss: z.array(z.array(z.number().int())),
  occupancy: z.array(z.number()),
  suicideRate: z.array(z.number()),
  worstMoves0: z.array(MoveAnalysisSchema),
  worstMoves1: z.array(MoveAnalysisSchema),
}).strict();

export const RoundRobinSchema = z.object({
  bots: z.array(z.string()),
  matrix: z.array(z.array(z.number().nullable())),  // 대각선은 null
}).strict();

export const SourceIndexEntrySchema = z.object({
  generation: z.number().int().nonnegative(),
  botName: z.string(),
  available: z.boolean(),
  file: z.string().nullable(),
}).strict();

export const BundleMetaSchema = z.object({
  demo: z.boolean(),
  generations: z.number().int().nonnegative(),
  gallerySeed: z.number().int(),
}).strict();

export type Replay = z.infer<typeof ReplaySchema>;
export type GenerationStat = z.infer<typeof GenerationStatSchema>;
export type AttemptRecord = z.infer<typeof AttemptRecordSchema>;
export type MoveAnalysis = z.infer<typeof MoveAnalysisSchema>;
export type MatchDiagnosis = z.infer<typeof MatchDiagnosisSchema>;
export type RoundRobinData = z.infer<typeof RoundRobinSchema>;
export type SourceIndexEntry = z.infer<typeof SourceIndexEntrySchema>;
export type BundleMeta = z.infer<typeof BundleMetaSchema>;
export type Direction = z.infer<typeof DirectionSchema>;
