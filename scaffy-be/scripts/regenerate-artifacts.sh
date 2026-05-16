#!/usr/bin/env bash
#
# Regenerate the cached scaffold artifacts (angular.zip, spring-boot.zip) by
# running the official framework CLIs against placeholder names, validating
# the output builds, then post-processing the tree to replace the placeholder
# names with __SCAFFY_*__ tokens that the Java composer expands per request.
#
# Requires: bash, GNU sed (Git Bash on Windows ships it; macOS users should
# run via Linux/WSL or install gnu-sed and alias it), node + npx, curl,
# python3, and a working Java toolchain on PATH for the validation pass.
#
# Usage:
#   ./regenerate-artifacts.sh                 # generate + validate + tokenize
#   SKIP_VALIDATE=1 ./regenerate-artifacts.sh # skip the build-validation step
#   STACK=angular ./regenerate-artifacts.sh   # only regenerate one stack
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCAFFY_BE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ARTIFACTS_DIR="$SCAFFY_BE_DIR/src/main/resources/artifacts"

SKIP_VALIDATE="${SKIP_VALIDATE:-0}"
STACK="${STACK:-all}"

ANGULAR_VERSION="${ANGULAR_VERSION:-18}"
SPRING_BOOT_VERSION="${SPRING_BOOT_VERSION:-4.0.6}"
CREATE_VUE_VERSION="${CREATE_VUE_VERSION:-latest}"
CREATE_VITE_VERSION="${CREATE_VITE_VERSION:-latest}"
DOTNET_FRAMEWORK="${DOTNET_FRAMEWORK:-net10.0}"
NESTJS_CLI_VERSION="${NESTJS_CLI_VERSION:-latest}"

# ---- Placeholder identity -------------------------------------------------
# These names are what the CLIs see. After validation they are swept out and
# replaced with the __SCAFFY_*__ tokens. They MUST satisfy each CLI's own
# naming rules (kebab-case, lowercase package leaf, etc).
PROJECT_KEBAB="scaffy-template-app"
PROJECT_PASCAL="ScaffyTemplateApp"
PROJECT_CAMEL="scaffyTemplateApp"
GROUP_ID="com.example"
PACKAGE_LEAF="scaffytemplate"
PACKAGE_NAME="$GROUP_ID.$PACKAGE_LEAF"
PACKAGE_PATH="${PACKAGE_NAME//.//}"

mkdir -p "$ARTIFACTS_DIR"
WORKSPACE="$(mktemp -d -t scaffy-artifacts.XXXXXXXX)"
trap 'rm -rf "$WORKSPACE"' EXIT

# Cross-platform zip via python (Git Bash on Windows has no `zip`).
zip_dir() {
	local src_dir="$1"
	local dest_zip="$2"
	python -c "
import os, sys, zipfile
src = sys.argv[1]
dst = sys.argv[2]
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(src):
        for f in files:
            abs_path = os.path.join(root, f)
            rel_path = os.path.relpath(abs_path, src).replace(os.sep, '/')
            zf.write(abs_path, rel_path)
" "$src_dir" "$dest_zip"
}

echo "==> workspace:    $WORKSPACE"
echo "==> artifacts:    $ARTIFACTS_DIR"
echo "==> skip-validate: $SKIP_VALIDATE"
echo "==> stack:        $STACK"

# Sweep names → tokens across a tree's text files. Any extra args are passed
# through to `find` (typically `-not -path …` exclusions). One sed invocation
# per batch keeps it fast.
tokenize_text_files() {
	find . -type f "$@" \( \
		-name "*.ts" -o -name "*.tsx" -o -name "*.js" -o -name "*.jsx" -o \
		-name "*.json" -o -name "*.html" -o -name "*.css" -o -name "*.scss" -o \
		-name "*.java" -o -name "*.xml" -o -name "*.properties" -o \
		-name "*.cs" -o -name "*.csproj" -o -name "*.http" -o \
		-name "*.yml" -o -name "*.yaml" -o -name "*.md" -o \
		-name ".gitignore" -o -name ".editorconfig" \
	\) -exec sed -i \
		-e "s|$PROJECT_PASCAL|__SCAFFY_PROJECT_PASCAL__|g" \
		-e "s|$PROJECT_CAMEL|__SCAFFY_PROJECT_CAMEL__|g" \
		-e "s|$PACKAGE_PATH|__SCAFFY_PACKAGE_DIR__|g" \
		-e "s|$PACKAGE_NAME|__SCAFFY_PACKAGE__|g" \
		-e "s|$PROJECT_KEBAB|__SCAFFY_PROJECT_NAME__|g" \
		{} +
}

