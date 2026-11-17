import React from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useVersions, useLatestVersion} from '@docusaurus/plugin-content-docs/client';
import Layout from '@theme/Layout';

function docLink(version) {
  return `${version.path}/overview`;
}

export default function Versions() {
  const {siteConfig} = useDocusaurusContext();
  const versions = useVersions();
  const latestVersion = useLatestVersion();
  const currentVersion = versions.find((version) => version.name === 'current');
  const pastVersions = versions.filter(
    (version) => version !== latestVersion && version.name !== 'current',
  );
  const repoUrl = `https://github.com/${siteConfig.organizationName}/${siteConfig.projectName}`;

  return (
    <Layout title="Versions" description="Coursier documentation versions">
      <main className="container margin-vert--lg">
        <h1>{siteConfig.title} Versions</h1>

        <div className="margin-bottom--lg">
          <h3 id="latest">Current version</h3>
          <table>
            <tbody>
              <tr>
                <th>{latestVersion.label}</th>
                <td>
                  <Link to={docLink(latestVersion)}>Documentation</Link>
                </td>
                <td>
                  {latestVersion.name === 'current' ? (
                    <Link to={repoUrl}>Source Code</Link>
                  ) : (
                    <Link to={`${repoUrl}/releases/tag/v${latestVersion.name}`}>
                      Release Notes
                    </Link>
                  )}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        {currentVersion && currentVersion !== latestVersion && (
          <div className="margin-bottom--lg">
            <h3 id="next">Latest Version</h3>
            <p>Here you can find the latest documentation and unreleased code.</p>
            <table>
              <tbody>
                <tr>
                  <th>main</th>
                  <td>
                    <Link to={docLink(currentVersion)}>Documentation</Link>
                  </td>
                  <td>
                    <Link to={repoUrl}>Source Code</Link>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        )}

        {pastVersions.length > 0 && (
          <div className="margin-bottom--lg">
            <h3 id="archive">Past Versions</h3>
            <p>Here you can find documentation for previous versions.</p>
            <table>
              <tbody>
                {pastVersions.map((version) => (
                  <tr key={version.name}>
                    <th>{version.label}</th>
                    <td>
                      <Link to={docLink(version)}>Documentation</Link>
                    </td>
                    <td>
                      <Link to={`${repoUrl}/releases/tag/v${version.name}`}>
                        Release Notes
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <p>
          You can find past versions of this project on{' '}
          <Link to={`${repoUrl}/releases`}>GitHub</Link>.
        </p>
      </main>
    </Layout>
  );
}
