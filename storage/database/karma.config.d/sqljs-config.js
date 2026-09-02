// Browser tests need the same `sql-wasm.wasm` the app bundle needs, but Karma serves files rather
// than bundling them: the file is registered as served-but-not-included and proxied to the path the
// worker requests.
const path = require("path");
const os = require("os");
const dist = path.resolve("../../node_modules/sql.js/dist/");
const wasm = path.join(dist, "sql-wasm.wasm");

config.files.push({
    pattern: wasm,
    served: true,
    watched: false,
    included: false,
    nocache: false,
});
config.proxies["/sql-wasm.wasm"] = path.join("/absolute/", wasm);

// Karma's default webpack output directory is shared between runs; the worker chunk emitted there
// gets stale. A per-run temp directory keeps the two in step.
const output = {
    path: path.join(os.tmpdir(), "_karma_webpack_") + Math.floor(Math.random() * 1000000),
};
config.set({
    webpack: {...config.webpack, output},
});
config.files.push({
    pattern: `${output.path}/**/*`,
    watched: false,
    included: false,
});
