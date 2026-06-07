const listFieldDefinitions = [
  ["vendor", "Vendor"],
  ["type", "Type"],
  ["family", "Family"],
  ["name", "Name"],
  ["listname", "List name", "optional"],
  ["url", "URL", "wide readonly"],
];

const contentFieldDefinitions = [
  ["source", "Source", "wide"],
  ["imageAuthorName", "Image author name", "optional"],
  ["imageAuthorUrl", "Image author URL", "wide optional"],
  ["specUrl", "Spec URL", "wide optional"],
  ["githubRepo", "GitHub repo"],
  ["githubIssueId", "GitHub issue ID", "number"],
  ["vendor", "Vendor"],
  ["type", "Type"],
  ["family", "Family"],
  ["name", "Name", "readonly"],
  ["widthMillimeter", "Width (mm)", "number"],
  ["heightMillimeter", "Height (mm)", "number"],
];

const identityFields = ["vendor", "type", "family", "name"];
const elements = {
  summary: document.querySelector("#summary"),
  rows: document.querySelector("#chip-rows"),
  empty: document.querySelector("#empty-state"),
  search: document.querySelector("#search-filter"),
  repoFilter: document.querySelector("#repo-filter"),
  statusFilter: document.querySelector("#status-filter"),
  dieSizeFilter: document.querySelector("#die-size-filter"),
  metadataFilter: document.querySelector("#metadata-filter"),
  reload: document.querySelector("#reload-button"),
  dialog: document.querySelector("#editor"),
  form: document.querySelector("#editor-form"),
  title: document.querySelector("#editor-title"),
  subtitle: document.querySelector("#editor-subtitle"),
  listFields: document.querySelector("#list-fields"),
  contentFields: document.querySelector("#content-fields"),
  listExistence: document.querySelector("#list-existence"),
  contentExistence: document.querySelector("#content-existence"),
  issues: document.querySelector("#editor-issues"),
  saveMessage: document.querySelector("#save-message"),
  saveButton: document.querySelector("#save-button"),
  calculateDieSize: document.querySelector("#calculate-die-size"),
  copyContentToList: document.querySelector("#copy-content-to-list"),
  copyListToContent: document.querySelector("#copy-list-to-content"),
  closeConfirm: document.querySelector("#close-confirm"),
  confirmKeepEditing: document.querySelector("#confirm-keep-editing"),
  confirmDiscard: document.querySelector("#confirm-discard"),
  confirmSave: document.querySelector("#confirm-save"),
  dieSizeDialog: document.querySelector("#die-size-dialog"),
  dieSizeForm: document.querySelector("#die-size-form"),
  dieSizeRatio: document.querySelector("#die-size-ratio"),
  dieSizeValue: document.querySelector("#die-size-value"),
  dieSizePreview: document.querySelector("#die-size-preview"),
  dieSizeCancel: document.querySelector("#die-size-cancel"),
};

