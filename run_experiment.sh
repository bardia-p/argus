#!/bin/bash

RUNS=30

for ((i=1; i<=RUNS; i++)); do
    echo "Run #$i starting..."

    gradle runServer

    echo "Finished experiment. Killing Java processes..."

    pkill -f java   # or the safer method below

    echo "Cleanup done."
    echo "----------------------"
done