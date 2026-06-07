import type { ImageContent, Level } from "../types.js";

export const TILE_SIZE = 512;
export const BLOCK_LEVEL = 5;

export function buildLevels(width: number, height: number): Pick<ImageContent, "tileSize" | "maxLevel" | "levels"> {
  const levels: Level[] = [];
  let currentLevel = 0;
  let currentWidth = width;
  let currentHeight = height;

  while ((currentWidth > TILE_SIZE / 2 || currentHeight > TILE_SIZE / 2) && currentLevel <= BLOCK_LEVEL) {
    levels.push({
      level: currentLevel,
      xMax: Math.floor((width - 1) / (TILE_SIZE << currentLevel)) + 1,
      yMax: Math.floor((height - 1) / (TILE_SIZE << currentLevel)) + 1,
    });

    currentLevel += 1;
    currentWidth = Math.floor((currentWidth + 1) / 2);
    currentHeight = Math.floor((currentHeight + 1) / 2);
  }

  return {
    tileSize: TILE_SIZE,
    maxLevel: currentLevel - 1,
    levels,
  };
}
