import 'server-only';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import {
  ReplaySchema, GenerationStatSchema, AttemptRecordSchema, MatchDiagnosisSchema,
  RoundRobinSchema, SourceIndexEntrySchema, BundleMetaSchema,
  type Replay, type GenerationStat, type AttemptRecord, type MatchDiagnosis,
  type RoundRobinData, type SourceIndexEntry, type BundleMeta,
} from './schema';
import { z } from 'zod';

export interface Bundle {
  meta: BundleMeta;
  gallery: Replay[];
  diagnosis: MatchDiagnosis[];
  generations: GenerationStat[];
  loopHistory: Record<string, AttemptRecord[]>;
  roundRobin: RoundRobinData;
  sources: SourceIndexEntry[];
  sourceText: Record<number, string>;
}

/**
 * 번들 디렉터리는 ARENA_BUNDLE이 정한다. 기본값을 두지 않는 이유는
 * 발표용 빌드가 조용히 데모 번들로 나가는 것을 막기 위해서다 —
 * 값이 없으면 빌드가 여기서 즉시 죽는다.
 */
function bundleDir(): string {
  const rel = process.env.ARENA_BUNDLE;
  if (!rel) {
    throw new Error(
      'ARENA_BUNDLE이 설정되지 않았다. 진짜 번들은 public/data (먼저 ./gradlew record), ' +
      '데모 번들은 fixtures/data (먼저 ./gradlew fixture).',
    );
  }
  const dir = path.join(process.cwd(), rel);
  if (!existsSync(path.join(dir, 'meta.json'))) {
    throw new Error(
      `번들이 없다: ${dir}. 진짜 번들은 ./gradlew record, 데모 번들은 ./gradlew fixture 로 만든다.`,
    );
  }
  return dir;
}

function read<T>(dir: string, file: string, schema: z.ZodType<T>): T {
  return schema.parse(JSON.parse(readFileSync(path.join(dir, file), 'utf8')));
}

export function loadBundle(): Bundle {
  const dir = bundleDir();

  const sources = read(dir, 'sources/index.json', z.array(SourceIndexEntrySchema));
  const sourceText: Record<number, string> = {};
  for (const entry of sources) {
    if (entry.file) {
      sourceText[entry.generation] = readFileSync(path.join(dir, entry.file), 'utf8');
    }
  }

  return {
    meta: read(dir, 'meta.json', BundleMetaSchema),
    gallery: read(dir, 'gallery.json', z.array(ReplaySchema)),
    diagnosis: read(dir, 'diagnosis.json', z.array(MatchDiagnosisSchema)),
    generations: read(dir, 'generations.json', z.array(GenerationStatSchema)),
    loopHistory: read(dir, 'loop-history.json', z.record(z.string(), z.array(AttemptRecordSchema))),
    roundRobin: read(dir, 'roundrobin.json', RoundRobinSchema),
    sources,
    sourceText,
  };
}
