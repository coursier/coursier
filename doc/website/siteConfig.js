// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

const siteConfig = {
  title: 'Coursier',
  tagline: 'Pure Scala Artifact Fetching',

  // wiped when relativizing stuff
  url: 'https://coursier.github.io',
  baseUrl: '/coursier/',

  projectName: 'coursier',
  organizationName: 'coursier',

  customDocsPath: 'processed-docs',

  headerLinks: [
    {doc: 'intro', label: 'Docs'},
    {href: 'https://github.com/coursier/coursier', label: 'GitHub'},
  ],

  users: [],

  colors: {
    primaryColor: '#33475B',
    secondaryColor: '#3498DB',
  },

  copyright: `Copyright © ${new Date().getFullYear()} Alexandre Archambault and coursier contributors`,

  highlight: {
    theme: 'default',
  },

  scripts: ['https://buttons.github.io/buttons.js'],

  onPageNav: 'separate',
  cleanUrl: true,
};

module.exports = siteConfig;
