# EtherPay
Ether Wallet app for Android


# Building
The EtherPay app uses a batch of open source libraries. Most of these are downloaded
directly from MavenCentral from the build.gradle. These include:

https://mvnrepository.com/artifact/com.madgag.spongycastle/core
https://mvnrepository.com/artifact/commons-codec/commons-codec
https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
https://mvnrepository.com/artifact/org.slf4j/slf4j-android
https://github.com/kenglxn/QRGen

The project also incorporates source code from another open source project, ZBarAndroidSDK.
Fyi, that source code is released under an apache, v2 license. Also fyi, I got the source
code from: https://github.com/chentao0707/ZBarAndroidSDK/archive/master.zip
This is all just info; the source code is incorporated into the project, and rebuilt.



However, there is one open source libraries that requires a little bit of intervention:

ethereumj-core-1.4.0-SNAPSHOT.jar, from
https://oss.jfrog.org/libs-snapshot/org/ethereum/ethereumj-core/1.4.0-SNAPSHOT/ethereumj-core-1.4.0-20170124.094925-149.zip
(MIT License)

INSTRUCTIONS TO INSTALL ETHEREUMJ-CORE
==================================
ETHERPAY_ROOT=$(pwd)
cd ~/tmp
wget https://oss.jfrog.org/libs-snapshot/org/ethereum/ethereumj-core/1.4.0-SNAPSHOT/ethereumj-core-1.4.0-20170124.094925-149.zip
unzip ethereumj-core-1.4.0-20170124.094925-149.zip
if [ ! -d ${ETHERPAY_ROOT}/app/libs ]; then \
   mkdir ${ETHERPAY_ROOT}/app/libs;         \
fi
cp ethereumj-core-1.4.0-SNAPSHOT/lib/ethereumj-core-1.4.0-SNAPSHOT.jar ${ETHERPAY_ROOT}/app/libs/
cd $ETHERPAY_ROOT
