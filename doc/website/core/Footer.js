/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require('react');

class Footer extends React.Component {
  docUrl(doc, language) {
    const baseUrl = this.props.config.baseUrl;
    return `${baseUrl}docs/${language ? `${language}/` : ''}${doc}`;
  }

  pageUrl(doc, language) {
    const baseUrl = this.props.config.baseUrl;
    return baseUrl + (language ? `${language}/` : '') + doc;
  }

  render() {
    return (
      <footer className="nav-footer" id="footer">
        <section className="sitemap">
          <h2>
            <a href={this.props.config.baseUrl} className="nav-home">
              Coursier
            </a>
          </h2>
          <div>
            <h5><a href={this.docUrl('overview')}>Docs</a></h5>
            <a href={this.docUrl('overview')}>Overview</a>
            <a href={this.docUrl('cli-overview')}>Install CLI</a>
            <a href={this.docUrl('sbt-coursier')}>Setup sbt</a>
            <a href={this.docUrl('api')}>Use the API</a>
          </div>
          <div>
            <h5><a href="https://gitter.im/coursier/coursier" target="_blank">Community</a></h5>
            <a href="https://gitter.im/coursier/coursier" target="_blank">
              <img src="https://img.shields.io/gitter/room/coursier/coursier.svg?logo=gitter&style=social" />
            </a>
            <a href="https://github.com/coursier/coursier" target="_blank">
              <img src="https://img.shields.io/github/stars/coursier/coursier.svg?color=%23087e8b&label=stars&logo=github&style=social" />
            </a>
          </div>
        </section>
        <section className="copyright">{this.props.config.copyright}</section>
      </footer>
    );
  }
}

module.exports = Footer;
