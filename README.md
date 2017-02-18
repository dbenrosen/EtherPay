# EtherPay
Ether Wallet app for Android


# Building
The EtherPay app uses a batch of open source libraries. Most of these are downloaded
directly from MavenCentral or GitHUb from the build.gradle. These include:

https://mvnrepository.com/artifact/com.madgag.spongycastle/core
https://mvnrepository.com/artifact/commons-codec/commons-codec
https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
https://mvnrepository.com/artifact/org.slf4j/slf4j-android
https://github.com/kenglxn/QRGen


However, there are two open source libraries that require a little bit of intervention.
These are 

zbar.jar, from
https://sourceforge.net/projects/zbar/files/AndroidSDK/ZBarAndroidSDK-0.1.zip
(GNU Library or Lesser General Public License version 2.0 (LGPLv2)

and ethereumj-core-1.4.0-SNAPSHOT.jar, from
https://oss.jfrog.org/libs-snapshot/org/ethereum/ethereumj-core/1.4.0-SNAPSHOT/ethereumj-core-1.4.0-20170124.094925-149.zip
(MIT License)


To get zbar.jar:

ETHERPAY_ROOT=$(pwd)
cd ~/tmp
wget https://sourceforge.net/projects/zbar/files/AndroidSDK/ZBarAndroidSDK-0.1.zip
unzip ZBarAndroidSDK-0.1.zip
cp ZBarAndroidSDK-0.1/libs/zbar.jar ${ETHERPAY_ROOT}/app/libs/


To get ethereumj-core:
ETHERPAY_ROOT=$(pwd)
cd ~/tmp
wget https://oss.jfrog.org/libs-snapshot/org/ethereum/ethereumj-core/1.4.0-SNAPSHOT/ethereumj-core-1.4.0-20170124.094925-149.zip
unzip ethereumj-core-1.4.0-20170124.094925-149.zip
cp ethereumj-core-1.4.0-SNAPSHOT/lib/ethereumj-core-1.4.0-SNAPSHOT.jar ${ETHERPAY_ROOT}/app/libs/

