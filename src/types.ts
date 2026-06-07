export type WriteMode = "debug" | "release";

export interface Level {
  level: number;
  xMax: number;
  yMax: number;
}

export interface ImageContent {
  vendor: string;
  type: string;
  family: string;
  name: string;
  listname?: string;
  githubRepo: string;
  githubIssueId: number;
  source: string;
  imageAuthorName: string;
  imageAuthorUrl: string;
  specUrl: string;
  createTime: string;
  width: number;
  height: number;
  tileSize: number;
  maxLevel: number;
  levels: Level[];
  widthMillimeter: number;
  heightMillimeter: number;
}

export interface ChipListItem {
  vendor: string;
  type: string;
  family: string;
  name: string;
  listname?: string;
  url: string;
}

export interface UploadedImage {
  id: string;
  path: string;
  originalName: string;
  width: number;
  height: number;
}

export interface ChipMetadata {
  vendor: string;
  type: string;
  family: string;
  name: string;
  listname?: string;
  source: string;
  imageAuthorName: string;
  imageAuthorUrl: string;
  specUrl: string;
  githubRepo: string;
  githubIssueId: number;
  widthMillimeter: number;
  heightMillimeter: number;
}
