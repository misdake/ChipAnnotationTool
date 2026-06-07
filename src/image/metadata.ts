import sharp from "sharp";

export async function readImageSize(imagePath: string): Promise<{ width: number; height: number }> {
  const metadata = await sharp(imagePath, { limitInputPixels: false }).metadata();
  if (!metadata.width || !metadata.height) {
    throw new Error("cannot read image size");
  }
  return { width: metadata.width, height: metadata.height };
}

export function calculateDieDimensions(width: number, height: number, dieSize: number): { widthMillimeter: number; heightMillimeter: number } {
  if (!(width > 0 && height > 0)) throw new Error("invalid image size");
  if (!(dieSize > 0)) throw new Error("die size should be greater than 0");

  const ratio = width / height;
  return {
    widthMillimeter: formatDimension(Math.sqrt(dieSize * ratio)),
    heightMillimeter: formatDimension(Math.sqrt(dieSize / ratio)),
  };
}

function formatDimension(value: number): number {
  return Math.round(value * 10000) / 10000;
}
