import Busboy from "busboy";
import express from "express";
import fsp from "node:fs/promises";
import { createWriteStream } from "node:fs";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import type { Request, Response } from "express";
import type { ChipMetadata, ImageContent, UploadedImage, WriteMode } from "./types.js";
import { readImageSize, calculateDieDimensions } from "./image/metadata.js";
import { buildLevels } from "./image/preprocess.js";
import { cutAll } from "./image/cutter.js";
import { loadViewState, rebuildAggregateList, saveViewRecord, viewData, type ViewState } from "./data/repoView.js";
import { attachClient, cancelJob, completeJob, createJob, failJob, getJob, markJobCanceled, updateJob } from "./jobs/jobStore.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");
const webRoot = path.join(projectRoot, "src", "web");
const tmpRoot = path.join(projectRoot, ".tmp");
const uploadRoot = path.join(tmpRoot, "uploads");
const outputRoot = path.join(tmpRoot, "output");
const releaseDataFolder = path.resolve(projectRoot, "..", "ChipAnnotationDataCdn");
const MAX_UPLOAD_BYTES = 200 * 1024 * 1024;
const PORT = Number(process.env.PORT || 3000);

const images = new Map<string, UploadedImage>();
let viewState: ViewState;
let authorOptions: AuthorOption[] = [];
let chipNameOptions: ChipNameOption[] = [];

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use(express.static(webRoot));

app.get("/api/config", (_request, response) => {
  response.json({
    debugOutputRoot: outputRoot,
    releaseDataFolder,
    authors: authorOptions,
    chipNames: chipNameOptions,
  });
});
app.post("/api/images/upload", uploadImage);
app.post("/api/calculate-die-size", calculateDieSize);
app.post("/api/chips/process", processChip);
app.post("/api/jobs/:jobId/cancel", cancelJobRequest);
app.get("/api/jobs/:jobId/events", jobEvents);
app.post("/api/cleanup-temp", cleanupTempRequest);
app.get("/api/data", viewDataRequest);
app.post("/api/reload", reloadViewRequest);
app.put("/api/chips", saveViewChipRequest);

await fsp.mkdir(uploadRoot, { recursive: true });
await fsp.mkdir(outputRoot, { recursive: true });
viewState = await loadViewState(projectRoot);
refreshAuthorOptions(true);
refreshChipNameOptions();

app.listen(PORT, () => {
  console.log(`ChipAnnotationTool2 running at http://localhost:${PORT}`);
  console.log(`Loaded ${viewState.records.size} chips from ${viewState.repoNames.length} repositories.`);
});

interface AuthorOption {
  name: string;
  url: string;
}

interface ChipNameOption {
  name: string;
  repo: string;
  directoryName: string;
}

function uploadImage(request: Request, response: Response): void {
  const contentType = request.headers["content-type"] || "";
  if (!contentType.includes("multipart/form-data")) {
    response.status(400).json({ error: "multipart/form-data is required" });
    return;
  }

  const busboy = Busboy({
    headers: request.headers,
    limits: { files: 1, fileSize: MAX_UPLOAD_BYTES },
  });

  let uploadPromise: Promise<UploadedImage> | undefined;
  let rejected = false;

  busboy.on("file", (_field, stream, info) => {
    const originalName = info.filename || "image";
    const extension = path.extname(originalName) || ".img";
    const id = randomUUID();
    const tempPath = path.join(uploadRoot, `${id}${extension}`);
    const output = createWriteStream(tempPath);

    stream.on("limit", () => {
      rejected = true;
      stream.unpipe(output);
      output.destroy(new Error("image exceeds 200MB limit"));
      void fsp.rm(tempPath, { force: true });
    });

    uploadPromise = new Promise<UploadedImage>((resolve, reject) => {
      output.on("error", reject);
      stream.on("error", reject);
      output.on("finish", async () => {
        try {
          if (rejected) throw new Error("image exceeds 200MB limit");
          const size = await readImageSize(tempPath);
          const image: UploadedImage = { id, path: tempPath, originalName, ...size };
          images.set(id, image);
          resolve(image);
        } catch (error) {
          await fsp.rm(tempPath, { force: true });
          reject(error);
        }
      });
    });

    stream.pipe(output);
  });

  busboy.on("error", (error: unknown) => response.status(400).json({ error: errorMessage(error) }));
  busboy.on("finish", async () => {
    try {
      if (!uploadPromise) throw new Error("image file is required");
      const image = await uploadPromise;
      response.json({
        imageId: image.id,
        originalName: image.originalName,
        width: image.width,
        height: image.height,
      });
    } catch (error) {
      response.status(400).json({ error: error instanceof Error ? error.message : String(error) });
    }
  });

  request.pipe(busboy);
}

