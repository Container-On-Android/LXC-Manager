#!/system/bin/env sh

unset LXC_DIR
unset LXC_LD_DIR
unset LXC_BIN_DIR

su -c "exit" 2>/dev/null && : || { echo " [!] No su program was found on your device \n or the authorization request was denied \n perhaps you need to specify the su program \n manually in the settings"; exit 1; }

if [ "$LXC_CMD" = "lxc-attach" ]; then
    su -p -c env $LXC_CMD $LXC_ARG --clear-env --set-var TERM=xterm-256color --set-var COLORTERM=truecolor
else
    su -p -c env $LXC_CMD $LXC_ARG
fi