import fs from "node:fs/promises";
import fsSync from "node:fs";
import path from "node:path";

const IDENTITY_FIELDS = ["vendor", "type", "family", "name"] as const;

export interface ViewState {
  repoNames: string[];
  repos: Map<string, RepoState>;
  records: Map<string, InternalRecord>;
}

interface RepoState {
  name: string;
  path: string;
}

interface InternalRecord {
  id: string;
  repo: string;
  directoryName: string;
  list?: Record<string, unknown>;
  content?: Record<string, unknown>;
  contentPath?: string;
}

export interface PublicRecord {
  id: string;
  repo: string;
  directoryName: string;
  list?: Record<string, unknown>;
  content?: Record<string, unknown>;
  issues: Array<{ code: string; label: string }>;
}

export async function loadViewState(projectRoot: string): Promise<ViewState> {
  const repoNames = (await fs.readFile(path.join(projectRoot, "repos.txt"), "utf8"))
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  const repos = new Map<string, RepoState>();
  const records = new Map<string, InternalRecord>();

  for (const repo of repoNames) {
    const repoPath = path.resolve(projectRoot, "..", repo);
    if (!fsSync.existsSync(repoPath)) {
      throw new Error(`Cannot read repo: ${repoPath}`);
    }
    repos.set(repo, { name: repo, path: repoPath });

    for (const entry of await fs.readdir(repoPath, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const contentPath = path.join(repoPath, entry.name, "content.json");
      if (!fsSync.existsSync(contentPath)) continue;
      const id = recordId(repo, entry.name);
      const record = records.get(id) || { id, repo, directoryName: entry.name };
      record.content = await readJson<Record<string, unknown>>(contentPath);
      record.contentPath = contentPath;
      record.list = listItemFromContent(record.content, repo);
      records.set(id, record);
    }
  }

  return { repoNames, repos, records };
}

export async function rebuildAggregateList(projectRoot: string, state: ViewState): Promise<string> {
  const byName = new Map<string, Record<string, unknown>>();
  for (const record of state.records.values()) {
    if (!record.content) continue;
    const item = listItemFromContent(record.content, record.repo);
    byName.set(String(item.name || ""), item);
  }
  const result = [...byName.values()].sort((a, b) => chipSortKey(a).localeCompare(chipSortKey(b)));
  const aggregateListPath = path.join(projectRoot, "list.json");
  await writeJsonAtomic(aggregateListPath, result);
  return aggregateListPath;
}

export function viewData(state: ViewState): { repos: string[]; records: PublicRecord[] } {
  const records = [...state.records.values()]
    .map(publicRecord)
    .sort((a, b) => {
      const left = `${String(a.list?.vendor || a.content?.vendor || "")}-${String(a.list?.name || a.content?.name || a.directoryName)}`;
      const right = `${String(b.list?.vendor || b.content?.vendor || "")}-${String(b.list?.name || b.content?.name || b.directoryName)}`;
      return left.localeCompare(right);
    });
  return { repos: state.repoNames, records };
}

export async function saveViewRecord(projectRoot: string, state: ViewState, id: string, listValue: unknown, contentValue: unknown): Promise<PublicRecord> {
  const record = state.records.get(id);
  if (!record) throw new Error("chip record not found");
  const repo = state.repos.get(record.repo);
  if (!repo) throw new Error("repo not found");

  if (contentValue !== undefined) {
    if (!isPlainObject(contentValue)) throw new Error("content should be an object");
    const listObject = isPlainObject(listValue) ? listValue : {};
    const nextContent = { ...contentValue };
    for (const field of [...IDENTITY_FIELDS, "listname"] as const) {
      if (listObject[field] !== undefined) nextContent[field] = listObject[field];
    }
    if (record.content && Object.prototype.hasOwnProperty.call(record.content, "name")) {
      nextContent.name = record.content.name;
    }
    const directoryPath = path.join(repo.path, record.directoryName);
    const resolvedDirectory = path.resolve(directoryPath);
    if (path.dirname(resolvedDirectory) !== repo.path) throw new Error("invalid directory path");
    await fs.mkdir(resolvedDirectory, { recursive: true });
    record.contentPath = path.join(resolvedDirectory, "content.json");
    record.content = nextContent;
    record.list = listItemFromContent(nextContent, record.repo);
    await writeJsonAtomic(record.contentPath, nextContent);
  }

  await rebuildAggregateList(projectRoot, state);
  return publicRecord(record);
}

function publicRecord(record: InternalRecord): PublicRecord {
  return {
    id: record.id,
    repo: record.repo,
    directoryName: record.directoryName,
    list: record.list,
    content: record.content,
    issues: issuesFor(record),
  };
}

function issuesFor(record: InternalRecord): Array<{ code: string; label: string }> {
  const issues: Array<{ code: string; label: string }> = [];
  if (!record.content) issues.push({ code: "missing-content", label: "missing content" });
  if (record.list && record.content) {
    for (const field of IDENTITY_FIELDS) {
      if ((record.list[field] ?? "") !== (record.content[field] ?? "")) {
        issues.push({ code: `mismatch-${field}`, label: `${field} mismatch` });
      }
    }
  }
  return issues;
}

function recordId(repo: string, directoryName: string): string {
  return `${repo}::${directoryName}`;
}

function listItemFromContent(content: Record<string, unknown>, repo: string): Record<string, unknown> {
  const name = String(content.name || "");
  const item: Record<string, unknown> = {
    vendor: String(content.vendor || ""),
    type: String(content.type || ""),
    family: String(content.family || ""),
    name,
    url: repo === "ChipAnnotationDataCdn"
      ? `https://chip.rgbuv.xyz/chip/${name}`
      : `https://misdake.github.io/${repo}/${name}`,
  };
  if (typeof content.listname === "string" && content.listname.trim()) {
    item.listname = content.listname;
  }
  return item;
}

function chipSortKey(item: Record<string, unknown>): string {
  return `${String(item.vendor || "")}-${String(item.type || "")}-${String(item.family || "")}-${String(item.name || "")}`;
}

async function readJson<T>(filePath: string): Promise<T> {
  return JSON.parse(await fs.readFile(filePath, "utf8")) as T;
}

async function writeJsonAtomic(filePath: string, value: unknown): Promise<void> {
  const tempPath = `${filePath}.tmp`;
  await fs.writeFile(tempPath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
  await fs.rename(tempPath, filePath);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
