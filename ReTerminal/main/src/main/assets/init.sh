#!/system/bin/env sh

unset LXC_DIR
unset LXC_LD_DIR
unset LXC_BIN_DIR

lxc_dir_custom="$LXC_DIR"
lxc_dir_share="/data/share"
lxc_dir_lxc="/data/lxc"

if [ "$1" = "0" ]; then
   result=$(sh "$PREFIX/local/bin/rish" -c "/system/bin/app_process -Djava.class.path=\"$PKG_PATH\" /system/bin com.rk.shell.Installer" "$2")

   sh "$PREFIX/local/bin/rish" -c "
       mkdir -p /data/local/tmp/ReTerminal/$2
       export LD_LIBRARY_PATH=/data/local/tmp/ReTerminal
       export PROOT_TMP_DIR=/data/local/tmp/ReTerminal/$2
       /data/local/tmp/ReTerminal/proot $result /bin/login -f root
   "

elif [ "$1" = "1" ]; then
    sh "$PREFIX/local/bin/rish"
elif [ "$1" = "2" ]; then
  su -c "exit" 2>/dev/null && : || { echo " [!] No su program was found on your device \n or the authorization request was denied, \n perhaps you need to specify the su program \n manually in the settings"; exit 1; }

  for dir in "$lxc_dir_custom" "$lxc_dir_share" "$lxc_dir_lxc"; do
    if [[ -z "$dir" ]]; then
            continue
    fi
    output=$(ls "$dir" 2>&1)
    if [[ "$output" == *"Permission denied"* ]]; then
        echo "Detected existing directory: $dir"
        LXC_DIR="$dir"
        LXC_LD_DIR="$dir/lib:$dir/lib64:/data/sysroot/lib:/data/sysroot/lib64:$LD_LIBRARY_PATH"
        LXC_BIN_DIR="$dir/bin:$dir/libexec/lxc:$PATH"
        break
    elif [[ "$output" == *"No such file or directory"* ]]; then
        continue
    fi
  done

  env -i LD_LIBRARY_PATH=$LXC_LD_DIR PATH=$LXC_BIN_DIR HOME=$LXC_DIR su -m
elif [ "$1" = "3" ]; then
    result=$(su -c "/system/bin/app_process -Djava.class.path=\"$PKG_PATH\" /system/bin com.rk.shell.Installer" "$2")
    su -c "
           mkdir -p /data/local/tmp/ReTerminal/$2
           export LD_LIBRARY_PATH=/data/local/tmp/ReTerminal
           export PROOT_TMP_DIR=/data/local/tmp/ReTerminal/$2
           /data/local/tmp/ReTerminal/proot $result /bin/login -f root
       "
else
    echo "Unknown working mode $1"
fi


