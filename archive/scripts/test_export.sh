#!/bin/bash

echo "Starting listings export test with dynamic caching..."
echo "This may take 3-4 minutes to complete."
echo "Output will be saved to test_export.log"
echo ""

# Run the export and save output to log file
./gradlew autoDiscover -Pcollection=listings > test_export.log 2>&1 &
PID=$!

echo "Export started with PID: $PID"
echo "You can monitor progress with: tail -f test_export.log"
echo ""
echo "To check if it's still running: ps -p $PID"
echo "To see the results when done: cat test_export.log | grep -E '(PHASE|Caching|Collections cached|Export complete|Total time)'"