function calculateDieSize(request: Request, response: Response): void {
  const image = images.get(String(request.body.imageId || ""));
  const dieSize = Number(request.body.dieSize);
  if (!image) {
    response.status(404).json({ error: "image not found" });
    return;
  }
  try {
    response.json(calculateDieDimensions(image.width, image.height, dieSize));
  } catch (error) {
    response.status(400).json({ error: error instanceof Error ? error.message : String(error) });
  }
}

function processChip(request: Request, response: Response): void {
  const image = images.get(String(request.body.imageId || ""));
  const writeMode = request.body.writeMode === "release" ? "release" : "debug";
  const releaseConfirmed = request.body.releaseConfirmed === true;
  const metadata = normalizeMetadata(request.body.metadata || {});

  if (!image) {
    response.status(404).json({ error: "image not found" });
    return;
  }
  if (!metadata.name) {
    response.status(400).json({ error: "chip name is required" });
    return;
  }
  if (chipNameOptions.some((chip) => chip.name === metadata.name)) {
    response.status(400).json({ error: `chip name already exists: ${metadata.name}` });
    return;
  }
  if (!metadata.githubRepo.includes("/")) {
    response.status(400).json({ error: "githubRepo should be 'username/reponame'" });
    return;
  }
  if (writeMode === "release" && !releaseConfirmed) {
    response.status(400).json({ error: "release mode requires confirmation" });
    return;
  }

  const job = createJob();
  response.json({ jobId: job.id });

  void runProcessJob(job.id, image, writeMode, metadata);
}

function jobEvents(request: Request, response: Response): void {
  const job = getJob(String(request.params.jobId));
  if (!job) {
    response.status(404).json({ error: "job not found" });
    return;
  }

  response.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });
  attachClient(job, response);
}

function viewDataRequest(_request: Request, response: Response): void {
  response.json(viewData(viewState));
}

async function reloadViewRequest(_request: Request, response: Response): Promise<void> {
  viewState = await loadViewState(projectRoot);
  refreshAuthorOptions();
  refreshChipNameOptions();
  response.json(viewData(viewState));
}

async function saveViewChipRequest(request: Request, response: Response): Promise<void> {
  const record = await saveViewRecord(projectRoot, viewState, String(request.body.id || ""), request.body.list, request.body.content);
  refreshAuthorOptions();
  refreshChipNameOptions();
  response.json(record);
}

function cancelJobRequest(request: Request, response: Response): void {
  const job = getJob(String(request.params.jobId));
  if (!job) {
    response.status(404).json({ error: "job not found" });
    return;
  }
  cancelJob(job);
  response.json({ ok: true });
}

