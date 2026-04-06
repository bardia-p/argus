#!/bin/bash

RUNS=50

for ((i=1; i<=RUNS; i++)); do
    echo "Run #$i starting..."

    start_time=$(date +%s)

    gradle runServer > /dev/null 2>&1 &
    PID=$!

    spin='-\|/'
    s=0

    while kill -0 $PID 2>/dev/null; do
        now=$(date +%s)
        elapsed=$((now - start_time))

        s=$(( (s+1) % 4 ))

        printf "\r[%c] Elapsed: %02d:%02d" "${spin:$s:1}" $((elapsed/60)) $((elapsed%60))
        sleep 0.2
    done

    wait $PID
    printf "\r[✓] Elapsed: %02d:%02d\n" $((elapsed/60)) $((elapsed%60))

    echo "Finished experiment. Killing Java processes..."
    pkill -f java

    echo "Cleanup done."
    echo "----------------------"
done