let data = { repos: [], records: [] };
let activeRecord = null;
let initialEditorState = "";
let saveInProgress = false;

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`);
  return body;
}

function issueType(record, type) {
  if (type === "issues") return record.issues.length > 0;
  if (type === "ok") return record.issues.length === 0;
  if (type === "missing") return record.issues.some((issue) => issue.code.startsWith("missing-"));
  if (type === "mismatch") return record.issues.some((issue) => issue.code.startsWith("mismatch-"));
  return true;
}

function pairHtml(listValue, contentValue) {
  const equal = (listValue ?? "") === (contentValue ?? "");
  if (equal) return `<span class="pair">${escapeHtml(listValue || "—")}</span>`;
  return `<span class="pair pair-mismatch">${escapeHtml(listValue || "—")} / ${escapeHtml(contentValue || "—")}</span>`;
}

function dieSizeHtml(content) {
  const width = Number(content?.widthMillimeter);
  const height = Number(content?.heightMillimeter);
  if (!(width > 0 && height > 0)) return '<span class="status missing">未填写</span>';
  const area = Number((width * height).toFixed(2));
  return `
    <span class="status">已填写</span>
    <span class="die-size-value">${escapeHtml(area)} mm²<br>${escapeHtml(width)} × ${escapeHtml(height)} mm</span>`;
}

function hasDieSize(content) {
  return Number(content?.widthMillimeter) > 0 && Number(content?.heightMillimeter) > 0;
}

function metadataState(content) {
  const spec = Boolean(String(content?.specUrl ?? "").trim());
  const authorName = Boolean(String(content?.imageAuthorName ?? "").trim());
  const authorUrl = Boolean(String(content?.imageAuthorUrl ?? "").trim());
  return {
    spec,
    author: authorName && authorUrl,
    authorPartial: authorName !== authorUrl,
    complete: spec && authorName && authorUrl,
  };
}

function metadataHtml(content) {
  const state = metadataState(content);
  const authorClass = state.author ? "" : state.authorPartial ? "partial" : "missing";
  const authorLabel = state.author ? "已填写" : state.authorPartial ? "不完整" : "未填写";
  return `
    <div class="metadata-row"><span>Spec URL</span><span class="status ${state.spec ? "" : "missing"}">${state.spec ? "已填写" : "未填写"}</span></div>
    <div class="metadata-row"><span>Image Author</span><span class="status ${authorClass}">${authorLabel}</span></div>`;
}

function render() {
  const search = elements.search.value.trim().toLocaleLowerCase();
  const repo = elements.repoFilter.value;
  const status = elements.statusFilter.value;
  const dieSize = elements.dieSizeFilter.value;
  const metadata = elements.metadataFilter.value;
  const filtered = data.records.filter((record) => {
    const haystack = JSON.stringify(record).toLocaleLowerCase();
    const dieSizeFilled = hasDieSize(record.content);
    const metadataFilled = metadataState(record.content).complete;
    return (
      (!search || haystack.includes(search)) &&
      (!repo || record.repo === repo) &&
      issueType(record, status) &&
      (!dieSize || (dieSize === "filled") === dieSizeFilled) &&
      (!metadata || (metadata === "filled") === metadataFilled)
    );
  });

  const issueCount = data.records.filter((record) => record.issues.length).length;
  elements.summary.textContent = `共 ${data.records.length} 个芯片，${issueCount} 个存在缺失或不一致；当前显示 ${filtered.length} 个。`;
  elements.empty.classList.toggle("hidden", filtered.length > 0);
  elements.rows.innerHTML = filtered.map((record) => {
    const issueLabels = record.issues.map((issue) => issue.label).join("、");
    return `
      <tr data-id="${escapeHtml(record.id)}">
        <td><span class="status ${record.issues.length ? "issue" : ""}" title="${escapeHtml(issueLabels)}">${record.issues.length ? `${record.issues.length} 个问题` : "一致"}</span></td>
        <td class="repo-cell"><strong>${escapeHtml(record.repo)}</strong><span>${escapeHtml(record.directoryName)}</span></td>
        <td class="name-cell"><strong>${escapeHtml(record.list?.name || "—")}</strong><span>${escapeHtml(record.list?.listname || "")}</span></td>
        <td class="name-cell"><strong>${escapeHtml(record.content?.name || "—")}</strong><span>${escapeHtml(record.content?.githubRepo || "")}</span></td>
        <td>${pairHtml(record.list?.vendor, record.content?.vendor)}</td>
        <td>${pairHtml(record.list?.type, record.content?.type)}</td>
        <td>${pairHtml(record.list?.family, record.content?.family)}</td>
        <td class="die-size-cell">${dieSizeHtml(record.content)}</td>
        <td class="metadata-cell">${metadataHtml(record.content)}</td>
      </tr>`;
  }).join("");
}

function renderFields(container, definitions, values) {
  container.innerHTML = definitions.map(([name, label, flags = ""]) => {
    const type = flags.includes("number") ? "number" : "text";
    const step = type === "number" ? ' step="any"' : "";
    const readonly = flags.includes("readonly") ? " readonly" : "";
    const openButton = name === "source"
      ? '<button type="button" class="secondary open-field-url" data-url-field="source">打开</button>'
      : "";
    const input = `<input type="${type}"${step}${readonly} data-field="${escapeHtml(name)}" value="${escapeHtml(values?.[name] ?? "")}">`;
    return `
      <label class="${flags.includes("wide") ? "wide" : ""}">
        ${escapeHtml(label)}${flags.includes("optional") ? "（可选）" : ""}
        ${openButton ? `<span class="field-with-action">${input}${openButton}</span>` : input}
      </label>`;
  }).join("");
}

function defaultList(repo, directoryName) {
  const sampleUrl = data.records.find((record) => record.repo === repo && record.list?.url)?.list.url;
  const urlPrefix = sampleUrl?.slice(0, sampleUrl.lastIndexOf("/") + 1) || `https://misdake.github.io/${repo}/`;
  return {
    vendor: "",
    type: "",
    family: "",
    name: directoryName,
    url: `${urlPrefix}${encodeURIComponent(directoryName)}`,
  };
}

