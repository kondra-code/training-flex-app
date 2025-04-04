#!/bin/bash
set -e -o pipefail

THIS_SCRIPT=$(realpath "$0")
THIS_SCRIPT_DIR=$(dirname "$THIS_SCRIPT")
TOP_DIR="${THIS_SCRIPT_DIR}/.."

# set plats for exactly the targets this project needs to compile for
PLATS="arm64"

source "${THIS_SCRIPT_DIR}/kos_version.source"

# URLs for the SDK packages to download
declare -A SDK_URL
SDK_URL["x64"]="https://sause2tcccknaprod0001.blob.core.windows.net/release/kos-cross-sdk-host.x64-tgt.x64-"
SDK_URL["arm64"]="https://sause2tcccknaprod0001.blob.core.windows.net/release/kos-cross-sdk-host.x64-tgt.arm64-"
SDK_URL["arm32"]="https://sause2tcccknaprod0001.blob.core.windows.net/release/kos-cross-sdk-host.x64-tgt.arm32-"

# Postfix on the SDK environment-setup script
declare -A SDK_ARCHS
SDK_ARCHS["x64"]="corei7-64-kos-linux"
SDK_ARCHS["arm64"]="cortexa57-kos-linux"
SDK_ARCHS["arm32"]="cortexa9t2hf-neon-kos-linux-gnueabi"

# Build each platform's native
if [ "$1" == "--forcesdkupdate" ]; then
	rm -rf "${TOP_DIR}/build/sdk"
fi

mkdir -p "${TOP_DIR}/build"
# configure the SDKs
for plat in ${PLATS}; do
	# Install the SDK
	cd "${TOP_DIR}/build"
	if [ -f "sdk/environment-setup-${SDK_ARCHS[${plat}]}" ]; then
		echo "${plat} sdk already installed, skipping..."
		continue
	fi
	mkdir -p sdk
	echo "Downloading ${SDK_URL[${plat}]}${SDK_VERSION}.sh"

	curl -f -o sdk/sdk-${plat}.sh "${SDK_URL[${plat}]}${SDK_VERSION}.sh"
	(cd sdk; chmod 755 sdk-${plat}.sh; ./sdk-${plat}.sh -d . -y) || exit 1	
done

for plat in ${PLATS}; do
	# kos-layer-core-native-dev
	echo "resolving core-native-dev artifact"
	KOS_NATIVE_DEV_URL="$(kos-resolve-market-artifact.sh kos-layer-core-native-dev "${plat}" "${SDK_VERSION}" kos-cdn)"
        cd "${TOP_DIR}/build"
	KOS_NATIVE_DEV_FILENAME="download/$(basename "${KOS_NATIVE_DEV_URL}")"
        mkdir -p download
	if [ ! -f "${KOS_NATIVE_DEV_FILENAME}" ]; then
           cd download
           curl -O "${KOS_NATIVE_DEV_URL}"
           cd ..
	fi
	rm -rf tmp
	mkdir -p tmp
	cd tmp
	kabtool -x "../${KOS_NATIVE_DEV_FILENAME}"
	rdsquashfs -u /. layer.img
	rm layer.img descriptor.json
	cp -r usr "../sdk/sysroots/${SDK_ARCHS[${plat}]}/"
done


