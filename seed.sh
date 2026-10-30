#!/bin/sh
# Reset the database to the original demo data (stop the server first)
exec "$(dirname "$0")/run.sh" --seed
