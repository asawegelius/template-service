#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: ./bootstrap-new-project.sh 'Your New Project Name'"
  exit 1
fi

PROJECT_NAME="$1"

echo "Bootstrapping new project: $PROJECT_NAME"

if [ -d ".git" ]; then
  echo "Removing old Git history..."
  rm -rf .git
else
  echo "No .git folder found, skipping Git cleanup."
fi

if [ -f "README.md" ]; then
  echo "Removing old README.md..."
  rm README.md
fi

echo "# $PROJECT_NAME" > README.md
echo "New README.md created."

git init
git add .
git commit -m "Initial commit for $PROJECT_NAME"

echo "Git repository initialized."
echo "Project '$PROJECT_NAME' is now ready. Remember to create a new GitHub repository and add remote origin."
