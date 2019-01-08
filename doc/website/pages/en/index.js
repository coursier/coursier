const React = require('react');

const CompLibrary = require('../../core/CompLibrary.js');
const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

const siteConfig = require(process.cwd() + '/siteConfig.js');

class Button extends React.Component {
  render() {
    return (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={this.props.href} target={this.props.target}>
          {this.props.children}
        </a>
      </div>
    );
  }
}

function assetUrl(img) {
  return siteConfig.baseUrl + 'docs/assets/' + img;
}


function imgUrl(img) {
  return siteConfig.baseUrl + "img/" + img;
}

function docUrl(doc, language) {
  return siteConfig.baseUrl + 'docs/' + (language ? language + '/' : '') + doc;
}

Button.defaultProps = {
  target: '_self',
};

const SplashContainer = props => (
  <div className="homeContainer">
    <div className="homeSplashFade">
      <div className="wrapper homeWrapper">{props.children}</div>
    </div>
  </div>
);

const Logo = props => (
  <div className="projectLogo">
    <img src={props.img_src} />
  </div>
);

const ProjectTitle = props => (
  <h2 className="projectTitle">
    {siteConfig.title}
    <small>{siteConfig.tagline}</small>
  </h2>
);

const PromoSection = props => (
  <div className="section promoSection">
    <div className="promoRow">
      <div className="pluginRowBlock">{props.children}</div>
    </div>
  </div>
);

class HomeSplash extends React.Component {
  render() {
    let language = this.props.language || '';
    return (
      <SplashContainer>
        <Logo img_src={assetUrl('DigbyShadows.svg')} />
        <div className="inner">
          <ProjectTitle />
          <img id="screencast" src={imgUrl("coursier-launch.gif")} />
          <PromoSection>
            <Button href={docUrl('intro', language)}>Docs</Button>
            <Button href='https://github.com/coursier/coursier'>Code</Button>
          </PromoSection>
          <PromoSection>
            <Button href={docUrl('quick-start-cli', language)}>CLI</Button>
            <Button href={docUrl('quick-start-sbt', language)}>sbt</Button>
            <Button href={docUrl('quick-start-api', language)}>API</Button>
          </PromoSection>
        </div>
      </SplashContainer>
    );
  }
}

const Block = props => (
  <Container
      padding={['bottom', 'top']}
      id={props.id}
      background={props.background}>
      <GridBlock align="center" contents={props.children} layout={props.layout}/>
  </Container>
);

const MainFeatures = props => (

  <div>
      <Block layout="fourColumn">
          {[
              {
                  content:
                      'Check dependencies and run apps from them right from the command-line',
                  title: 'CLI',
              },
              {
                  content:
                      'No global lock involved, so that several apps can download things at the same time',
                  title: 'No global lock',
              },
              {
                  content:
                      'Don\'t wait for the slowest repositories, and enjoy multiple downloads at the same time from repositories allowing it',
                  title: 'Parallel downloads',
              },
              {
                  content:
                      'Immutability and referential transparency at heart, relying on Scala futures or the IO monad of your choice',
                  title: 'Principled API',
              },
          ]}
      </Block>
  </div>
);

class Index extends React.Component {
  render() {
    let language = this.props.language || '';

    return (
        <div>
          <HomeSplash language={language} />
          <MainFeatures/>
        </div>
    );
  }
}

module.exports = Index;
