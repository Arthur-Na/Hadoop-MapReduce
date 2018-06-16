#!/bin/bash
while IFS='' read -r line || [[ -n "$line" ]]; do
    #echo "Text read from file: $line"
    ssh-copy-id -i ~/.ssh/hadoop_tp hadoop@$line
done < "$1"