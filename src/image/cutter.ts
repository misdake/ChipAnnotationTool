import fs from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";
import type { ImageContent } from "../types.js";
import { BLOCK_LEVEL, TILE_SIZE } from "./preprocess.js";

const BLOCK_SIZE = TILE_SIZE << BLOCK_LEVEL;
const TILE_WRITE_CONCURRENCY = 8;

export interface CutProgress {
  doneTile(level: number, x: number, y: number, current: number, total: number): void;
  show(message: string): void;
  isCanceled?(): boolean;
}

export async function cutAll(imagePath: string, content: ImageContent, chipFolder: string, progress: CutProgress): Promise<void> {
  for (const level of content.levels) {
    await fs.mkdir(path.join(chipFolder, String(level.level)), { recursive: true });
  }

  const total = content.levels.reduce((sum, level) => sum + level.xMax * level.yMax, 0);
  let current = 0;

  const blockCountX = Math.floor((content.width - 1) / BLOCK_SIZE) + 1;
  const blockCountY = Math.floor((content.height - 1) / BLOCK_SIZE) + 1;
  let blockIndex = 0;

  for (let blockX = 0; blockX < blockCountX; blockX += 1) {
    for (let blockY = 0; blockY < blockCountY; blockY += 1) {
      throwIfCanceled(progress);
      blockIndex += 1;
      progress.show(`reading block ${blockIndex}/${blockCountX * blockCountY}`);

      const blockLeft = blockX * BLOCK_SIZE;
      const blockTop = blockY * BLOCK_SIZE;
      const blockWidth = Math.min(BLOCK_SIZE, content.width - blockLeft);
      const blockHeight = Math.min(BLOCK_SIZE, content.height - blockTop);
      const blockBuffer = await sharp(imagePath, { limitInputPixels: false })
        .extract({ left: blockLeft, top: blockTop, width: blockWidth, height: blockHeight })
        .flatten({ background: { r: 0, g: 0, b: 0 } })
        .raw()
        .toBuffer();

      progress.show(`writing block ${blockIndex}/${blockCountX * blockCountY}`);

      for (const level of content.levels) {
        throwIfCanceled(progress);
        const scale = 1 << level.level;
        const resizedBlockWidth = Math.ceil(blockWidth / scale);
        const resizedBlockHeight = Math.ceil(blockHeight / scale);
        const resizedBuffer = level.level === 0
          ? blockBuffer
          : await sharp(blockBuffer, { raw: { width: blockWidth, height: blockHeight, channels: 3 } })
            .resize(resizedBlockWidth, resizedBlockHeight, { fit: "fill" })
            .raw()
            .toBuffer();

        const count = (BLOCK_SIZE / TILE_SIZE) >> level.level;
        const tileX1 = blockX * count;
        const tileY1 = blockY * count;
        const tileX2 = Math.min(tileX1 + count - 1, level.xMax - 1);
        const tileY2 = Math.min(tileY1 + count - 1, level.yMax - 1);
        const tasks: Array<() => Promise<void>> = [];

        for (let x = tileX1; x <= tileX2; x += 1) {
          for (let y = tileY1; y <= tileY2; y += 1) {
            tasks.push(async () => {
              throwIfCanceled(progress);
              const localLeft = (x - tileX1) * TILE_SIZE;
              const localTop = (y - tileY1) * TILE_SIZE;
              const extractWidth = Math.min(TILE_SIZE, Math.max(0, resizedBlockWidth - localLeft));
              const extractHeight = Math.min(TILE_SIZE, Math.max(0, resizedBlockHeight - localTop));
              let pipeline = sharp(resizedBuffer, {
                raw: { width: resizedBlockWidth, height: resizedBlockHeight, channels: 3 },
              }).extract({ left: localLeft, top: localTop, width: extractWidth, height: extractHeight });

              if (extractWidth < TILE_SIZE || extractHeight < TILE_SIZE) {
                pipeline = pipeline.extend({
                  right: TILE_SIZE - extractWidth,
                  bottom: TILE_SIZE - extractHeight,
                  background: { r: 0, g: 0, b: 0 },
                });
              }

              await pipeline
                .jpeg({ quality: 90 })
                .toFile(path.join(chipFolder, String(level.level), `${x}_${y}.jpg`));

              current += 1;
              progress.doneTile(level.level, x, y, current, total);
            });
          }
        }

        await runConcurrent(tasks, TILE_WRITE_CONCURRENCY, progress);
      }
    }
  }
}

async function runConcurrent(tasks: Array<() => Promise<void>>, concurrency: number, progress: CutProgress): Promise<void> {
  let next = 0;
  async function worker(): Promise<void> {
    while (next < tasks.length) {
      throwIfCanceled(progress);
      const task = tasks[next];
      next += 1;
      await task();
    }
  }
  const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, () => worker());
  await Promise.all(workers);
}

function throwIfCanceled(progress: CutProgress): void {
  if (progress.isCanceled?.()) {
    throw new Error("job canceled");
  }
}
