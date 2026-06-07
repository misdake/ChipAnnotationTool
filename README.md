# ChipAnnotationTool

Local TypeScript web tool for adding and viewing chip annotations. It generates chip image tiles, writes `content.json`, and rebuilds the aggregate `list.json` from sibling data repositories.

## Commands

```powershell
npm install
npm run dev
```

Open `http://localhost:3000`.

The home page links to:

- `Add`: upload and cut a new chip image.
- `View`: the original `ChipAnnotationList` UI copied unchanged, backed by this tool's APIs.

For a compiled run:

```powershell
npm run build
npm start
```

## Write Modes

- `debug` is the default. It writes everything to `.tmp/output/{jobId}` and leaves target repositories unchanged.
- `release` writes tiles/content to `../ChipAnnotationDataCdn` and rebuilds this tool's final `list.json`. The UI requires an explicit confirmation checkbox.

Uploads are limited to 200MB and are stored under `.tmp/uploads` while processing.

## Cutting

The cutter reads the original image in 16K x 16K source blocks, then writes tiles from each block with limited concurrency. The web UI has a Stop button; cancellation is checked at block/tile boundaries and can leave partial debug/release output for inspection or cleanup.

## Aggregate List

`repos.txt` points to sibling chip data repositories. The server reads chip data from every `content.json`; it does not read or update per-repo `list.json` files. The final aggregate `list.json` in this project is regenerated after edits and after release Add runs.
