const state = {
  imageId: "",
  imageWidth: 0,
  imageHeight: 0,
  debugOutputRoot: "",
  releaseDataFolder: "",
  jobId: "",
  eventSource: null,
  authors: new Map(),
  chipNames: new Map(),
};

const el = {
  imageFile: document.querySelector("#image-file"),
  uploadButton: document.querySelector("#upload-button"),
  cleanupTempButton: document.querySelector("#cleanup-temp-button"),
  imageSummary: document.querySelector("#image-summary"),
  uploadProgress: document.querySelector("#upload-progress"),
  uploadStatus: document.querySelector("#upload-status"),
  modeBadge: document.querySelector("#mode-badge"),
  releaseConfirmRow: document.querySelector("#release-confirm-row"),
  releaseConfirm: document.querySelector("#release-confirm"),
  calculateDieSize: document.querySelector("#calculate-die-size"),
  dieSize: document.querySelector("#die-size"),
  startButton: document.querySelector("#start-button"),
  stopButton: document.querySelector("#stop-button"),
  runStatus: document.querySelector("#run-status"),
  runProgress: document.querySelector("#run-progress"),
  runProgressText: document.querySelector("#run-progress-text"),
  result: document.querySelector("#result"),
  authorOptions: document.querySelector("#author-options"),
  imageAuthorName: document.querySelector("#imageAuthorName"),
  imageAuthorUrl: document.querySelector("#imageAuthorUrl"),
  source: document.querySelector("#source"),
  name: document.querySelector("#name"),
  nameWarning: document.querySelector("#name-warning"),
};

const fields = [
  "vendor",
  "type",
  "family",
  "name",
  "listname",
  "source",
  "imageAuthorName",
  "imageAuthorUrl",
  "specUrl",
  "githubRepo",
  "githubIssueId",
  "widthMillimeter",
  "heightMillimeter",
];

function field(id) {
  return document.querySelector(`#${id}`);
}

function selectedWriteMode() {
  return document.querySelector('input[name="writeMode"]:checked').value;
}

function setBusy(busy) {
  el.uploadButton.disabled = busy;
  el.cleanupTempButton.disabled = busy;
  el.startButton.disabled = busy;
  el.stopButton.disabled = !busy;
}

function setStatus(text, error = false) {
  el.runStatus.textContent = text;
  el.runStatus.style.color = error ? "#b42318" : "";
}

function metadata() {
  const result = {};
  for (const id of fields) {
    const input = field(id);
    result[id] = input.type === "number" ? Number(input.value || 0) : input.value.trim();
  }
  return result;
}

function applyInferred(data) {
  state.imageId = data.imageId;
  state.imageWidth = data.width;
  state.imageHeight = data.height;
  el.imageSummary.textContent = `${data.originalName}, ${data.width} x ${data.height}px`;
}

function uploadImage() {
  const file = el.imageFile.files?.[0];
  if (!file) {
    el.uploadStatus.textContent = "Select an image first";
    return;
  }

  const form = new FormData();
  form.append("image", file);

  const xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/images/upload");
  el.cleanupTempButton.disabled = true;
  xhr.upload.onprogress = (event) => {
    if (!event.lengthComputable) return;
    el.uploadProgress.value = event.loaded / event.total;
    el.uploadStatus.textContent = `Uploading ${Math.round((event.loaded / event.total) * 100)}%`;
  };
  xhr.onload = () => {
    try {
      const body = JSON.parse(xhr.responseText);
      if (xhr.status >= 400) throw new Error(body.error || `HTTP ${xhr.status}`);
      applyInferred(body);
      el.uploadProgress.value = 1;
      el.uploadStatus.textContent = "Upload complete";
    } catch (error) {
      el.uploadStatus.textContent = error.message;
    } finally {
      el.cleanupTempButton.disabled = false;
    }
  };
  xhr.onerror = () => {
    el.uploadStatus.textContent = "Upload failed";
    el.cleanupTempButton.disabled = false;
  };
  xhr.send(form);
}

async function calculateDieSize() {
  if (!state.imageId) {
    setStatus("Upload an image before calculating die size", true);
    return;
  }
  const dieSize = Number(el.dieSize.value);
  const response = await fetch("/api/calculate-die-size", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ imageId: state.imageId, dieSize }),
  });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
  field("widthMillimeter").value = body.widthMillimeter;
  field("heightMillimeter").value = body.heightMillimeter;
}

async function startCutting() {
  if (!state.imageId) {
    setStatus("Upload an image first", true);
    return;
  }
  if (checkChipNameDuplicate()) {
    setStatus("Chip name already exists; choose a different name", true);
    return;
  }

  const writeMode = selectedWriteMode();
  const releaseConfirmed = el.releaseConfirm.checked;
  if (writeMode === "release" && !releaseConfirmed) {
    setStatus("Release mode requires confirmation", true);
    return;
  }

  setBusy(true);
  setStatus("Starting");
  el.result.textContent = "";
  el.runProgress.value = 0;
  el.runProgressText.textContent = "0%";

  const response = await fetch("/api/chips/process", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      imageId: state.imageId,
      writeMode,
      releaseConfirmed,
      metadata: metadata(),
    }),
  });
  const body = await response.json();
  if (!response.ok) {
    setBusy(false);
    throw new Error(body.error || `HTTP ${response.status}`);
  }

  subscribeJob(body.jobId);
}