function defaultContent(repo, directoryName) {
  const githubRepo =
    data.records.find((record) => record.repo === repo && record.content?.githubRepo)?.content.githubRepo ||
    `misdake/${repo}`;
  return {
    source: "",
    githubRepo,
    githubIssueId: 0,
    vendor: "",
    type: "",
    family: "",
    name: directoryName,
    tileSize: 512,
    width: 0,
    height: 0,
    maxLevel: 0,
    levels: [],
    widthMillimeter: 0,
    heightMillimeter: 0,
  };
}

function openEditor(record) {
  activeRecord = structuredClone(record);
  elements.title.textContent = record.list?.listname || record.list?.name || record.content?.name || record.directoryName;
  elements.subtitle.textContent = `${record.repo} / ${record.directoryName}`;
  elements.listExistence.textContent = record.list ? "已存在" : "保存时将添加";
  elements.contentExistence.textContent = record.content ? "已存在" : "保存时将添加";
  renderFields(elements.listFields, listFieldDefinitions, record.list || defaultList(record.repo, record.directoryName));
  renderFields(elements.contentFields, contentFieldDefinitions, record.content || defaultContent(record.repo, record.directoryName));
  elements.saveMessage.textContent = "";
  elements.dialog.showModal();
  initialEditorState = editorState();
  updateIdentityActions();
  updateEditorIssues();
  updateDieSizeButton();
  updateOpenUrlButtons();
}

function readFields(container, definitions, original = {}) {
  const result = { ...original };
  for (const [name, , flags = ""] of definitions) {
    const input = container.querySelector(`[data-field="${name}"]`);
    if (flags.includes("readonly")) continue;
    const value = input.value.trim();
    if (flags.includes("optional") && value === "") {
      delete result[name];
    } else if (flags.includes("number")) {
      result[name] = value === "" ? 0 : Number(value);
    } else {
      result[name] = value;
    }
  }
  return result;
}

function editorState() {
  const fields = [...elements.form.querySelectorAll("input, select")]
    .filter((input) => !input.disabled)
    .map((input) => [input.id || input.dataset.field || input.dataset.levelField, input.value]);
  return JSON.stringify(fields);
}

function editorIsDirty() {
  return editorState() !== initialEditorState;
}

function closeEditor() {
  elements.dialog.close();
  initialEditorState = "";
}

async function requestEditorClose() {
  if (saveInProgress) return;
  if (!editorIsDirty()) {
    closeEditor();
    return;
  }
  if (!elements.closeConfirm.open) elements.closeConfirm.showModal();
}

function copyIdentity(fromContainer, toContainer) {
  for (const field of identityFields) {
    const target = toContainer.querySelector(`[data-field="${field}"]`);
    if (!target.readOnly) target.value = fromContainer.querySelector(`[data-field="${field}"]`).value;
  }
  updateIdentityActions();
}

function writableIdentityFieldsDiffer(targetContainer) {
  return identityFields.some((field) =>
    !targetContainer.querySelector(`[data-field="${field}"]`).readOnly &&
    elements.listFields.querySelector(`[data-field="${field}"]`).value !==
      elements.contentFields.querySelector(`[data-field="${field}"]`).value
  );
}

function updateIdentityActions() {
  elements.copyContentToList.disabled = !writableIdentityFieldsDiffer(elements.listFields);
  elements.copyListToContent.disabled = !writableIdentityFieldsDiffer(elements.contentFields);
  updateEditorIssues();
}

