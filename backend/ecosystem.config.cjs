module.exports = {
  apps: [{
    name: "bnbu-api-kuan-week2",
    script: "dist/src/server.js",
    cwd: __dirname,
    instances: 1,
    exec_mode: "fork",
    autorestart: true,
    watch: false,
    max_memory_restart: "512M",
    kill_timeout: 15000,
    listen_timeout: 10000,
    time: true,
    env: {
      NODE_ENV: "production"
    },
    error_file: "./logs/error.log",
    out_file: "./logs/output.log",
    merge_logs: true
  }]
};
