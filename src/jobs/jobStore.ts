import { randomUUID } from "node:crypto";
import type { Response } from "express";

export type JobState = "running" | "done" | "error" | "canceled";

export interface JobSnapshot {
  id: string;
  state: JobState;
  message: string;
  progress: number;
  current: number;
  total: number;
  outputFolder?: string;
  contentPath?: string;
  listPath?: string;
  error?: string;
  canceled: boolean;
}

export interface Job extends JobSnapshot {
  clients: Set<Response>;
}

const jobs = new Map<string, Job>();

export function createJob(): Job {
  const id = randomUUID();
  const job: Job = {
    id,
    state: "running",
    message: "created",
    progress: 0,
    current: 0,
    total: 0,
    canceled: false,
    clients: new Set(),
  };
  jobs.set(id, job);
  return job;
}

export function getJob(id: string): Job | undefined {
  return jobs.get(id);
}

export function attachClient(job: Job, response: Response): void {
  job.clients.add(response);
  send(response, "snapshot", snapshot(job));
  response.on("close", () => job.clients.delete(response));
}

export function updateJob(job: Job, patch: Partial<Omit<JobSnapshot, "id">>): void {
  Object.assign(job, patch);
  broadcast(job, "progress", snapshot(job));
}

export function completeJob(job: Job, patch: Partial<Omit<JobSnapshot, "id" | "state">>): void {
  Object.assign(job, patch, { state: "done", progress: 1 });
  broadcast(job, "done", snapshot(job));
  closeClients(job);
}

export function cancelJob(job: Job): void {
  if (job.state !== "running") return;
  Object.assign(job, { canceled: true, message: "cancel requested" });
  broadcast(job, "progress", snapshot(job));
}

export function markJobCanceled(job: Job): void {
  Object.assign(job, { state: "canceled", message: "canceled" });
  broadcast(job, "canceled", snapshot(job));
  closeClients(job);
}

export function failJob(job: Job, error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  Object.assign(job, { state: "error", error: message, message, progress: 0 });
  broadcast(job, "error", snapshot(job));
  closeClients(job);
}

function snapshot(job: Job): JobSnapshot {
  const { clients: _clients, ...rest } = job;
  return rest;
}

function broadcast(job: Job, event: string, payload: JobSnapshot): void {
  for (const client of job.clients) send(client, event, payload);
}

function send(response: Response, event: string, payload: JobSnapshot): void {
  response.write(`event: ${event}\n`);
  response.write(`data: ${JSON.stringify(payload)}\n\n`);
}

function closeClients(job: Job): void {
  for (const client of job.clients) client.end();
  job.clients.clear();
}
