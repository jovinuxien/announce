const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const webpack = require('webpack');

const root = path.resolve(__dirname, '..');

/**
 * Output layout: index.html at the output root (served by the edge from classpath:/static at "/"),
 * bundles under assets/js and assets/css referenced with absolute "/assets/..." URLs (public on the edge).
 */
module.exports = (env = {}) => ({
  context: root,
  entry: { main: './app/index.tsx' },
  resolve: {
    extensions: ['.ts', '.tsx', '.js', '.json'],
    alias: { app: path.resolve(root, 'app') },
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        exclude: /node_modules/,
        use: { loader: 'ts-loader', options: { transpileOnly: true } },
      },
    ],
  },
  plugins: [
    new HtmlWebpackPlugin({ template: path.resolve(root, 'index.html'), inject: 'body' }),
    new CopyWebpackPlugin({
      patterns: [{ from: path.resolve(root, 'public'), to: '.', globOptions: { ignore: ['**/.gitkeep'] } }],
    }),
    new webpack.DefinePlugin({
      'process.env.APP_VERSION': JSON.stringify(process.env.APP_VERSION || 'dev'),
    }),
  ],
});
