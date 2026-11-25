#!/bin/sh
set -eu
mkdir -p website/dist/docs/assets website/dist/docs/store/screenshots website/dist/site
cp website/index.html website/dist/site/index.html
cp docs/assets/anilumos-logo.svg website/dist/docs/assets/
cp docs/store/screenshots/*.jpg website/dist/docs/store/screenshots/
# Keep relative asset paths valid and serve the landing page at the root.
sed 's|\.\./docs/|/docs/|g' website/index.html > website/dist/index.html
rm -rf website/dist/site
