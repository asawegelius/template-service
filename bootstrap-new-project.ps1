param (
    [string]$ProjectName,
    [string]$ServiceSlug,
    [string]$BasePackage,
    [ValidateSet("persistence", "generic")]
    [string]$TemplateFlavor = "persistence"
)

if (-not $ProjectName) {
    Write-Output "Usage: ./bootstrap-new-project.ps1 'Your New Project Name' [-ServiceSlug 'your-service-slug'] [-BasePackage 'com.example.your.service'] [-TemplateFlavor 'persistence|generic']"
    exit 1
}

function Convert-ToServiceSlug {
    param (
        [string]$Value
    )

    $slug = $Value.ToLowerInvariant() -replace '[^a-z0-9]+', '-' -replace '^-+', '' -replace '-+$', '' -replace '-+', '-'
    if (-not $slug) {
        return "service"
    }

    return $slug
}

function Convert-ToPascalCase {
    param (
        [string]$Value
    )

    $segments = $Value -split '[^A-Za-z0-9]+' | Where-Object { $_ }
    $result = ($segments | ForEach-Object {
            if ($_.Length -eq 1) {
                $_.ToUpperInvariant()
            } else {
                $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1).ToLowerInvariant()
            }
        }) -join ''

    if (-not $result) {
        return "Service"
    }

    return $result
}

function Test-BasePackage {
    param (
        [string]$Value
    )

    return $Value -match '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$'
}

function Replace-InFile {
    param (
        [string]$Path,
        [string]$OldValue,
        [string]$NewValue
    )

    if (-not (Test-Path $Path)) {
        return
    }

    $content = Get-Content -Raw $Path
    $updated = $content.Replace($OldValue, $NewValue)
    [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
}

function Move-PackageDirectory {
    param (
        [string]$OldPath,
        [string]$NewPath
    )

    if (-not (Test-Path $OldPath) -or $OldPath -eq $NewPath) {
        return
    }

    $parent = Split-Path $NewPath -Parent
    New-Item -ItemType Directory -Force -Path $parent | Out-Null

    if (Test-Path $NewPath) {
        Get-ChildItem -Force $OldPath | ForEach-Object {
            Move-Item -Force $_.FullName -Destination $NewPath
        }
        Remove-Item -Recurse -Force $OldPath
    } else {
        Move-Item -Force $OldPath $NewPath
    }
}

function Remove-EmptyDirectoryChain {
    param (
        [string]$StartPath,
        [string]$StopPath
    )

    $current = $StartPath
    while ($current -and (Test-Path $current) -and ($current -ne $StopPath)) {
        if ((Get-ChildItem -Force $current | Measure-Object).Count -gt 0) {
            break
        }

        Remove-Item -Force $current
        $current = Split-Path $current -Parent
    }
}

function Remove-TemplateSection {
    param (
        [string]$Path,
        [string]$StartMarker,
        [string]$EndMarker
    )

    if (-not (Test-Path $Path)) {
        return
    }

    $lines = Get-Content $Path
    $result = New-Object System.Collections.Generic.List[string]
    $skip = $false

    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ($trimmed -eq $StartMarker) {
            $skip = $true
            continue
        }

        if ($trimmed -eq $EndMarker) {
            $skip = $false
            continue
        }

        if (-not $skip) {
            $result.Add($line)
        }
    }

    [System.IO.File]::WriteAllLines($Path, $result, [System.Text.UTF8Encoding]::new($false))
}

if (-not $ServiceSlug) {
    $ServiceSlug = Convert-ToServiceSlug $ProjectName
}

if (-not $BasePackage) {
    $BasePackage = "com.example.$($ServiceSlug -replace '-', '.')"
}

if (-not (Test-BasePackage $BasePackage)) {
    Write-Error "BasePackage must be a valid Java package name."
    exit 1
}

$applicationPrefix = Convert-ToPascalCase $ServiceSlug
$applicationClassName = "${applicationPrefix}Application"
$applicationSpecName = "${applicationPrefix}ApplicationSpec"
$basePackagePath = $BasePackage -replace '\.', [string][IO.Path]::DirectorySeparatorChar
$mainJavaPath = Join-Path "src/main/java" $basePackagePath
$testGroovyPath = Join-Path "src/test/groovy" $basePackagePath

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

$oldMainJavaPath = "src/main/java/com/template/service"
$oldTestGroovyPath = "src/test/groovy/com/template/service"

Move-PackageDirectory $oldMainJavaPath $mainJavaPath
Move-PackageDirectory $oldTestGroovyPath $testGroovyPath
Remove-EmptyDirectoryChain "src/main/java/com/template" "src/main/java"
Remove-EmptyDirectoryChain "src/test/groovy/com/template" "src/test/groovy"

