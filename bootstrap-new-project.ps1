param (
    [string]$ProjectName
)

if (-not $ProjectName) {
    Write-Output "❌ Usage: ./bootstrap-new-project.ps1 'Your New Project Name'"
    exit 1
}

Write-Output "🚀 Bootstrapping new project: $ProjectName"

# Step 1: Remove old Git history
if (Test-Path ".git") {
    Write-Output "🧹 Removing old Git history..."
    Remove-Item -Recurse -Force .git
} else {
    Write-Output "⚠️  No .git folder found, skipping Git cleanup."
}

# Step 2: Delete old README if it exists
if (Test-Path "README.md") {
    Write-Output "🧹 Removing old README.md..."
    Remove-Item README.md
}

# Step 3: Create new README.md
"# $ProjectName" | Out-File -Encoding utf8 README.md
Write-Output "✅ New README.md created."

# Step 4: Initialize new Git repository
git init
git add .
git commit -m "Initial commit for $ProjectName"

Write-Output "✅ Git repository initialized."

Write-Output "🎉 Project '$ProjectName' is now ready! Remember to create a new GitHub repository and add remote origin."
