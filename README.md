# The Cooja Network Simulator

Cooja is an open source network simulator.

## MSPSim support for the Cooja Simulator

MSPSim is a Java-based instruction level emulator of the MSP430 series
microprocessor and emulation of some sensor networking platforms. It is used
by Cooja to emulate MSP430 based platforms and is part of the Cooja
source code.

## Building

To build Cooja, you can run `./gradlew build`. Cooja is then provided in `build/libs/cooja.jar` as a JAR file. The dependencies are located in `build/libs/lib`.

To build Cooja easily executable and with all dependencies, you can use the following command:
```
./gradlew distTar
```
or
```
./gradlew distZip
```
This command creates a compressed folder in `build/distributions/` which contains both the JAR file and a platform-independent script for execution.
