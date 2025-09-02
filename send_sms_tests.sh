#!/bin/bash

set -eu

# Script to send SMS messages to the Android emulator based on test cases.

# Path to the JSON file containing the test SMS data.
JSON_FILE="app/src/test/resources/sms_tests.json"

# Check if jq is installed. jq is a lightweight and flexible command-line JSON processor.
if ! command -v jq &> /dev/null
then
    echo "jq could not be found. Please install it to run this script."
    exit 1
fi

# Check if adb is available.
if ! command -v adb &> /dev/null
then
    echo "adb could not be found. Please ensure the Android SDK platform-tools are in your PATH."
    exit 1
fi

echo "Checking for running emulators or devices..."
# Check if there are any devices/emulators connected.
if [[ -z $(adb devices | tail -n +2 | grep "device") ]]; then
    echo "No running emulator or connected device found. Please start an emulator or connect a device."
    exit 1
fi

echo "Found a device/emulator. Proceeding to send SMS messages."

# Read the JSON file and iterate over each test case.
# For each test case, iterate over each address in the "addresses" array.
# Addresses containing spaces are ignored.
# The `-c` option for jq outputs each JSON object on a single line.
# The `-r` option for jq outputs the raw string, not a JSON-escaped string.
jq -c -r '.[] | .rawMessage as $msg | .address? | select(. != null) | select(contains(" ") | not) | [., $msg] | @tsv' "${JSON_FILE}" |
while IFS=$'\t' read -r address rawMessage; do
    echo "----------------------------------------"
    echo "Sending SMS from: ${address}"
    echo "Message: $(printf '%s' "$rawMessage")"

    # Use adb to send the SMS to the emulator.
    # The `emu sms send` command simulates receiving an SMS.
    adb emu sms send "${address}" "$(printf '%s' "$rawMessage")"

    if [ $? -eq 0 ]; then
        echo "SMS sent successfully."
    else
        echo "Failed to send SMS."
    fi
done

echo "----------------------------------------"
echo "All test SMS messages have been processed."

set +eu
