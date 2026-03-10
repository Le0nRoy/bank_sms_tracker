# BankSMSTracker Makefile
# Run `make help` to see all available targets

.PHONY: help build clean lint test test-unit test-android test-appium test-smoke test-all \
        coverage install run appium-start appium-stop appium-docker-start appium-docker-stop \
        cluster-start cluster-stop cluster-status

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
	@echo "$(GREEN)Other:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | grep -vE '(build|clean|install|lint|test|coverage|appium)' | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'

#------------------------------------------------------------------------------
# Build targets
#------------------------------------------------------------------------------

build: ## Build debug APK
	./gradlew assembleDebug --no-daemon

build-release: ## Build release APK
	./gradlew assembleRelease --no-daemon

clean: ## Clean build artifacts
	./gradlew clean --no-daemon

install: ## Install debug APK on connected device/emulator
	./gradlew installDebug --no-daemon

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
		--tests "*.appium.SmsToPaymentFlowAppiumTest.createCategory" \
		--tests "*.appium.SmsToPaymentFlowAppiumTest.createSenderWithRule" \
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
# Quick shortcuts
#------------------------------------------------------------------------------

l: lint ## Shortcut for lint
u: test-unit ## Shortcut for unit tests
a: test-android ## Shortcut for android tests
c: coverage ## Shortcut for coverage
b: build ## Shortcut for build
