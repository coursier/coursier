var path = require('path');

module.exports = {
  entry: {
    "web-fastopt": [path.join(__dirname, "out/web/fastOpt/dest/out.js")]
  },
  output: {
    path: path.join(__dirname, "out/web/webpack/dest/"),
    filename: "[name]-bundle.js"
  },
  mode: "development",
  devtool: "source-map",
  module: {
    rules: [{
      test: /\.js$/,
      use: ["scalajs-friendly-source-map-loader"],
      enforce: "pre",
  }]
  },
  resolveLoader: {
      modules: [
          path.join(__dirname, 'node_modules')
      ]
  },
  resolve: {
    extensions: [".ts", ".js"],
    alias: {
      "raphael": 'webpack-raphael',
      "bootstrap-treeview": path.join(__dirname, 'node_modules', 'bootstrap-treeview', 'src', 'js', 'bootstrap-treeview.js')
    },
    modules: [
      "node_modules",
      path.resolve(__dirname, 'node_modules')
    ]
  }
}
