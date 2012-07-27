#!/bin/sh

adb push cert-blacklist.apk /mnt/sdcard/
adb shell su -c cp /mnt/sdcard/cert-blacklist.apk /system/app/
sleep 3
adb shell am start -a android.intent.action.MAIN -n org.nick.certblacklist/org.nick.certblacklist.MainActivity

