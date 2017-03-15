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

The project also incorporates source code from another two open source projects:

ZBarAndroidSDK
Fyi, that source code is released under an apache, v2 license. Also fyi, I got the source
code from: https://github.com/chentao0707/ZBarAndroidSDK/archive/master.zip

and,
ethereumj-core
Fyi, that source code is released under an MIT license. Also fyi, I got the source
code from: https://github.com/ethereum/ethereumj

This is all just info; the source code for both projects is incorporated into the
project, and rebuilt.

