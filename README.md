Jelly Bean certificate blacklisting
==============

Sample code for the 'Certificate blacklisting in Jelly Bean' article at http://nelenkov.blogspot.com/2012/07/certificate-blacklisting-in-jelly-bean.html

How to use:

N.B. The app needs system permissions so it needs to be installed on a 
rooted device or the emulator.

1. Create a CA with OpenSSL and issue an end entity certificate. 
2. Convert both the CA and EE certificates to DER from and copy to 
the device's external storage root (usually /mnt/sdcard).
3. Mount the /system partition rw. 
    $ su
    # mount -o rw,remount /system
4. Sign and export the app into this directory. 
5. Use install.sh to install and start on the device. 
6. Push buttons to see what happens :)


