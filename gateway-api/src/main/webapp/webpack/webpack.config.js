const path = require('path');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const webpack = require('webpack');

// Same-origin edge the dev server proxies to; the edge itself listens on 8080 by default (override when the
// port is taken on your machine, e.g. EDGE_URL=http://localhost:8090 npm run webapp:dev).
const EDGE_URL = process.env.EDGE_URL || 'http://localhost:8080';

module.exports = {
  mode: 'development',
  entry: path.resolve(__dirname, '../app/index.tsx'),
  output: {
    path: path.resolve(__dirname, '../../../../target/classes/static'),
    filename: 'app/[name].bundle.js',
    publicPath: '/',
    clean: true,
  },
  devtool: 'eval-source-map',
  resolve: {
    extensions: ['.tsx', '.ts', '.js'],
    alias: { app: path.resolve(__dirname, '../app') },
  },
  module: {
    rules: [
      { test: /\.tsx?$/, use: { loader: 'ts-loader', options: { onlyCompileBundledFiles: true } }, exclude: /node_modules/ },
      { test: /\.scss$/, use: ['style-loader', 'css-loader', { loader: 'sass-loader', options: { sassOptions: { quietDeps: true, silenceDeprecations: ['import', 'global-builtin', 'color-functions', 'mixed-decls'] } } }] },
      { test: /\.css$/, use: ['style-loader', 'css-loader'] },
      { test: /\.(woff2?|ttf|eot|svg|png|jpe?g|gif|webp)$/, type: 'asset/resource' },
    ],
  },
  plugins: [
    new webpack.DefinePlugin({
      'process.env.APP_VERSION': JSON.stringify(process.env.APP_VERSION || 'dev'),
      'process.env.NODE_ENV': JSON.stringify('development'),
    }),
    new HtmlWebpackPlugin({ template: path.resolve(__dirname, '../public/index.html') }),
    new CopyWebpackPlugin({ patterns: [{ from: path.resolve(__dirname, '../public'), to: '.', globOptions: { ignore: ['**/index.html'] } }] }),
  ],
  devServer: {
    port: 9000,
    historyApiFallback: true,
    proxy: [
      { context: ['/api', '/auth', '/out'], target: EDGE_URL, changeOrigin: true },
      { context: ['/ws'], target: EDGE_URL.replace(/^http/, 'ws'), ws: true, changeOrigin: true },
    ],
  },
};
