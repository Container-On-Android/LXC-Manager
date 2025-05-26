# Ensure an argument is provided
if [ -z "$1" ]; then
    echo "Usage: \$EXEC <executable>"
    exit 1
fi

echo "dadadhddhadh打电话的好哇"

CURRENT_PATH="./$1"


if [ -f "$BIN/$1" ]; then
    EXEC_PATH=$(realpath "$BIN/$1")
elif [ -f "$CURRENT_PATH" ]; then
    EXEC_PATH=$(realpath "$CURRENT_PATH")
else
    echo "Error: '$1' not found in $BIN or current directory."
    exit 1
fi

# Execute the found file with $LINKER
shift
$LINKER "$EXEC_PATH" "$@"

