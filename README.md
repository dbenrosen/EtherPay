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

and ethereumj-core-1.4.0-SNAPSHOT.jar, from
https://oss.jfrog.org/libs-snapshot/org/ethereum/ethereumj-core/1.4.0-SNAPSHOT/ethereumj-core-1.4.0-20170124.094925-149.zip
(MIT License)

and ZBarAndroidSDK from a recompile of ZBarAndroidSDK (Apache License V2.0, January 2004).

INSTRUCTIONS TO INSTALL THESE LIBS FOLLOW:


==================================
To get ethereumj-core:
ETHERPAY_ROOT=$(pwd)
cd ~/tmp
wget https://oss.jfrog.org/libs-snapshot/org/ethereum/ethereumj-core/1.4.0-SNAPSHOT/ethereumj-core-1.4.0-20170124.094925-149.zip
unzip ethereumj-core-1.4.0-20170124.094925-149.zip
if [ ! -d ${ETHERPAY_ROOT}/app/libs ]; then \
   mkdir ${ETHERPAY_ROOT}/app/libs;         \
fi
cp ethereumj-core-1.4.0-SNAPSHOT/lib/ethereumj-core-1.4.0-SNAPSHOT.jar ${ETHERPAY_ROOT}/app/libs/
cd $ETHERPAY_ROOT


==================================
To get ZBarAndroidSDK:
ETHERPAY_ROOT=$(pwd)
cd ~/tmp
wget https://github.com/chentao0707/ZBarAndroidSDK/archive/master.zip
unzip master.zip
cd ZBarAndroidSDK-master/
if [ ! -d ${ETHERPAY_ROOT}/app/libs ]; then \
   mkdir ${ETHERPAY_ROOT}/app/libs;         \
fi
cp ZBarScanProjAll/libs/ZBarDecoder.jar ${ETHERPAY_ROOT}/app/libs/
if [ ! -d ${ETHERPAY_ROOT}/app/src/main/jniLibs ]; then \
   mkdir ${ETHERPAY_ROOT}/app/src/main/jniLibs;         \
fi
cp -R ZBarBuild/libs/* ${ETHERPAY_ROOT}/app/src/main/jniLibs/
cd $ETHERPAY_ROOT