async function cleanupTempRequest(_request: Request, response: Response): Promise<void> {
  const skipped: Array<{ path: string; error: string }> = [];
  const activeUploads = new Set([...images.values()].map((image) => path.resolve(image.path)));

  await fsp.mkdir(tmpRoot, { recursive: true });
  const entries = await fsp.readdir(tmpRoot, { withFileTypes: true });
  for (const entry of entries) {
    const entryPath = path.join(tmpRoot, entry.name);
    try {
      if (path.resolve(entryPath) === path.resolve(uploadRoot)) {
        await cleanDirectoryContents(uploadRoot, activeUploads, skipped);
      } else {
        await fsp.rm(entryPath, { recursive: true, force: true });
      }
    } catch (error) {
      skipped.push({ path: entryPath, error: errorMessage(error) });
    }
  }

  await fsp.mkdir(uploadRoot, { recursive: true });
  await fsp.mkdir(outputRoot, { recursive: true });
  for (const [id, image] of images) {
    try {
      await fsp.access(image.path);
    } catch {
      images.delete(id);
    }
  }

  response.json({ ok: true, tmpRoot, skipped });
}

async function cleanDirectoryContents(directoryPath: string, skipFiles: Set<string>, skipped: Array<{ path: string; error: string }>): Promise<void> {
  await fsp.mkdir(directoryPath, { recursive: true });
  for (const entry of await fsp.readdir(directoryPath, { withFileTypes: true })) {
    const entryPath = path.join(directoryPath, entry.name);
    const resolvedEntryPath = path.resolve(entryPath);
    if (skipFiles.has(resolvedEntryPath)) {
      skipped.push({ path: entryPath, error: "active upload image" });
      continue;
    }
    try {
      await fsp.rm(entryPath, { recursive: true, force: true });
    } catch (error) {
      skipped.push({ path: entryPath, error: errorMessage(error) });
    }
  }
}

async function runProcessJob(jobId: string, image: UploadedImage, writeMode: WriteMode, metadata: ChipMetadata): Promise<void> {
  const job = getJob(jobId);
  if (!job) return;

  try {
    const content = buildContent(image, metadata);

    const baseOutputFolder = writeMode === "release" ? releaseDataFolder : path.join(outputRoot, jobId);
    const finalListPath = path.join(projectRoot, "list.json");
    const chipFolder = path.join(baseOutputFolder, metadata.name);
    const contentPath = path.join(chipFolder, "content.json");
    const listPath = writeMode === "release" ? finalListPath : undefined;

    await fsp.mkdir(chipFolder, { recursive: true });

    updateJob(job, {
      message: `cutting image (${writeMode})`,
      outputFolder: baseOutputFolder,
      contentPath,
      listPath,
    });

    await cutAll(image.path, content, chipFolder, {
      show(message) {
        updateJob(job, { message });
      },
      doneTile(level, x, y, current, total) {
        updateJob(job, {
          message: `wrote level ${level} tile ${x}_${y}`,
          current,
          total,
          progress: total > 0 ? current / total : 1,
        });
      },
      isCanceled() {
        return job.canceled;
      },
    });

    if (job.canceled) {
      markJobCanceled(job);
      return;
    }

    updateJob(job, { message: "saving content.json" });
    await fsp.writeFile(contentPath, `${JSON.stringify(content, null, 2)}\n`, "utf8");

    if (writeMode === "release") {
      updateJob(job, { message: "rebuilding final list.json" });
      await backupFinalList(finalListPath);
      viewState = await loadViewState(projectRoot);
      refreshAuthorOptions();
      refreshChipNameOptions();
      await rebuildAggregateList(projectRoot, viewState);
    }

    completeJob(job, {
      message: "all done",
      current: job.total,
      total: job.total,
      outputFolder: baseOutputFolder,
      contentPath,
      listPath,
    });
  } catch (error) {
    if (job.canceled || errorMessage(error) === "job canceled") {
      markJobCanceled(job);
      return;
    }
    failJob(job, error);
  } finally {
    await fsp.rm(image.path, { force: true }).catch(() => undefined);
    images.delete(image.id);
  }
}