function updateEditorIssues() {
  const fixedIssueCodes = new Set(["missing-list", "missing-content", "url-directory"]);
  const fixedIssues = activeRecord.issues.filter((issue) => fixedIssueCodes.has(issue.code));
  const mismatchIssues = identityFields
    .filter((field) =>
      elements.listFields.querySelector(`[data-field="${field}"]`).value !==
      elements.contentFields.querySelector(`[data-field="${field}"]`).value
    )
    .map((field) => `${field} 不一致`);
  const issues = [...fixedIssues.map((issue) => issue.label), ...mismatchIssues];
  elements.issues.textContent = issues.length
    ? `检查结果：${issues.join("；")}`
    : "检查结果：list 与 content 身份字段一致。";
  elements.issues.classList.toggle("success", issues.length === 0);
  elements.issues.classList.toggle("error", issues.length > 0);
}

function contentNumber(field) {
  return Number(elements.contentFields.querySelector(`[data-field="${field}"]`).value);
}

function canCalculateDieSize() {
  return (
    contentNumber("widthMillimeter") === 0 &&
    contentNumber("heightMillimeter") === 0 &&
    Number(activeRecord.content?.width) > 0 &&
    Number(activeRecord.content?.height) > 0
  );
}

function calculateDimensions(dieSize) {
  const ratio = Number(activeRecord.content.width) / Number(activeRecord.content.height);
  return {
    width: Math.sqrt(dieSize * ratio),
    height: Math.sqrt(dieSize / ratio),
  };
}

function formatDimension(value) {
  return Number(value.toFixed(4)).toString();
}

function updateDieSizeButton() {
  elements.calculateDieSize.classList.toggle("hidden", !canCalculateDieSize());
}

function validWebUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function updateOpenUrlButtons() {
  elements.contentFields.querySelectorAll("[data-url-field]").forEach((button) => {
    const input = elements.contentFields.querySelector(`[data-field="${button.dataset.urlField}"]`);
    button.disabled = !validWebUrl(input.value.trim());
  });
}

function updateDieSizePreview() {
  const dieSize = Number(elements.dieSizeValue.value);
  if (!(dieSize > 0)) {
    elements.dieSizePreview.textContent = "";
    return;
  }
  const dimensions = calculateDimensions(dieSize);
  elements.dieSizePreview.textContent =
    `计算结果：${formatDimension(dimensions.width)} mm × ${formatDimension(dimensions.height)} mm`;
}

function closeDieSizeDialog() {
  elements.dieSizeDialog.close();
  elements.dieSizeValue.value = "";
  elements.dieSizePreview.textContent = "";
}

async function loadData() {
  data = await api("/api/data");
  elements.repoFilter.innerHTML = '<option value="">全部仓库</option>' +
    data.repos.map((repo) => `<option value="${escapeHtml(repo)}">${escapeHtml(repo)}</option>`).join("");
  render();
}

elements.rows.addEventListener("click", (event) => {
  const row = event.target.closest("tr[data-id]");
  if (!row) return;
  const record = data.records.find((item) => item.id === row.dataset.id);
  if (record) openEditor(record);
});
[elements.search, elements.repoFilter, elements.statusFilter, elements.dieSizeFilter, elements.metadataFilter]
  .forEach((element) => element.addEventListener("input", render));
