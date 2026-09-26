const path = require('path');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const webpack = require('webpack');

module.exports = {
  mode: 'production',
  entry: path.resolve(__dirname, '../app/index.tsx'),
  output: {
    path: path.resolve(__dirname, '../../../../target/classes/static'),
    filename: 'app/[name].[contenthash].js',
    publicPath: '/',
    clean: true,
  },
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
  performance: { hints: false },
  optimization: {
    minimizer: [new TerserPlugin({ test: /\.m?js(\?.*)?$/i })],
    splitChunks: { chunks: 'all' },
  },
  plugins: [
    new webpack.DefinePlugin({
      'process.env.APP_VERSION': JSON.stringify(process.env.APP_VERSION || 'dev'),
      'process.env.NODE_ENV': JSON.stringify('production'),
    }),
    new HtmlWebpackPlugin({ template: path.resolve(__dirname, '../public/index.html') }),
    new CopyWebpackPlugin({ patterns: [{ from: path.resolve(__dirname, '../public'), to: '.', globOptions: { ignore: ['**/index.html'] } }] }),
  ],
};
