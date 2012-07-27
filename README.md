Jelly Bean certificate blacklisting
==============

Sample code for the 'Certificate blacklisting in Jelly Bean' article at 
http://nelenkov.blogspot.com/2012/07/certificate-blacklisting-in-jelly-bean.html

How to use:

N.B. The app needs system permissions so it needs to be installed on a 
rooted device or the emulator.

1. Create a CA with OpenSSL and issue an end entity certificate. 
2. Convert both the CA and EE certificates to DER from and name 
them 'cacert.cer' and 'keystore-test.cer' respectively.
(or modify source to match your names)
3. Copy them to the device's external storage root (usually /mnt/sdcard).
4. Mount the /system partition rw: 

```shell
  $ su
  # mount -o rw,remount /system
```

5. Sign and export the app into this directory. 
6. Run install.sh to install and start on the device. 
7. Push buttons to see what happens :)


