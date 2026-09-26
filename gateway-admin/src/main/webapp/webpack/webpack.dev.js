const path = require('path');
const { merge } = require('webpack-merge');
const webpack = require('webpack');
const common = require('./webpack.common.js');

const root = path.resolve(__dirname, '..');
// The edge to proxy /api and /auth to (the SPA is served here on 9001 during development).
const edge = process.env.EDGE_URL || 'http://localhost:8081';

module.exports = (env = {}) =>
  merge(common(env), {
    mode: 'development',
    devtool: 'eval-cheap-module-source-map',
    output: {
      path: path.resolve(root, 'dist'),
      publicPath: '/',
      filename: 'assets/js/[name].js',
      clean: true,
    },
    module: {
      rules: [{ test: /\.css$/, use: ['style-loader', 'css-loader'] }],
    },
    plugins: [new webpack.DefinePlugin({ 'process.env.NODE_ENV': JSON.stringify('development') })],
    devServer: {
      port: 9001,
      hot: true,
      historyApiFallback: true,
      static: { directory: path.resolve(root, 'public') },
      proxy: [{ context: ['/api', '/auth'], target: edge, changeOrigin: true }],
    },
  });
