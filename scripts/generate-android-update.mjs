import fs from "node:fs/promises";

const OWNER = "rishi0810";
const REPO = "Musicality-App";
const OUT_PATH = "public/app-update/android.json";

const url = `https://api.github.com/repos/${OWNER}/${REPO}/releases/latest`;

const res = await fetch(url, {
  headers: {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "static-update-json-generator"
  }
});

if (!res.ok) {
  throw new Error(`GitHub API failed: ${res.status} ${res.statusText}`);
}

const release = await res.json();
const apkAsset = release.assets?.find((asset) =>
  asset.name.toLowerCase().endsWith(".apk")
);

if (!apkAsset) {
  throw new Error("No APK asset found in latest GitHub release");
}

const versionCode = findVersionCode(apkAsset.name, release.body || "");
if (versionCode == null) {
  throw new Error(
    "Could not determine versionCode. Use an APK name like app-release-v12.apk or add 'versionCode: 12' to the release body."
  );
}

const updateJson = {
  latestVersionCode: versionCode,
  latestVersionName: release.tag_name,
  apkUrl: apkAsset.browser_download_url
};

await fs.mkdir("public/app-update", { recursive: true });
await fs.writeFile(OUT_PATH, `${JSON.stringify(updateJson, null, 2)}\n`);

console.log(updateJson);

function findVersionCode(assetName, releaseBody) {
  const assetMatch = assetName.match(/v?(\d+)\.apk$/i);
  if (assetMatch) return Number(assetMatch[1]);

  const bodyMatch = releaseBody.match(/versionCode\s*:\s*(\d+)/i);
  if (bodyMatch) return Number(bodyMatch[1]);

  return null;
}
