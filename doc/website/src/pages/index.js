import React from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';

import styles from './index.module.css';

const features = [
  {
    title: 'CLI',
    content: 'Check dependencies and run apps from them right from the command-line',
  },
  {
    title: 'No global lock',
    content:
      'No global lock involved, so that several apps can download things at the same time',
  },
  {
    title: 'Parallel downloads',
    content:
      "Don't wait for the slowest repositories, and enjoy multiple downloads at the same time from repositories allowing it",
  },
  {
    title: 'Principled API',
    content:
      'Immutability and referential transparency at heart, relying on Scala futures or the IO monad of your choice',
  },
];

function Buttons({children}) {
  return <div className={styles.buttons}>{children}</div>;
}

function Button({to, children}) {
  return (
    <Link className="button button--secondary button--lg" to={to}>
      {children}
    </Link>
  );
}

function HomeSplash() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={styles.heroBanner}>
      <div className="container">
        <h1 className="hero__title">{siteConfig.title}</h1>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <img
          className={styles.screencast}
          src={useBaseUrl('/img/coursier-launch.gif')}
          alt="Running an application with coursier"
        />
        <Buttons>
          <Button to="/docs/overview">Docs</Button>
          <Button to="https://github.com/coursier/coursier">Code</Button>
        </Buttons>
        <Buttons>
          <Button to="/docs/cli-installation">Install CLI</Button>
          <Button to="/docs/sbt-coursier">Setup sbt</Button>
          <Button to="/docs/api">Use the API</Button>
        </Buttons>
        <Buttons>
          <Button to="https://alexarchambault.github.io/posts/2020-09-21-cs-setup.html">
            Single Command Scala Setup
          </Button>
        </Buttons>
      </div>
    </header>
  );
}

function MainFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {features.map(({title, content}) => (
            <div key={title} className="col col--3 text--center padding-horiz--md">
              <h3>{title}</h3>
              <p>{content}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout title={siteConfig.title} description={siteConfig.tagline}>
      <HomeSplash />
      <main>
        <MainFeatures />
      </main>
    </Layout>
  );
}
