import fs from "node:fs";
import path from "node:path";
import { gzipSync } from "node:zlib";

const root = path.resolve(process.cwd(), "dist");
const assetsDir = path.join(root, "assets");
const limitBytes = 150 * 1024;

if (!fs.existsSync(assetsDir)) {
  console.error("Build assets not found. Run npm run build first.");
  process.exit(1);
}

const entries = fs.readdirSync(assetsDir).filter((file) => /\.(js|css)$/.test(file));
const total = entries.reduce((sum, file) => {
  const content = fs.readFileSync(path.join(assetsDir, file));
  const gzipped = gzipSync(content);
  return sum + gzipped.byteLength;
}, 0);

if (total > limitBytes) {
  console.error(`Bundle size ${total} bytes exceeds ${limitBytes} bytes.`);
  process.exit(1);
}

console.log(`Bundle size ${total} bytes within ${limitBytes} bytes.`);
