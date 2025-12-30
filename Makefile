# BankSMSTracker Makefile
# Run `make help` to see all available targets

.PHONY: help build clean lint test test-unit test-android test-appium test-all \
        coverage install run appium-start appium-stop appium-docker-start appium-docker-stop

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

test-appium: ## Run Appium UI tests (requires Appium server + emulator)
	@echo "$(YELLOW)Note: Ensure Appium server is running and emulator is connected$(NC)"
	./gradlew test --tests "*.appium.*" --no-daemon

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
	docker-compose -f docker-compose.appium.yml up -d
	@sleep 5
	@echo "$(GREEN)Appium Docker container started$(NC)"

appium-docker-stop: ## Stop Appium Docker container
	docker-compose -f docker-compose.appium.yml down
	@echo "$(YELLOW)Appium Docker container stopped$(NC)"

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
