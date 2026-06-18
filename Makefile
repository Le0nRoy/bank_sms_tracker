# BankSMSTracker Makefile
# Run `make help` to see all available targets

.PHONY: help build clean lint test test-unit test-android test-appium test-smoke test-all \
        coverage install install-fresh update-adb update-apk restore-adb keystore-init gradle-cache-ensure run appium-start appium-stop appium-docker-start appium-docker-stop \
        cluster-start cluster-stop cluster-status \
        allure-install allure-report allure-serve

# Default target
.DEFAULT_GOAL := help

# Colors for terminal output
BLUE := \033[34m
GREEN := \033[32m
YELLOW := \033[33m
RED := \033[31m
NC := \033[0m # No Color

#------------------------------------------------------------------------------
# Help
#------------------------------------------------------------------------------

help: ## Show this help message
	@echo "$(BLUE)BankSMSTracker - Available Make Targets$(NC)"
	@echo ""
	@echo "$(GREEN)Build:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -E '(build|clean|install)' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Linting:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -E 'lint' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Testing:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -E 'test|coverage' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Appium:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -E 'appium' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Cluster (Docker):$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -E 'cluster' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Allure:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -E 'allure' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Other:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -vE '(build|clean|install|lint|test|coverage|appium|allure)' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'

#------------------------------------------------------------------------------
# Build targets
#------------------------------------------------------------------------------

gradle-cache-ensure: ## Start gradle-cache container if not already running (no-op if Docker unavailable)
	@if docker info >/dev/null 2>&1; then \
		if ! docker ps --filter name=gradle-build-cache --filter status=running -q | grep -q .; then \
			echo "$(BLUE)Starting gradle-cache container...$(NC)"; \
			docker compose up -d gradle-cache; \
		fi; \
	else \
		echo "$(YELLOW)Docker not available — building without remote cache$(NC)"; \
	fi

build: gradle-cache-ensure ## Build debug APK (auto-starts gradle-cache for remote caching)
	./gradlew assembleDebug --no-daemon

build-release: gradle-cache-ensure ## Build release APK (auto-starts gradle-cache for remote caching)
	./gradlew assembleRelease --no-daemon

clean: ## Clean build artifacts
	./gradlew clean --no-daemon

install: ## Install debug APK on connected device/emulator
	./gradlew installDebug --no-daemon

keystore-init: ## Generate project-local debug keystore (run once; never commit the .keystore file)
	@if [ -f keystore/debug.keystore ]; then \
		echo "$(GREEN)keystore/debug.keystore already exists — skipping.$(NC)"; \
	else \
		echo "$(BLUE)Generating keystore/debug.keystore...$(NC)"; \
		keytool -genkeypair \
			-keystore keystore/debug.keystore \
			-alias androiddebugkey \
			-keyalg RSA -keysize 2048 -validity 10000 \
			-storepass android -keypass android \
			-dname "CN=BankSMSTracker Debug, O=Debug, C=US" \
			-storetype pkcs12; \
		echo "$(GREEN)keystore/debug.keystore created.$(NC)"; \
		echo "$(YELLOW)Keep this file safe — share it via the signing server, not git.$(NC)"; \
	fi

install-fresh: build ## Uninstall (wipe data) then install current debug build
	adb uninstall com.example.banksmstracker || true
	./gradlew installDebug --no-daemon

update-adb: build ## Build and install via ADB, PRESERVING app data and DB
	@echo "$(BLUE)Installing update via ADB (data preserved)...$(NC)"
	@if adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -q "Success"; then \
		echo "$(GREEN)Update installed. App data and databases preserved.$(NC)"; \
	else \
		echo "$(YELLOW)Signature mismatch — backing up data, reinstalling, restoring...$(NC)"; \
		adb shell "run-as com.example.banksmstracker sh -c 'cp -r databases /sdcard/bst_db_backup 2>/dev/null; cp -r shared_prefs /sdcard/bst_prefs_backup 2>/dev/null; echo ok'" || true; \
		adb uninstall com.example.banksmstracker || true; \
		echo "$(YELLOW)>>> WATCH YOUR PHONE — tap 'Install' if a confirmation dialog appears <<<$(NC)"; \
		if adb install app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -q "Success"; then \
			adb shell "run-as com.example.banksmstracker sh -c 'cp -r /sdcard/bst_db_backup/. databases/ 2>/dev/null; cp -r /sdcard/bst_prefs_backup/. shared_prefs/ 2>/dev/null; echo ok'" || true; \
			adb shell rm -rf /sdcard/bst_db_backup /sdcard/bst_prefs_backup 2>/dev/null || true; \
			echo "$(GREEN)Update installed with data restored.$(NC)"; \
		else \
			echo "$(RED)Install failed. App is uninstalled. Your data backup is at /sdcard/bst_db_backup and /sdcard/bst_prefs_backup$(NC)"; \
			echo "$(RED)Fix the install issue, then run: make restore-adb$(NC)"; \
			exit 1; \
		fi; \
	fi

restore-adb: ## Restore DB backup from /sdcard after a failed update-adb (app must be installed)
	@echo "$(BLUE)Restoring data from /sdcard backup...$(NC)"
	adb shell "run-as com.example.banksmstracker sh -c 'cp -r /sdcard/bst_db_backup/. databases/ 2>/dev/null; cp -r /sdcard/bst_prefs_backup/. shared_prefs/ 2>/dev/null; echo ok'"
	adb shell rm -rf /sdcard/bst_db_backup /sdcard/bst_prefs_backup 2>/dev/null || true
	@echo "$(GREEN)Data restored.$(NC)"

update-apk: build ## Build APK and print its local path for manual transfer to phone
	@echo "$(GREEN)APK ready:$(NC) $(shell pwd)/app/build/outputs/apk/debug/app-debug.apk"

#------------------------------------------------------------------------------
# Linting targets
#------------------------------------------------------------------------------

lint: ## Run ktlint check
	./gradlew ktlintCheck --no-daemon

lint-fix: ## Run ktlint and auto-fix issues
	./gradlew ktlintFormat --no-daemon

#------------------------------------------------------------------------------
# Test targets
#------------------------------------------------------------------------------

test: test-unit ## Run unit tests (alias for test-unit)

test-unit: ## Run unit tests
	./gradlew testDebugUnitTest --no-daemon

test-android: ## Run Android instrumented tests (requires emulator)
	./gradlew connectedDebugAndroidTest --no-daemon

test-appium: ## Run Appium UI tests (requires Appium server + device/emulator)
	@echo "$(YELLOW)Note: Ensure Appium server is running and device/emulator is connected$(NC)"
	@# When Appium runs in Docker, pass APPIUM_APK_PATH so Appium installs fresh (clean state).
	@# When running with a native Appium, install manually first: make install
	APPIUM_APK_PATH=/apk/debug/app-debug.apk ./gradlew testDebugUnitTest --tests "*.appium.*" --no-daemon

test-smoke: ## Run smoke tests only — 1-2 tests per feature (requires Appium server + device/emulator)
	@echo "$(YELLOW)Running smoke tests (1-2 per feature)...$(NC)"
	APPIUM_APK_PATH=/apk/debug/app-debug.apk ./gradlew testDebugUnitTest \
		--tests "*.appium.MainNavigationAppiumTest.mainScreenDisplaysAppTitle" \
		--tests "*.appium.MainNavigationAppiumTest.mainScreenHasAllNavigationButtons" \
		--tests "*.appium.RegexBuilderAppiumTest.navigateToRegexBuilder" \
		--tests "*.appium.RegexBuilderAppiumTest.testRegexPatternMatching" \
		--tests "*.appium.RegexBuilderAppiumTest.presetAmountInsertPlaceholder" \
		--tests "*.appium.CategoryManagementAppiumTest.navigateToCategoriesScreen" \
		--tests "*.appium.CategoryManagementAppiumTest.addNewCategoryWithName" \
		--tests "*.appium.SenderManagementAppiumTest.navigateToSendersScreen" \
		--tests "*.appium.SenderManagementAppiumTest.addNewSenderWithName" \
		--tests "*.appium.SettingsAppiumTest.settingsScreenDisplaysThemeSection" \
		--tests "*.appium.SettingsAppiumTest.settingsScreenDisplaysLanguageSection" \
		--tests "*.appium.BugReportAppiumTest.navigateToBugReport" \
		--tests "*.appium.BugReportAppiumTest.enterBugDescription" \
		--tests "*.appium.CategoryCascadeAppiumTest.navigateToCategories" \
		--tests "*.appium.CategoryCascadeAppiumTest.recategorizeButtonExists" \
		--tests "*.appium.PaymentsFilterAppiumTest.navigateToPaymentsScreen" \
		--tests "*.appium.PaymentsFilterAppiumTest.senderFilterSpinnerExists" \
		--tests "*.appium.PaymentsFilterAppiumTest.merchantSearchFieldExists" \
		--tests "*.appium.PaymentsFilterAppiumTest.categorySelectButtonExists" \
		--tests "*.appium.IncomesAppiumTest.navigateToIncomesScreen" \
		--tests "*.appium.IncomesAppiumTest.senderFilterSpinnerExists" \
		--tests "*.appium.IncomesAppiumTest.startDateButtonExists" \
		--tests "*.appium.IncomesAppiumTest.sourceSearchFieldExists" \
		--tests "*.appium.IncomesAppiumTest.incomeReportButtonExists" \
		--tests "*.appium.SmsToPaymentFlowAppiumTest.createCategory" \
		--tests "*.appium.SmsToPaymentFlowAppiumTest.createSenderWithRule" \
		--tests "*.appium.MainNavigationAppiumTest.navigateToIncomesScreen" \
		--tests "*.appium.ExchangeRatesAppiumTest.navigateToExchangeRatesScreen" \
		--tests "*.appium.ExchangeRatesAppiumTest.currencyFilterButtonExists" \
		--tests "*.appium.ExchangeRatesAppiumTest.startDateButtonExists" \
		--tests "*.appium.ExchangeRatesAppiumTest.endDateButtonExists" \
		--tests "*.appium.ExchangeRatesAppiumTest.downloadMissingButtonExists" \
		--no-daemon

test-all: lint test-unit test-android ## Run all tests (lint + unit + android)
	@echo "$(GREEN)All tests completed!$(NC)"

#------------------------------------------------------------------------------
# Coverage targets
#------------------------------------------------------------------------------

coverage: ## Run unit tests with coverage report
	./gradlew testDebugUnitTest jacocoTestReport --no-daemon
	@echo "$(GREEN)Coverage report generated at: app/build/reports/jacoco/jacocoTestReport/html/index.html$(NC)"

coverage-verify: ## Verify coverage meets minimum threshold
	./gradlew jacocoCoverageVerification --no-daemon

#------------------------------------------------------------------------------
# Appium targets
#------------------------------------------------------------------------------

appium-install: ## Install Appium globally (requires npm)
	npm install -g appium
	appium driver install uiautomator2
	@echo "$(GREEN)Appium installed successfully!$(NC)"

appium-start: ## Start Appium server locally
	@echo "$(BLUE)Starting Appium server...$(NC)"
	appium &
	@sleep 3
	@echo "$(GREEN)Appium server started at http://localhost:4723$(NC)"

appium-stop: ## Stop Appium server
	@pkill -f "appium" || true
	@echo "$(YELLOW)Appium server stopped$(NC)"

appium-docker-start: ## Start Appium server in Docker
	@echo "$(BLUE)Starting Appium in Docker...$(NC)"
	docker compose up -d appium
	@sleep 5
	@echo "$(GREEN)Appium Docker container started$(NC)"

appium-docker-stop: ## Stop Appium Docker container
	docker compose stop appium
	@echo "$(YELLOW)Appium Docker container stopped$(NC)"

cluster-start: ## Start all services (Appium + Gradle build cache)
	@echo "$(BLUE)Starting full testing cluster...$(NC)"
	docker compose up -d
	@sleep 5
	@echo "$(GREEN)Cluster started: Appium (4723) + Gradle build cache (5071)$(NC)"

cluster-stop: ## Stop all services
	docker compose down
	@echo "$(YELLOW)All cluster services stopped$(NC)"

cluster-status: ## Show cluster service status
	docker compose ps

appium-status: ## Check Appium server status
	@curl -s http://localhost:4723/status | jq . || echo "$(RED)Appium server not running$(NC)"

#------------------------------------------------------------------------------
# Development targets
#------------------------------------------------------------------------------

run: install ## Build, install, and launch the app
	adb shell am start -n com.example.banksmstracker/.ui.MainActivity

emulator-list: ## List available Android emulators
	emulator -list-avds

emulator-start: ## Start default emulator (set AVD_NAME or uses first available)
	@AVD=$${AVD_NAME:-$$(emulator -list-avds | head -1)}; \
	if [ -z "$$AVD" ]; then \
		echo "$(RED)No AVD found. Create one in Android Studio.$(NC)"; \
	else \
		echo "$(BLUE)Starting emulator: $$AVD$(NC)"; \
		emulator -avd $$AVD &; \
	fi

devices: ## List connected Android devices/emulators
	adb devices

#------------------------------------------------------------------------------
# CI targets
#------------------------------------------------------------------------------

ci: lint test-unit coverage ## Run CI pipeline (lint + unit tests + coverage)
	@echo "$(GREEN)CI pipeline completed!$(NC)"

ci-full: lint test-unit coverage test-android ## Run full CI pipeline (includes instrumented tests)
	@echo "$(GREEN)Full CI pipeline completed!$(NC)"

#------------------------------------------------------------------------------
# Allure reporting targets
#------------------------------------------------------------------------------

allure-install: ## Install Allure CLI into project-local .venv (no global install)
	@echo "$(BLUE)Installing Allure CLI into .venv ...$(NC)"
	@python3 -m venv .venv
	@.venv/bin/pip install --quiet --upgrade pip
	@.venv/bin/pip install --quiet allure-pytest
	@echo "$(GREEN)Allure installed: $$(. .venv/bin/activate && allure --version)$(NC)"
	@echo "$(YELLOW)Note: .venv/ is project-local and listed in .gitignore$(NC)"

allure-report: ## Generate static Allure HTML report from last test run
	@echo "$(BLUE)Generating Allure report...$(NC)"
	@.venv/bin/allure generate app/build/allure-results -o app/build/reports/allure-report --clean
	@echo "$(GREEN)Report generated at: app/build/reports/allure-report/index.html$(NC)"

allure-serve: ## Open Allure report in browser (serves locally, no upload)
	@echo "$(BLUE)Opening Allure report in browser...$(NC)"
	@.venv/bin/allure serve app/build/allure-results

#------------------------------------------------------------------------------
# Quick shortcuts
#------------------------------------------------------------------------------

l: lint ## Shortcut for lint
u: test-unit ## Shortcut for unit tests
a: test-android ## Shortcut for android tests
c: coverage ## Shortcut for coverage
b: build ## Shortcut for build
