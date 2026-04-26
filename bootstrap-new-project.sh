#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: ./bootstrap-new-project.sh 'Your New Project Name' [service-slug] [com.example.base.package]"
  exit 1
fi

PROJECT_NAME="$1"
SERVICE_SLUG="$2"
BASE_PACKAGE="$3"
TEMPLATE_REFERENCE="unknown-template-reference"
TEMPLATE_REPOSITORY="unknown-template-repository"
BOOTSTRAP_TIMESTAMP_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

slugify() {
  local value="$1"
  local slug
  slug="$(echo "$value" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//; s/-+/-/g')"
  if [ -z "$slug" ]; then
    echo "service"
  else
    echo "$slug"
  fi
}

to_pascal_case() {
  local value="$1"
  local pascal
  pascal="$(echo "$value" | sed -E 's/[^A-Za-z0-9]+/ /g' | awk '{ for (i = 1; i <= NF; i++) { printf toupper(substr($i, 1, 1)) tolower(substr($i, 2)) } }')"
  if [ -z "$pascal" ]; then
    echo "Service"
  else
    echo "$pascal"
  fi
}

replace_in_file() {
  local file="$1"
  local old_value="$2"
  local new_value="$3"

  if [ ! -f "$file" ]; then
    return
  fi

  sed -i.bak "s|$old_value|$new_value|g" "$file"
  rm -f "$file.bak"
}

move_package_directory() {
  local old_path="$1"
  local new_path="$2"

  if [ ! -d "$old_path" ] || [ "$old_path" = "$new_path" ]; then
    return
  fi

  mkdir -p "$(dirname "$new_path")"

  if [ -d "$new_path" ]; then
    mv "$old_path"/* "$new_path"/
    rmdir "$old_path"
  else
    mv "$old_path" "$new_path"
  fi
}

if [ -z "$SERVICE_SLUG" ]; then
  SERVICE_SLUG="$(slugify "$PROJECT_NAME")"
fi

if [ -z "$BASE_PACKAGE" ]; then
  BASE_PACKAGE="com.example.${SERVICE_SLUG//-/.}"
fi

if [[ ! "$BASE_PACKAGE" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$ ]]; then
  echo "Base package must be a valid Java package name."
  exit 1
fi

APPLICATION_PREFIX="$(to_pascal_case "$SERVICE_SLUG")"
APPLICATION_CLASS_NAME="${APPLICATION_PREFIX}Application"
APPLICATION_SPEC_NAME="${APPLICATION_PREFIX}ApplicationSpec"
BASE_PACKAGE_PATH="${BASE_PACKAGE//./\/}"
MAIN_JAVA_PATH="src/main/java/$BASE_PACKAGE_PATH"
TEST_GROOVY_PATH="src/test/groovy/$BASE_PACKAGE_PATH"

echo "Bootstrapping new project: $PROJECT_NAME"

if [ -d ".git" ]; then
  TEMPLATE_REFERENCE="$(git describe --tags --always 2>/dev/null || git rev-parse --short HEAD 2>/dev/null || echo unknown-template-reference)"
  TEMPLATE_REPOSITORY="$(git config --get remote.origin.url 2>/dev/null || echo unknown-template-repository)"
  echo "Removing old Git history..."
  rm -rf .git
else
  echo "No .git folder found, skipping Git cleanup."
fi

if [ -f "README.md" ]; then
  echo "Removing old README.md..."
  rm README.md
fi

move_package_directory "src/main/java/com/template/service" "$MAIN_JAVA_PATH"
move_package_directory "src/test/groovy/com/template/service" "$TEST_GROOVY_PATH"

if [ -f "$MAIN_JAVA_PATH/Application.java" ] && [ "$MAIN_JAVA_PATH/Application.java" != "$MAIN_JAVA_PATH/$APPLICATION_CLASS_NAME.java" ]; then
  mv "$MAIN_JAVA_PATH/Application.java" "$MAIN_JAVA_PATH/$APPLICATION_CLASS_NAME.java"
fi

if [ -f "$TEST_GROOVY_PATH/ApplicationSpec.groovy" ] && [ "$TEST_GROOVY_PATH/ApplicationSpec.groovy" != "$TEST_GROOVY_PATH/$APPLICATION_SPEC_NAME.groovy" ]; then
  mv "$TEST_GROOVY_PATH/ApplicationSpec.groovy" "$TEST_GROOVY_PATH/$APPLICATION_SPEC_NAME.groovy"
fi

while IFS= read -r file; do
  replace_in_file "$file" "com.template.service" "$BASE_PACKAGE"
done < <(find "$MAIN_JAVA_PATH" "$TEST_GROOVY_PATH" -type f 2>/dev/null)

replace_in_file "build.gradle" "group = 'com.template'" "group = '$BASE_PACKAGE'"
replace_in_file "settings.gradle" "rootProject.name = 'template-service'" "rootProject.name = '$SERVICE_SLUG'"
replace_in_file "src/main/resources/application.yaml" '${SERVICE_NAME:template-service}' "\${SERVICE_NAME:$SERVICE_SLUG}"
replace_in_file "src/main/resources/application.yaml" 'jdbc:h2:mem:template-service' "jdbc:h2:mem:$SERVICE_SLUG"
replace_in_file "$MAIN_JAVA_PATH/config/ApplicationMetadata.java" 'public static final String SERVICE_NAME = "template-service";' "public static final String SERVICE_NAME = \"$SERVICE_SLUG\";"
replace_in_file "$MAIN_JAVA_PATH/config/ApplicationMetadata.java" 'public static final String SWAGGER_TITLE = "Template Service API";' "public static final String SWAGGER_TITLE = \"$PROJECT_NAME API\";"
replace_in_file "$MAIN_JAVA_PATH/config/ApplicationMetadata.java" 'public static final String SWAGGER_DESCRIPTION = "API for Template Service";' "public static final String SWAGGER_DESCRIPTION = \"API for $PROJECT_NAME\";"
replace_in_file "$MAIN_JAVA_PATH/$APPLICATION_CLASS_NAME.java" 'public class Application' "public class $APPLICATION_CLASS_NAME"
replace_in_file "$MAIN_JAVA_PATH/$APPLICATION_CLASS_NAME.java" 'SpringApplication.run(Application.class, args);' "SpringApplication.run($APPLICATION_CLASS_NAME.class, args);"
replace_in_file "$TEST_GROOVY_PATH/$APPLICATION_SPEC_NAME.groovy" 'class ApplicationSpec extends Specification' "class $APPLICATION_SPEC_NAME extends Specification"

cat > template-origin.properties <<EOF
template.name=template-service
template.reference=$TEMPLATE_REFERENCE
template.repository=$TEMPLATE_REPOSITORY
template.bootstrapped_at_utc=$BOOTSTRAP_TIMESTAMP_UTC
template.bootstrap_script=bootstrap-new-project.sh
generated.project_name=$PROJECT_NAME
generated.service_slug=$SERVICE_SLUG
generated.base_package=$BASE_PACKAGE
EOF

echo "template-origin.properties created."

cat > README.md <<EOF
# $PROJECT_NAME

Service slug: \`$SERVICE_SLUG\`
Base package: \`$BASE_PACKAGE\`

Bootstrapped from template-service ($TEMPLATE_REFERENCE).
Template provenance is recorded in \`template-origin.properties\`.

Replace this README with project-specific documentation.
EOF

echo "New README.md created."

git init
git add .
git commit -m "Initial commit for $PROJECT_NAME"

echo "Git repository initialized."
echo "Project '$PROJECT_NAME' is now ready. Remember to create a new GitHub repository and add remote origin."
