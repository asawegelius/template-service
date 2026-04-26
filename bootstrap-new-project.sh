#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: ./bootstrap-new-project.sh 'Your New Project Name'"
  exit 1
fi

PROJECT_NAME="$1"
TEMPLATE_REFERENCE="unknown-template-reference"

echo "Bootstrapping new project: $PROJECT_NAME"

if [ -d ".git" ]; then
  TEMPLATE_REFERENCE="$(git describe --tags --always 2>/dev/null || git rev-parse --short HEAD 2>/dev/null || echo unknown-template-reference)"
  echo "Removing old Git history..."
  rm -rf .git
else
  echo "No .git folder found, skipping Git cleanup."
fi

if [ -f "README.md" ]; then
  echo "Removing old README.md..."
  rm README.md
fi

cat > README.md <<EOF
# $PROJECT_NAME

Bootstrapped from template-service ($TEMPLATE_REFERENCE).

Replace this README with project-specific documentation.
EOF

echo "New README.md created."

git init
git add .
git commit -m "Initial commit for $PROJECT_NAME"

echo "Git repository initialized."
echo "Project '$PROJECT_NAME' is now ready. Remember to create a new GitHub repository and add remote origin."