# ---------------------------------------------------------------------------
# Angular
# ---------------------------------------------------------------------------
generate_angular() {
	echo
	echo "==> generating angular @ $ANGULAR_VERSION"
	cd "$WORKSPACE"

	NG_CLI_ANALYTICS=false \
		npx -y --package="@angular/cli@$ANGULAR_VERSION" -- \
		ng new "$PROJECT_KEBAB" \
			--skip-git \
			--skip-install \
			--routing \
			--style=css \
			--ssr=false \
			--defaults

	cd "$PROJECT_KEBAB"

	if [[ "$SKIP_VALIDATE" != "1" ]]; then
		echo "==> validating angular (npm install + npm run build)"
		npm install --silent --no-audit --no-fund
		npm run build --silent
		# Strip validation byproducts before tokenizing/zipping.
		rm -rf node_modules dist .angular
	fi

	echo "==> tokenizing angular sources"
	tokenize_text_files \
		-not -path "./node_modules/*" \
		-not -path "./dist/*" \
		-not -path "./.angular/*"

	cd "$WORKSPACE"
	echo "==> writing $ARTIFACTS_DIR/angular.zip"
	rm -f "$ARTIFACTS_DIR/angular.zip"
	zip_dir "$PROJECT_KEBAB" "$ARTIFACTS_DIR/angular.zip"
}

# ---------------------------------------------------------------------------
# Vue
# ---------------------------------------------------------------------------
generate_vue() {
	echo
	echo "==> generating vue (create-vue @ $CREATE_VUE_VERSION)"
	cd "$WORKSPACE"

	# create-vue writes to a subdirectory named after the project.
	# --ts enables TypeScript; all other optional features are disabled for a
	# minimal scaffold that matches the iteration-2 acceptance criteria.
	npx -y "create-vue@$CREATE_VUE_VERSION" "$PROJECT_KEBAB" --ts

	cd "$PROJECT_KEBAB"

	if [[ "$SKIP_VALIDATE" != "1" ]]; then
		echo "==> validating vue (npm install + npm run build)"
		npm install --silent --no-audit --no-fund
		npm run build --silent
		rm -rf node_modules dist
	fi

	echo "==> tokenizing vue sources"
	tokenize_text_files \
		-not -path "./node_modules/*" \
		-not -path "./dist/*"

	cd "$WORKSPACE"
	echo "==> writing $ARTIFACTS_DIR/vue.zip"
	rm -f "$ARTIFACTS_DIR/vue.zip"
	zip_dir "$PROJECT_KEBAB" "$ARTIFACTS_DIR/vue.zip"
}

# ---------------------------------------------------------------------------
# React (via create-vite --template react-ts)
# ---------------------------------------------------------------------------
generate_react() {
	echo
	echo "==> generating react (create-vite @ $CREATE_VITE_VERSION, template react-ts)"
	cd "$WORKSPACE"

	npx -y "create-vite@$CREATE_VITE_VERSION" "$PROJECT_KEBAB" --template react-ts

	cd "$PROJECT_KEBAB"

	if [[ "$SKIP_VALIDATE" != "1" ]]; then
		echo "==> validating react (npm install + npm run build)"
		npm install --silent --no-audit --no-fund
		npm run build --silent
		rm -rf node_modules dist
	fi

	echo "==> tokenizing react sources"
	tokenize_text_files \
		-not -path "./node_modules/*" \
		-not -path "./dist/*"

	cd "$WORKSPACE"
	echo "==> writing $ARTIFACTS_DIR/react.zip"
	rm -f "$ARTIFACTS_DIR/react.zip"
	zip_dir "$PROJECT_KEBAB" "$ARTIFACTS_DIR/react.zip"
}

# ---------------------------------------------------------------------------
# .NET Web API (dotnet new webapi)
# ---------------------------------------------------------------------------
generate_dotnet() {
	echo
	echo "==> generating .NET Web API (dotnet new webapi @ $DOTNET_FRAMEWORK)"
	cd "$WORKSPACE"

	# .NET uses PascalCase for the project/assembly name.
	dotnet new webapi \
		--name "$PROJECT_PASCAL" \
		--output dotnet-template \
		--framework "$DOTNET_FRAMEWORK" \
		--no-https \
		--no-restore \
		--force

	cd dotnet-template

	# Set the HTTP dev server URL to port 8080 so dotnet run listens consistently.
	if [[ -f Properties/launchSettings.json ]]; then
		sed -i 's|"applicationUrl": "http://localhost:[0-9]*"|"applicationUrl": "http://localhost:8080"|g' \
			Properties/launchSettings.json
	fi

	if [[ "$SKIP_VALIDATE" != "1" ]]; then
		# Use restore (not build) for validation: dotnet new webapi always produces a
		# structurally valid project, and dotnet restore confirms all package references
		# resolve. dotnet build is skipped here because SDK 10.x has a known "Question
		# build" regression that causes the first clean build to exit non-zero on Windows.
		echo "==> validating .NET (dotnet restore)"
		dotnet restore -q
		rm -rf bin obj
	fi

	echo "==> tokenizing .NET sources"
	tokenize_text_files \
		-not -path "./bin/*" \
		-not -path "./obj/*"

	# Rename the .csproj file: ScaffyTemplateApp.csproj → __SCAFFY_PROJECT_PASCAL__.csproj
	find . -maxdepth 1 -name "${PROJECT_PASCAL}.csproj" \
		-execdir mv {} "__SCAFFY_PROJECT_PASCAL__.csproj" \;

	# Rename the .http file if present: ScaffyTemplateApp.http → __SCAFFY_PROJECT_PASCAL__.http
	find . -maxdepth 1 -name "${PROJECT_PASCAL}.http" \
		-execdir mv {} "__SCAFFY_PROJECT_PASCAL__.http" \;

	cd "$WORKSPACE"
	echo "==> writing $ARTIFACTS_DIR/dotnet.zip"
	rm -f "$ARTIFACTS_DIR/dotnet.zip"
	zip_dir "dotnet-template" "$ARTIFACTS_DIR/dotnet.zip"
}

