const { spawnSync } = require("node:child_process");

const pm2Command = process.platform === "win32" ? "pm2.cmd" : "pm2";
const logrotateSettings = {
  max_size: "10M",
  retain: "7",
  compress: "true",
  dateFormat: "YYYY-MM-DD_HH-mm-ss",
  workerInterval: "30",
  rotateInterval: "0 0 * * *",
  rotateModule: "true",
  TZ: "Etc/UTC"
};

function runPm2(args, { captureOutput = false, allowFailure = false } = {}) {
  const result = spawnSync(pm2Command, args, {
    encoding: "utf8",
    shell: process.platform === "win32",
    stdio: captureOutput ? ["ignore", "pipe", "pipe"] : "inherit"
  });

  if (result.error) {
    if (result.error.code === "ENOENT") {
      throw new Error("PM2 CLI was not found. Install PM2 first: npm install --global pm2");
    }
    throw result.error;
  }

  if (result.status !== 0 && !allowFailure) {
    const stderr = captureOutput ? result.stderr.trim() : "";
    throw new Error(`pm2 ${args.join(" ")} failed with exit code ${result.status ?? "unknown"}${stderr ? `: ${stderr}` : ""}`);
  }

  return result;
}

function hasLogrotateModule() {
  const result = runPm2(["jlist"], { captureOutput: true, allowFailure: true });
  if (result.status !== 0) return false;

  try {
    const processes = JSON.parse(result.stdout);
    return processes.some((process) => process.name === "pm2-logrotate");
  } catch {
    throw new Error("PM2 returned invalid JSON for `pm2 jlist`; refusing to change log-rotation settings.");
  }
}

if (!hasLogrotateModule()) {
  runPm2(["install", "pm2-logrotate"]);
}

for (const [name, value] of Object.entries(logrotateSettings)) {
  runPm2(["set", `pm2-logrotate:${name}`, value]);
}

runPm2(["save"]);
console.log("PM2 log rotation configured: 10M per file, 7 retained archives, gzip compression, UTC timestamps.");
