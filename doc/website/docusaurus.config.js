// See https://docusaurus.io/docs/api/docusaurus-config for all the possible
// site configuration options.

const fs = require('fs');
const path = require('path');
const {themes} = require('prism-react-renderer');

// versions.json is copied from coursier/versioned-docs by `./mill doc.copyVersionedData`,
// it's missing when building the website without the past versions
const versionsJson = path.join(__dirname, 'versions.json');
const versions = fs.existsSync(versionsJson)
  ? JSON.parse(fs.readFileSync(versionsJson, 'utf8'))
  : [];

// The address the website is served from. Can be overridden with COURSIER_WEBSITE_URL, like
// 'https://host/some/path/', to serve it from elsewhere (behind a proxy, …)
const siteUrl = new URL(process.env.COURSIER_WEBSITE_URL || 'https://get-coursier.io/');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Coursier',
  tagline: 'Pure Scala Artifact Fetching',

  url: siteUrl.origin,
  baseUrl: siteUrl.pathname.endsWith('/') ? siteUrl.pathname : `${siteUrl.pathname}/`,
  // generates docs/overview.html rather than docs/overview/index.html, like Docusaurus 1 did,
  // so that GitHub Pages serves the former URLs of the website as is
  trailingSlash: false,

  organizationName: 'coursier',
  projectName: 'coursier',

  // the past versions have quite a few links to pages that don't exist anymore
  onBrokenLinks: 'warn',
  onBrokenAnchors: 'warn',

  markdown: {
    // Parse .md files as CommonMark rather than MDX, like Docusaurus 1 did. MDX is stricter
    // (no raw '<' or '{', HTML has to be valid JSX…), which the past versions don't abide by.
    format: 'detect',
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          // written by mdoc, from doc/docs
          path: '../processed-docs',
          sidebarPath: require.resolve('./sidebars.json'),
          editUrl: ({version, docPath}) =>
            version === 'current'
              ? `https://github.com/coursier/coursier/edit/main/doc/docs/${docPath}`
              : undefined,
        },
        blog: {
          showReadingTime: false,
          onInlineAuthors: 'ignore',
          onUntruncatedBlogPosts: 'ignore',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themes: [
    [
      // Search index computed at build time, and served along with the website. It only works on
      // the built website, not with the development server.
      require.resolve('@easyops-cn/docusaurus-search-local'),
      {
        // The index file names contain a hash of the docs sources, so that browsers don't use a
        // cached index from a former build. Having the hash in the query rather than in the file
        // name breaks `docusaurus serve` with a base URL other than '/': it doesn't recognize
        // `search-index.json?_=…` as a file, and redirects it to `search-index.json/?_=…`.
        hashed: 'filename',
        // Only used to compute that hash
        docsDir: ['../processed-docs', 'versioned_docs'],
        highlightSearchTermsOnTargetPage: true,
      },
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: 'Coursier',
        items: [
          {type: 'doc', docId: 'overview', position: 'left', label: 'Docs'},
          {to: '/blog', label: 'Blog', position: 'left'},
          {
            type: 'docsVersionDropdown',
            position: 'right',
            // there are way too many versions to list them all here
            versions: ['current', ...versions.slice(0, 5)],
            dropdownItemsAfter: [{to: '/versions', label: 'All versions'}],
          },
          {
            href: 'https://github.com/coursier/coursier',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {label: 'Overview', to: '/docs/overview'},
              {label: 'Install CLI', to: '/docs/cli-installation'},
              {label: 'Setup sbt', to: '/docs/sbt-coursier'},
              {label: 'Use the API', to: '/docs/api'},
            ],
          },
          {
            title: 'Community',
            items: [
              {label: 'Gitter', href: 'https://gitter.im/coursier/coursier'},
              {label: 'GitHub', href: 'https://github.com/coursier/coursier'},
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} coursier contributors`,
      },
      prism: {
        theme: themes.github,
        darkTheme: themes.dracula,
        additionalLanguages: ['bash', 'java', 'scala', 'json'],
      },
    }),
};

module.exports = config;
