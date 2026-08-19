// Web navigation puts the current screen in the URL path, so a reload or a shared link asks the dev
// server for e.g. `/details/7`. Without this it 404s instead of serving the SPA. There is no Kotlin
// DSL for the flag, hence the raw webpack config.
config.devServer = {
    ...config.devServer,
    "historyApiFallback": true,
};
