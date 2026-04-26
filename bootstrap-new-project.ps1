param (
    [string]$ProjectName
)

if (-not $ProjectName) {
    Write-Output "Usage: ./bootstrap-new-project.ps1 'Your New Project Name'"
    exit 1
}

Write-Output "Bootstrapping new project: $ProjectName"
$templateReference = "unknown-template-reference"

if (Test-Path ".git") {
    try {
        $templateReference = (git describe --tags --always 2>$null).Trim()
        if (-not $templateReference) {
            $templateReference = (git rev-parse --short HEAD 2>$null).Trim()
        }
    } catch {
        $templateReference = "unknown-template-reference"
    }
}

if (Test-Path ".git") {
    Write-Output "Removing old Git history..."
    Remove-Item -Recurse -Force .git
} else {
    Write-Output "No .git folder found, skipping Git cleanup."
}

if (Test-Path "README.md") {
    Write-Output "Removing old README.md..."
    Remove-Item README.md
}

$readmeLines = @(
    "# $ProjectName",
    "",
    "Bootstrapped from template-service ($templateReference).",
    "",
    "Replace this README with project-specific documentation."
)

$readmeLines | Out-File -Encoding utf8 README.md
Write-Output "New README.md created."

git init
git add .
git commit -m "Initial commit for $ProjectName"

Write-Output "Git repository initialized."
Write-Output "Project '$ProjectName' is now ready. Remember to create a new GitHub repository and add remote origin."