function buildContent(image: UploadedImage, metadata: ChipMetadata): ImageContent {
  const preprocess = buildLevels(image.width, image.height);
  return {
    vendor: metadata.vendor,
    type: metadata.type,
    family: metadata.family,
    name: metadata.name,
    ...(metadata.listname ? { listname: metadata.listname } : {}),
    githubRepo: metadata.githubRepo,
    githubIssueId: metadata.githubIssueId,
    source: metadata.source,
    imageAuthorName: metadata.imageAuthorName,
    imageAuthorUrl: metadata.imageAuthorUrl,
    specUrl: metadata.specUrl,
    createTime: new Date().toISOString(),
    width: image.width,
    height: image.height,
    ...preprocess,
    widthMillimeter: metadata.widthMillimeter,
    heightMillimeter: metadata.heightMillimeter,
  };
}

function normalizeMetadata(input: Record<string, unknown>): ChipMetadata {
  return {
    vendor: text(input.vendor),
    type: text(input.type),
    family: text(input.family),
    name: text(input.name),
    listname: text(input.listname),
    source: text(input.source),
    imageAuthorName: text(input.imageAuthorName),
    imageAuthorUrl: text(input.imageAuthorUrl),
    specUrl: text(input.specUrl),
    githubRepo: text(input.githubRepo) || "misdake/ChipAnnotationData",
    githubIssueId: number(input.githubIssueId),
    widthMillimeter: number(input.widthMillimeter),
    heightMillimeter: number(input.heightMillimeter),
  };
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function number(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

async function backupFinalList(listPath: string): Promise<string | undefined> {
  try {
    await fsp.access(listPath);
  } catch {
    return undefined;
  }
  const parsed = path.parse(listPath);
  const backupPath = path.join(parsed.dir, `${parsed.name}.${timestampForFilename()}${parsed.ext}`);
  await fsp.copyFile(listPath, backupPath);
  return backupPath;
}

function timestampForFilename(): string {
  const now = new Date();
  const pad = (value: number, size = 2) => String(value).padStart(size, "0");
  return [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    "-",
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds()),
    "-",
    pad(now.getMilliseconds(), 3),
  ].join("");
}

function refreshAuthorOptions(reportConflicts = false): void {
  const { options, conflicts } = buildAuthorOptions(viewState);
  authorOptions = options;
  if (reportConflicts) reportAuthorConflicts(conflicts);
}

function refreshChipNameOptions(): void {
  chipNameOptions = [...viewState.records.values()]
    .map((record) => ({
      name: text(record.content?.name),
      repo: record.repo,
      directoryName: record.directoryName,
    }))
    .filter((chip) => Boolean(chip.name))
    .sort((a, b) => a.name.localeCompare(b.name));
}

function buildAuthorOptions(state: ViewState): { options: AuthorOption[]; conflicts: Array<{ name: string; urls: string[] }> } {
  const byName = new Map<string, Set<string>>();
  for (const record of state.records.values()) {
    const content = record.content;
    if (!content) continue;
    const name = text(content.imageAuthorName);
    const url = text(content.imageAuthorUrl);
    if (!name || !url) continue;
    const urls = byName.get(name) || new Set<string>();
    urls.add(url);
    byName.set(name, urls);
  }

  const options: AuthorOption[] = [];
  const conflicts: Array<{ name: string; urls: string[] }> = [];
  for (const [name, urls] of byName) {
    const sortedUrls = [...urls].sort((a, b) => a.localeCompare(b));
    options.push({ name, url: sortedUrls[0] || "" });
    if (sortedUrls.length > 1) conflicts.push({ name, urls: sortedUrls });
  }
  options.sort((a, b) => a.name.localeCompare(b.name));
  conflicts.sort((a, b) => a.name.localeCompare(b.name));
  return { options, conflicts };
}

function reportAuthorConflicts(conflicts: Array<{ name: string; urls: string[] }>): void {
  for (const conflict of conflicts) {
    console.warn(`Image author "${conflict.name}" has multiple URLs; using "${conflict.urls[0]}".`);
    for (const url of conflict.urls) console.warn(`  - ${url}`);
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
