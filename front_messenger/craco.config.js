const webpack = require('webpack');

module.exports = {
    webpack: {
        plugins: {
            add: [
                new webpack.ProvidePlugin({
                    process: 'process/browser.js',
                }),
            ],
        },
        configure: (webpackConfig) => {
            // Добавляем fallback для Node.js модулей, которых нет в браузере
            webpackConfig.resolve.fallback = {
                ...webpackConfig.resolve.fallback,
                process: require.resolve('process/browser.js'),
                buffer: false,
                crypto: false,
                stream: false,
                util: false,
                path: false,
                fs: false,
            };
            return webpackConfig;
        },
    },
};