function subscribeJob(jobId) {
  state.jobId = jobId;
  if (state.eventSource) state.eventSource.close();
  const source = new EventSource(`/api/jobs/${encodeURIComponent(jobId)}/events`);
  state.eventSource = source;

  source.addEventListener("snapshot", (event) => updateJob(JSON.parse(event.data)));
  source.addEventListener("progress", (event) => updateJob(JSON.parse(event.data)));
  source.addEventListener("done", (event) => {
    updateJob(JSON.parse(event.data));
    state.jobId = "";
    setBusy(false);
    source.close();
  });
  source.addEventListener("canceled", (event) => {
    updateJob(JSON.parse(event.data));
    state.jobId = "";
    setBusy(false);
    source.close();
  });
  source.addEventListener("error", (event) => {
    if (!event.data) return;
    updateJob(JSON.parse(event.data));
    state.jobId = "";
    setBusy(false);
    source.close();
  });
}

async function stopCutting() {
  if (!state.jobId) return;
  el.stopButton.disabled = true;
  setStatus("Cancel requested");
  const response = await fetch(`/api/jobs/${encodeURIComponent(state.jobId)}/cancel`, { method: "POST" });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
}

async function cleanupTemp() {
  if (!confirm("Clean files under .tmp, including uploaded images and debug outputs?")) return;
  el.cleanupTempButton.disabled = true;
  el.uploadStatus.textContent = "Cleaning temp files";
  const response = await fetch("/api/cleanup-temp", { method: "POST" });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);

  state.imageId = "";
  state.imageWidth = 0;
  state.imageHeight = 0;
  el.imageFile.value = "";
  el.imageSummary.textContent = "";
  el.uploadProgress.value = 0;
  el.uploadStatus.textContent = body.skipped?.length
    ? `Temp cleanup finished; skipped ${body.skipped.length} item(s)`
    : "Temp cleanup complete";
  if (body.skipped?.length) {
    el.result.textContent = body.skipped.map((item) => `skipped: ${item.path} (${item.error})`).join("\n");
  }
  el.cleanupTempButton.disabled = false;
}

function updateJob(job) {
  el.runProgress.value = job.progress || 0;
  el.runProgressText.textContent = `${Math.round((job.progress || 0) * 100)}%`;
  setStatus(job.message || job.state, job.state === "error");
  el.result.textContent = [
    `jobId: ${job.id}`,
    `state: ${job.state}`,
    `current: ${job.current || 0}/${job.total || 0}`,
    job.outputFolder ? `outputFolder: ${job.outputFolder}` : "",
    job.contentPath ? `content.json: ${job.contentPath}` : "",
    job.listPath ? `list.json: ${job.listPath}` : "",
    job.error ? `error: ${job.error}` : "",
  ].filter(Boolean).join("\n");
}

function updateModeUi() {
  const mode = selectedWriteMode();
  el.modeBadge.textContent = mode === "release" ? "RELEASE WRITE" : "DEBUG WRITE";
  el.modeBadge.classList.toggle("release", mode === "release");
  el.modeBadge.classList.toggle("debug", mode !== "release");
  el.releaseConfirmRow.classList.toggle("hidden", mode !== "release");
  if (mode !== "release") el.releaseConfirm.checked = false;
}

async function loadConfig() {
  const response = await fetch("/api/config");
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
  state.debugOutputRoot = body.debugOutputRoot || "";
  state.releaseDataFolder = body.releaseDataFolder || "";
  setAuthorOptions(body.authors || []);
  setChipNames(body.chipNames || []);
  updateModeUi();
}

function setAuthorOptions(authors) {
  state.authors = new Map();
  el.authorOptions.textContent = "";
  for (const author of authors) {
    if (!author.name || !author.url) continue;
    state.authors.set(author.name, author.url);
    const option = document.createElement("option");
    option.value = author.name;
    option.label = author.url;
    el.authorOptions.appendChild(option);
  }
}

function fillAuthorUrl() {
  const name = el.imageAuthorName.value.trim();
  const url = state.authors.get(name);
  if (url) el.imageAuthorUrl.value = url;
}

function fillAuthorFromSource() {
  const source = el.source.value.trim();
  if (!source) return;
  for (const [name, url] of state.authors) {
    if (!url || !source.startsWith(url)) continue;
    el.imageAuthorName.value = name;
    el.imageAuthorUrl.value = url;
    return;
  }
}

function setChipNames(chipNames) {
  state.chipNames = new Map();
  for (const chip of chipNames) {
    if (!chip.name) continue;
    state.chipNames.set(chip.name, chip);
  }
  checkChipNameDuplicate();
}

function checkChipNameDuplicate() {
  const name = el.name.value.trim();
  const duplicate = name ? state.chipNames.get(name) : undefined;
  el.name.classList.toggle("invalid", Boolean(duplicate));
  el.nameWarning.classList.toggle("hidden", !duplicate);
  el.nameWarning.textContent = duplicate
    ? `Name already exists in ${duplicate.repo}/${duplicate.directoryName}`
    : "";
  return Boolean(duplicate);
}

el.uploadButton.addEventListener("click", uploadImage);
el.cleanupTempButton.addEventListener("click", () => {
  cleanupTemp().catch((error) => {
    el.cleanupTempButton.disabled = false;
    el.uploadStatus.textContent = error.message;
  });
});
el.calculateDieSize.addEventListener("click", () => {
  calculateDieSize().catch((error) => setStatus(error.message, true));
});
el.startButton.addEventListener("click", () => {
  startCutting().catch((error) => {
    setBusy(false);
    setStatus(error.message, true);
  });
});
el.stopButton.addEventListener("click", () => {
  stopCutting().catch((error) => setStatus(error.message, true));
});
document.querySelectorAll('input[name="writeMode"]').forEach((input) => {
  input.addEventListener("change", updateModeUi);
});
el.imageAuthorName.addEventListener("input", fillAuthorUrl);
el.imageAuthorName.addEventListener("change", fillAuthorUrl);
el.source.addEventListener("change", fillAuthorFromSource);
el.name.addEventListener("input", checkChipNameDuplicate);

loadConfig().catch((error) => setStatus(error.message, true));
