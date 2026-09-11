const path = require('path');
const ESLintPlugin = require('eslint-webpack-plugin');
const { VueLoaderPlugin } = require('vue-loader');

const config = {
  mode: 'production',
  context: path.resolve(__dirname, '.'),
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: [
          'babel-loader',
        ]
      },
      {
        test: /\.vue$/,
        use: [
          'vue-loader',
        ]
      }
    ]
  },
  plugins: [
    new ESLintPlugin({
      files: [
        './src/main/webapp/vue-app/*.js',
        './src/main/webapp/vue-app/*.vue',
        './src/main/webapp/vue-app/**/*.js',
        './src/main/webapp/vue-app/**/*.vue',
      ],
    }),
    new VueLoaderPlugin()
  ],
  entry: {
    matrix: './src/main/webapp/vue-apps/matrix/main.js',
    matrixChatNotificationsExtension: './src/main/webapp/vue-apps/notification/main.js',
    analyticsExtensionMatrix: './src/main/webapp/vue-apps/analytics-extension/main.js',
    chatPwaSettings: './src/main/webapp/vue-apps/chat-pwa-settings/main.js',
    matrixAdministration: './src/main/webapp/vue-apps/administration/main.js',
    matrixSpaceTemplateExtension: './src/main/webapp/vue-apps/space-template-extension/main.js',
    matrixSpacesAdministrationExtension: './src/main/webapp/vue-apps/spaces-administration-extension/main.js',
    matrixFavoritesExtension: './src/main/webapp/vue-apps/matrix-favorites-extension/main.js',
    matrixDocumentsExtension: './src/main/webapp/vue-apps/documents-extension/main.js',
    aiUxBindingExtensionMatrix: './src/main/webapp/vue-apps/ai-ux-binding/main.js',
    chatSearch: './src/main/webapp/vue-apps/chat-search/main.js',
    matrixQuickActionExtensions: './src/main/webapp/vue-apps/quick-actions/main.js',
  },
  output: {
    path: path.join(__dirname, 'target/matrix/'),
    filename: 'js/[name].bundle.js',
    libraryTarget: 'amd'
  },
  externals: {
    vue: 'Vue',
    vuetify: 'Vuetify',
    jquery: '$',
  },
};

module.exports = config;