# ---------------------------------------------------------------------------
# Spring Boot
# ---------------------------------------------------------------------------
generate_spring_boot() {
	echo
	echo "==> generating spring boot @ $SPRING_BOOT_VERSION"
	cd "$WORKSPACE"

	curl -fsSL https://start.spring.io/starter.zip \
		-d type=maven-project \
		-d language=java \
		-d bootVersion="$SPRING_BOOT_VERSION" \
		-d baseDir=spring-boot-template \
		-d groupId="$GROUP_ID" \
		-d artifactId="$PROJECT_KEBAB" \
		-d name="$PROJECT_KEBAB" \
		-d packageName="$PACKAGE_NAME" \
		-d packaging=jar \
		-d javaVersion=25 \
		-d dependencies=web \
		-o spring-boot.zip

	unzip -q spring-boot.zip
	rm spring-boot.zip
	cd spring-boot-template

	if [[ "$SKIP_VALIDATE" != "1" ]]; then
		echo "==> validating spring boot (mvnw -DskipTests package)"
		chmod +x ./mvnw
		./mvnw -B -q -DskipTests package
		rm -rf target
	fi

	echo "==> tokenizing spring boot sources"
	tokenize_text_files

	# Rename package directories: src/{main,test}/java/com/example/scaffytemplate
	# becomes src/{main,test}/java/__SCAFFY_PACKAGE_DIR__. The composer expands
	# the single-segment placeholder back into a multi-segment path at runtime.
	for tree in src/main/java src/test/java; do
		if [[ -d "$tree/$PACKAGE_PATH" ]]; then
			mv "$tree/$PACKAGE_PATH" "$tree/__SCAFFY_PACKAGE_DIR__"
			rmdir "$tree/com/example" 2>/dev/null || true
			rmdir "$tree/com" 2>/dev/null || true
		fi
	done

	# Rename Java sources whose filenames contain the project name.
	find . -type f -name "${PROJECT_PASCAL}Application.java" \
		-execdir mv {} "__SCAFFY_PROJECT_PASCAL__Application.java" \;
	find . -type f -name "${PROJECT_PASCAL}ApplicationTests.java" \
		-execdir mv {} "__SCAFFY_PROJECT_PASCAL__ApplicationTests.java" \;

	cd "$WORKSPACE"
	echo "==> writing $ARTIFACTS_DIR/spring-boot.zip"
	rm -f "$ARTIFACTS_DIR/spring-boot.zip"
	zip_dir "spring-boot-template" "$ARTIFACTS_DIR/spring-boot.zip"
}

# ---------------------------------------------------------------------------
# NestJS (via @nestjs/cli new)
# ---------------------------------------------------------------------------
generate_nestjs() {
	echo
	echo "==> generating nestjs (nest new @ $NESTJS_CLI_VERSION)"
	cd "$WORKSPACE"

	# nest new is interactive unless --package-manager is provided.
	npx -y "@nestjs/cli@$NESTJS_CLI_VERSION" new "$PROJECT_KEBAB" \
		--package-manager npm \
		--skip-git

	cd "$PROJECT_KEBAB"

	if [[ "$SKIP_VALIDATE" != "1" ]]; then
		echo "==> validating nestjs (npm run build)"
		npm run build --silent
		rm -rf node_modules dist
	fi

	echo "==> tokenizing nestjs sources"
	tokenize_text_files \
		-not -path "./node_modules/*" \
		-not -path "./dist/*"

	cd "$WORKSPACE"
	echo "==> writing $ARTIFACTS_DIR/nestjs.zip"
	rm -f "$ARTIFACTS_DIR/nestjs.zip"
	zip_dir "$PROJECT_KEBAB" "$ARTIFACTS_DIR/nestjs.zip"
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
case "$STACK" in
	all)         generate_angular; generate_vue; generate_react; generate_dotnet; generate_nestjs; generate_spring_boot ;;
	angular)     generate_angular ;;
	vue)         generate_vue ;;
	react)       generate_react ;;
	dotnet)      generate_dotnet ;;
	nestjs)      generate_nestjs ;;
	spring-boot) generate_spring_boot ;;
	*)           echo "Unknown STACK: $STACK" >&2; exit 2 ;;
esac

echo
echo "==> done"
ls -la "$ARTIFACTS_DIR"
