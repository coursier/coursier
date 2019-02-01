// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

const siteConfig = {
  title: 'Coursier',
  tagline: 'Pure Scala Artifact Fetching',

  // wiped when relativizing stuff
  url: 'https://get-coursier.io',
  baseUrl: '/',

  projectName: 'coursier',
  organizationName: 'coursier',

  customDocsPath: 'processed-docs',

  headerLinks: [
    {doc: 'overview', label: 'Docs'},
    {blog: true, label: 'Blog'},
    {href: 'https://github.com/coursier/coursier', label: 'GitHub'},
  ],

  users: [],

  colors: {
    primaryColor: '#58B8C1',
    secondaryColor: '#3498DB',
  },

  copyright: `Copyright © ${new Date().getFullYear()} coursier contributors`,

  highlight: {
    theme: 'default',
  },

  scripts: ['https://buttons.github.io/buttons.js'],

  onPageNav: 'separate',
  cleanUrl: true,

  enableUpdateTime: true, // doesn't seem to work

  editUrl: 'https://github.com/coursier/coursier/edit/master/doc/docs/',

  twitter: true,

  algolia: {
    apiKey: '53a7a919d6905f94dafd59e25d0f5e5d',
    indexName: 'get-coursier',
    algoliaOptions: {
      // seems we should need that
      // facetFilters: [ "version:VERSION" ]
    },
  },
};

module.exports = siteConfig;
