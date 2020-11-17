#!/usr/bin/env sh

# from https://github.com/coursier/apps/blob/f1d2bf568bf466a98569a85c3f23c5f3a8eb5360/.github/scripts/gpg-setup.sh

echo $PGP_SECRET | base64 --decode | gpg --import --no-tty --batch --yes

echo "allow-loopback-pinentry" >>~/.gnupg/gpg-agent.conf
echo "pinentry-mode loopback" >>~/.gnupg/gpg.conf

gpg-connect-agent reloadagent /bye