$applicationSourceFile = Join-Path $mainJavaPath "Application.java"
$renamedApplicationSourceFile = Join-Path $mainJavaPath "$applicationClassName.java"
if ((Test-Path $applicationSourceFile) -and $applicationSourceFile -ne $renamedApplicationSourceFile) {
    Move-Item -Force $applicationSourceFile $renamedApplicationSourceFile
}

$applicationSpecFile = Join-Path $testGroovyPath "ApplicationSpec.groovy"
$renamedApplicationSpecFile = Join-Path $testGroovyPath "$applicationSpecName.groovy"
if ((Test-Path $applicationSpecFile) -and $applicationSpecFile -ne $renamedApplicationSpecFile) {
    Move-Item -Force $applicationSpecFile $renamedApplicationSpecFile
}

$filesToRetarget = @(
    "build.gradle",
    "settings.gradle",
    "src/main/resources/application.yaml"
)

if (Test-Path $mainJavaPath) {
    $filesToRetarget += Get-ChildItem -Path $mainJavaPath -Recurse -File | Select-Object -ExpandProperty FullName
}

if (Test-Path $testGroovyPath) {
    $filesToRetarget += Get-ChildItem -Path $testGroovyPath -Recurse -File | Select-Object -ExpandProperty FullName
}

foreach ($file in $filesToRetarget | Select-Object -Unique) {
    Replace-InFile $file "com.template.service" $BasePackage
}

Replace-InFile "build.gradle" "group = 'com.template'" "group = '$BasePackage'"
Replace-InFile "settings.gradle" "rootProject.name = 'template-service'" "rootProject.name = '$ServiceSlug'"
Replace-InFile "src/main/resources/application.yaml" '${SERVICE_NAME:template-service}' "`${SERVICE_NAME:$ServiceSlug}"
Replace-InFile "src/main/resources/application.yaml" 'jdbc:h2:mem:template-service' "jdbc:h2:mem:$ServiceSlug"

$applicationMetadataFile = Join-Path $mainJavaPath "config/ApplicationMetadata.java"
Replace-InFile $applicationMetadataFile 'public static final String SERVICE_NAME = "template-service";' "public static final String SERVICE_NAME = `"$ServiceSlug`";"
Replace-InFile $applicationMetadataFile 'public static final String SWAGGER_TITLE = "Template Service API";' "public static final String SWAGGER_TITLE = `"$ProjectName API`";"
Replace-InFile $applicationMetadataFile 'public static final String SWAGGER_DESCRIPTION = "API for Template Service";' "public static final String SWAGGER_DESCRIPTION = `"API for $ProjectName`";"

Replace-InFile $renamedApplicationSourceFile "public class Application" "public class $applicationClassName"
Replace-InFile $renamedApplicationSourceFile "SpringApplication.run(Application.class, args);" "SpringApplication.run($applicationClassName.class, args);"
Replace-InFile $renamedApplicationSpecFile "class ApplicationSpec extends Specification" "class $applicationSpecName extends Specification"

if ($TemplateFlavor -eq "generic") {
    Remove-TemplateSection "build.gradle" "// TEMPLATE-PERSISTENCE-START" "// TEMPLATE-PERSISTENCE-END"
    Remove-TemplateSection "src/main/resources/application.yaml" "# TEMPLATE-PERSISTENCE-START" "# TEMPLATE-PERSISTENCE-END"

    if (Test-Path "src/main/resources/db") {
        Remove-Item -Recurse -Force "src/main/resources/db"
    }
}

$provenanceLines = @(
    "template.name=template-service",
    "template.reference=$templateReference",
    "template.repository=$templateRepository",
    "template.bootstrapped_at_utc=$bootstrapTimestampUtc",
    "template.bootstrap_script=bootstrap-new-project.ps1",
    "generated.project_name=$ProjectName",
    "generated.service_slug=$ServiceSlug",
    "generated.base_package=$BasePackage",
    "generated.template_flavor=$TemplateFlavor"
)

[System.IO.File]::WriteAllLines("template-origin.properties", $provenanceLines, [System.Text.UTF8Encoding]::new($false))
Write-Output "template-origin.properties created."

$readmeLines = @(
    "# $ProjectName",
    "",
    "Template flavor: ``$TemplateFlavor``",
    "Service slug: ``$ServiceSlug``",
    "Base package: ``$BasePackage``",
    "",
    "Bootstrapped from template-service ($templateReference).",
    "Template provenance is recorded in ``template-origin.properties``.",
    "",
    "Replace this README with project-specific documentation."
)

[System.IO.File]::WriteAllLines("README.md", $readmeLines, [System.Text.UTF8Encoding]::new($false))
Write-Output "New README.md created."

git init
git add .
git commit -m "Initial commit for $ProjectName"

Write-Output "Git repository initialized."
Write-Output "Project '$ProjectName' is now ready. Remember to create a new GitHub repository and add remote origin."
