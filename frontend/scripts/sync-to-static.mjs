import { cp, mkdir, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendRoot = path.resolve(__dirname, "..");
const distDir = path.resolve(frontendRoot, "dist");
const staticDir = path.resolve(frontendRoot, "..", "src", "main", "resources", "static");
const distAssetsDir = path.resolve(distDir, "assets");
const staticAssetsDir = path.resolve(staticDir, "assets");

async function sync() {
  await mkdir(staticDir, { recursive: true });
  await rm(staticAssetsDir, { recursive: true, force: true });

  if (existsSync(distAssetsDir)) {
    await cp(distAssetsDir, staticAssetsDir, { recursive: true });
  }

  await cp(path.resolve(distDir, "index.html"), path.resolve(staticDir, "index.html"));
}

sync().catch((error) => {
  console.error("Failed to sync dist to Spring static directory.");
  console.error(error);
  process.exitCode = 1;
});
