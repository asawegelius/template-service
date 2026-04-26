param (
    [string]$ProjectName
)

if (-not $ProjectName) {
    Write-Output "Usage: ./bootstrap-new-project.ps1 'Your New Project Name'"
    exit 1
}

Write-Output "Bootstrapping new project: $ProjectName"
$templateReference = "unknown-template-reference"
$templateRepository = "unknown-template-repository"
$bootstrapTimestampUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

if (Test-Path ".git") {
    try {
        $templateReference = (git describe --tags --always 2>$null).Trim()
        if (-not $templateReference) {
            $templateReference = (git rev-parse --short HEAD 2>$null).Trim()
        }
    } catch {
        $templateReference = "unknown-template-reference"
    }

    try {
        $templateRepository = (git config --get remote.origin.url 2>$null).Trim()
        if (-not $templateRepository) {
            $templateRepository = "unknown-template-repository"
        }
    } catch {
        $templateRepository = "unknown-template-repository"
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

$provenanceLines = @(
    "template.name=template-service",
    "template.reference=$templateReference",
    "template.repository=$templateRepository",
    "template.bootstrapped_at_utc=$bootstrapTimestampUtc",
    "template.bootstrap_script=bootstrap-new-project.ps1"
)

$provenanceLines | Out-File -Encoding utf8 template-origin.properties
Write-Output "template-origin.properties created."

$readmeLines = @(
    "# $ProjectName",
    "",
    "Bootstrapped from template-service ($templateReference).",
    "Template provenance is recorded in `template-origin.properties`.",
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
