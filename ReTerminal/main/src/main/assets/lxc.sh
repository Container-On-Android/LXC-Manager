#!/system/bin/env sh

unset LXC_DIR
unset LXC_LD_DIR
unset LXC_BIN_DIR

lxc_dir_custom="$LXC_DIR"
lxc_dir_share="/data/share"
lxc_dir_lxc="/data/lxc"

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

  env -i LD_LIBRARY_PATH=$LXC_LD_DIR PATH=$LXC_BIN_DIR HOME=$LXC_DIR LXC_CMD=$LXC_CMD LXC_ARG=$LXC_ARG su -m -c env $LXC_CMD $LXC_ARG