document.querySelectorAll("[data-close]").forEach((button) => button.addEventListener("click", requestEditorClose));
elements.dialog.addEventListener("mousedown", (event) => {
  if (event.target !== elements.dialog) return;
  const bounds = elements.dialog.getBoundingClientRect();
  const inside =
    event.clientX >= bounds.left &&
    event.clientX <= bounds.right &&
    event.clientY >= bounds.top &&
    event.clientY <= bounds.bottom;
  if (!inside) requestEditorClose();
});
elements.dialog.addEventListener("cancel", (event) => {
  event.preventDefault();
  requestEditorClose();
});
elements.confirmKeepEditing.addEventListener("click", () => elements.closeConfirm.close());
elements.confirmDiscard.addEventListener("click", () => {
  elements.closeConfirm.close();
  closeEditor();
});
elements.confirmSave.addEventListener("click", async () => {
  elements.confirmSave.disabled = true;
  const saved = await saveEditor();
  elements.confirmSave.disabled = false;
  if (saved) elements.closeConfirm.close();
});
elements.closeConfirm.addEventListener("mousedown", (event) => {
  if (event.target !== elements.closeConfirm) return;
  const bounds = elements.closeConfirm.getBoundingClientRect();
  const inside =
    event.clientX >= bounds.left &&
    event.clientX <= bounds.right &&
    event.clientY >= bounds.top &&
    event.clientY <= bounds.bottom;
  if (!inside) elements.closeConfirm.close();
});
elements.closeConfirm.addEventListener("cancel", (event) => {
  event.preventDefault();
  elements.closeConfirm.close();
});
elements.reload.addEventListener("click", async () => {
  elements.reload.disabled = true;
  try {
    data = await api("/api/reload", { method: "POST" });
    render();
  } catch (error) {
    alert(error.message);
  } finally {
    elements.reload.disabled = false;
  }
});
elements.copyListToContent.addEventListener("click", () => copyIdentity(elements.listFields, elements.contentFields));
elements.copyContentToList.addEventListener("click", () => copyIdentity(elements.contentFields, elements.listFields));
elements.listFields.addEventListener("input", updateIdentityActions);
elements.contentFields.addEventListener("input", () => {
  updateIdentityActions();
  updateDieSizeButton();
  updateOpenUrlButtons();
});
elements.contentFields.addEventListener("click", (event) => {
  const button = event.target.closest("[data-url-field]");
  if (!button || button.disabled) return;
  const input = elements.contentFields.querySelector(`[data-field="${button.dataset.urlField}"]`);
  window.open(input.value.trim(), "_blank", "noopener,noreferrer");
});
elements.calculateDieSize.addEventListener("click", () => {
  const width = Number(activeRecord.content.width);
  const height = Number(activeRecord.content.height);
  elements.dieSizeRatio.textContent = `图像比例：${width} × ${height} px`;
  elements.dieSizeValue.value = "";
  elements.dieSizePreview.textContent = "";
  elements.dieSizeDialog.showModal();
  elements.dieSizeValue.focus();
});
elements.dieSizeValue.addEventListener("input", updateDieSizePreview);
elements.dieSizeCancel.addEventListener("click", closeDieSizeDialog);
elements.dieSizeDialog.addEventListener("cancel", (event) => {
  event.preventDefault();
  closeDieSizeDialog();
});
elements.dieSizeDialog.addEventListener("mousedown", (event) => {
  if (event.target !== elements.dieSizeDialog) return;
  const bounds = elements.dieSizeDialog.getBoundingClientRect();
  const inside =
    event.clientX >= bounds.left &&
    event.clientX <= bounds.right &&
    event.clientY >= bounds.top &&
    event.clientY <= bounds.bottom;
  if (!inside) closeDieSizeDialog();
});
elements.dieSizeForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!elements.dieSizeForm.reportValidity()) return;
  const dimensions = calculateDimensions(Number(elements.dieSizeValue.value));
  elements.contentFields.querySelector('[data-field="widthMillimeter"]').value = formatDimension(dimensions.width);
  elements.contentFields.querySelector('[data-field="heightMillimeter"]').value = formatDimension(dimensions.height);
  closeDieSizeDialog();
  updateDieSizeButton();
});

async function saveEditor() {
  if (saveInProgress) return false;
  if (!elements.form.reportValidity()) return false;
  saveInProgress = true;
  elements.saveButton.disabled = true;
  elements.saveMessage.textContent = "正在保存...";
  try {
    const list = readFields(elements.listFields, listFieldDefinitions, activeRecord?.list);
    const content = readFields(elements.contentFields, contentFieldDefinitions, activeRecord?.content);
    const body = { id: activeRecord.id, list, content };
    await api("/api/chips", { method: "PUT", body: JSON.stringify(body) });
    await loadData();
    closeEditor();
    return true;
  } catch (error) {
    elements.saveMessage.textContent = error.message;
    return false;
  } finally {
    saveInProgress = false;
    elements.saveButton.disabled = false;
  }
}

elements.form.addEventListener("submit", async (event) => {
  event.preventDefault();
  await saveEditor();
});

loadData().catch((error) => {
  elements.summary.textContent = `读取失败：${error.message}`;
});
