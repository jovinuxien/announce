const path = require('path');
const { merge } = require('webpack-merge');
const webpack = require('webpack');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const common = require('./webpack.common.js');

const root = path.resolve(__dirname, '..');
// gateway-admin/target/classes/static: the edge serves it at "/" (SpaHistoryFallbackFilter + PublicPaths).
const out = path.resolve(root, '../../../target/classes/static');

module.exports = (env = {}) =>
  merge(common(env), {
    mode: 'production',
    devtool: 'source-map',
    output: {
      path: out,
      publicPath: '/',
      filename: 'assets/js/[name].[contenthash:8].js',
      chunkFilename: 'assets/js/[name].[contenthash:8].chunk.js',
      assetModuleFilename: 'assets/media/[name].[hash:8][ext]',
      clean: true,
    },
    module: {
      rules: [{ test: /\.css$/, use: [MiniCssExtractPlugin.loader, 'css-loader'] }],
    },
    optimization: {
      splitChunks: { chunks: 'all', cacheGroups: { vendors: { test: /[\\/]node_modules[\\/]/, name: 'vendors' } } },
      runtimeChunk: 'single',
    },
    performance: { hints: false },
    plugins: [
      new MiniCssExtractPlugin({ filename: 'assets/css/[name].[contenthash:8].css' }),
      new webpack.DefinePlugin({ 'process.env.NODE_ENV': JSON.stringify('production') }),
    ],
  });
