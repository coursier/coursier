#!/usr/bin/env bash
set -ev

mkdir -p target
git clone https://github.com/coursier/versioned-docs.git target/versioned-docs
cd target/versioned-docs
cp -R versioned_docs versioned_sidebars versions.json ../../doc/website/
cd -

git status

mill -i all doc.publishLocal doc.docusaurus.yarnRunBuild doc.docusaurus.postProcess
