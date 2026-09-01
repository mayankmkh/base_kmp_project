// Both halves of making sql.js work in a browser bundle. It lives here rather than next to the
// dependency in `:shared:libs:database` because `webpack.config.d` is only read by the project that
// owns the webpack task, and that is whichever module produces the bundle -- this one.
//
// sql.js is a Node-flavoured npm package: it references `fs`, `path` and `crypto` even in its
// browser build, so those have to be stubbed out rather than resolved. Merged into whatever
// `resolve` the Kotlin plugin already set up, not assigned over it.
config.resolve = config.resolve || {};
config.resolve.fallback = {
    ...config.resolve.fallback,
    fs: false,
    path: false,
    crypto: false,
};

// The worker fetches `sql-wasm.wasm` at runtime by relative URL, so the file has to sit next to the
// bundle. Nothing imports it, which means webpack would not otherwise emit it.
const CopyWebpackPlugin = require("copy-webpack-plugin");
config.plugins.push(
    new CopyWebpackPlugin({
        patterns: ["../../node_modules/sql.js/dist/sql-wasm.wasm"],
    }),
);
