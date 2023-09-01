# Cooja v5.0

## Cooja User Interface Changes

## Cooja Simulation File Changes

### Expand $(MAKE) in csc files

Simulation file version 2023090101 introduces expansion of $(MAKE).

# Cooja v4.9

## Cooja User Interface Changes

### MSPSim and Cooja started from same Main

MSPSim is started when `org.contikios.cooja.Main` is passed the command line parameter
`--platform=<platform>`, otherwise Cooja is started. There is nothing that prevents
Cooja-parameters from being passed when starting MSPSim, but they will be ignored
by MSPSim.

### Switched from Log4J 2 to SLF4J and Logback

This removes the `--log4j2` and `--logname` parameters. Set the system property
`logback.configurationFile` to use a different logback configuration.

To start Cooja with a custom Logback configuration:
```
./gradlew -Dcooja.logback.configurationFile=my-logback-configuration.xml run
```

Plugins need to change calls to `logger.fatal` to instead call `logger.error`,
and change the logger construction from
```
private static final Logger logger = LogManager.getLogger(MyClass.class);
```
to
```
private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
```
Plugins also need to update their imports, change
```
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
```
to
```
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

### Removed `-nogui` and `-quickstart` parameters

Graphical/headless mode is now controlled by separate parameter (`--[no-]gui`),
so `-nogui` and `-quickstart` have been removed. The old behavior for
`-nogui=file.csc` is now accomplished with `--no-gui file.csc`, and
`-quickstart=file.csc` is now accomplished with `file.csc`.

### Renamed `--external_tools_config` parameter to `--config`

This parameter specifies a configuration file not only with user specified
external tools, but also other user settings such as window location,
file history, etc.

### Double dash before long command line options

The implementation of the command line option `--[no-]gui` was required to use
a double dash, so the other command line options have been converted to also
use a double dash for consistency.

## Cooja API changes for plugins outside the main tree

### Removed removedLogOutput in LogOutputListener

Plugins that need information on when pruning happens can implement the functionality
internally, either by maintaining a counter, or keeping a full copy of the events.

### Removed C_SOURCES support in ContikiMoteType

No immediate replacement, report a bug on Cooja in the Contiki-NG repository
if you need this feature.

### Removed Observable from Log interface

Call addTrigger/removeTrigger/deleteTriggers on `getLogDataTriggers()` instead.

### Removed addRadioTransmissionObserver/removeRadioTransmissionObserver in RadioMedium

Call addTrigger/removeTrigger/deleteTriggers on `getRadioTransmissionTriggers()`
instead.

### Removed addRadioMediumObserver/removeRadioMediumObserver in AbstractRadioMedium

Call addTrigger/removeTrigger/deleteTriggers on `getRadioMediumTriggers()`
instead.

### Removed addMoteHighlightObserver/removeMoteHighlightObserver in Cooja

Call addTrigger/removeTrigger/deleteTriggers on
`simulation.getMoteHighlightTriggers()` instead.

### Removed observable from SerialPort interface

Use `getSerialDataTriggers()` instead.

### Changed return type of getMoteInterfaceClasses in MoteType interface

The method now returns a list instead of an array. The method implementation
in AbstractApplicationMoteType should be usable for all motes.

### Removed MoteCountListener from SimEventCentral

Use `simulation.getMoteTriggers().addTrigger()` instead.

### Removed MOTE_INTERFACES support in ContikiMoteType

Extend ContikiMoteType and override the methods that deal with interfaces
instead.

### Removed mote interface RimeAddress

The interface is no longer useful as Contiki-NG does not support Rime.

### Removed Observable from Position, LED, and addresses mote interfaces

Observable in these mote interfaces have been replaced by event triggers,
accessible by methods such as `getPositionTriggers()` or `getTriggers()`.

### Moved getExecutableAddressOf from WatchpointMote to MoteType

Call the method with `mote.getType().getExecutableAddressOf(file, line)`
instead of `mote.getExecutableAddressOf(file, line)`.

### Add added/removed methods to Mote interface

Cooja calls these methods when motes are added and removed from the simulation.

### Added MoteInterfaceHandler, MemoryInterface, and MoteType to AbstractWakeupMote

AbstractWakeupMote now provides implementations of `getID`.
`getInterfaces`, `getMemory`, and `getType`. Motes still need to allocate
the MoteInterfaceHandler and MemoryInterface in their constructor.

### Move handling of mote startup delay to the Clock interface

The Clock interface now expects to be created with a Mote as argument
and is now responsible to set the mote startup delay. This enables
a subclass to override this behaviour if desired.

### Moved handling of radio register to the Radio interface

The Radio interface will now register the radio to the radio medium
via its `added()` method instead of the simulation. This is in preparation
to support multiple radios.

### Removed addMoteRelationsObserver/deleteMoteRelationsObserver from Cooja

Use `simulation.getMoteRelationsTriggers()` to get the object where triggers
can be managed. The available methods are similar to the previous Observable
interface, except an explicit owner is passed in (usually `this` in the caller):
`addTrigger`/`removeTrigger`/`deleteTriggers`.

### Removed simulationFinishedLoading from RadioMedium interface

Cooja now calls setConfigXML on the radio medium last, so the hook is
no longer required. Update by moving the body of simulationFinishedLoading
into setConfigXML.

### LogOutputListener interface no longer extends MoteCountListener

Plugins that need to observe both logs and motes added/removed should
install separate listeners for the two.

### RadioMedium converted to interface

RadioMediums should now implement the interface instead of extending the class.

### Removed setIdentifier/setMoteInterfaceClasses in MoteType

These methods are not required by Cooja itself, so make it a mote-internal
question of how to implement the support.

### Converted MoteInterface/Beeper/Button/MoteID into interfaces

To reduce the scope of the deprecated Observable, MoteInterface was converted
from a class to an interface. This also forced Beeper, Button and MoteID
to be made into interfaces. The abstract class Button was renamed to
AbstractButton and resides inside the Button interface.

### Removed registerMote/unregisterMote in RadioMedium

Use registerRadioInterface/unregisterRadioInterface instead.

### Removed getMoteType(String identifier) method in Simulation

The mote type identifier is no longer exposed in the simulation files,
so the method is no longer used by Cooja. The mote type can be accessed
through a mote by calling the `getType()` method.

### Method updates in MoteTypeCreationException

The `hasCompilationOutput` and `setCompilationOutput` methods were removed from
MoteTypeCreationException. The setter was replaced with constructors that accept
the compilation output as the third argument.

### Contiki-NG-specific methods moved from MoteType to BaseContikiMoteType

Contiki-NG specific methods were moved from MoteType to BaseContikiMoteType
([#857](https://github.com/contiki-ng/cooja/pull/857)).

The following method implementations can safely be removed from mote other types:

* `getContikiSourceFile`/`setContikiSourceFile`
* `getContikiFirmwareFile`/`setContikiFirmwareFile`
* `getCompileCommands`/`setCompileCommands`

Plugins that need access to the getters can still access them through:

```Java
if (mote.getType() instanceof BaseContikiMoteType moteType) {
   var sourceFile = moteType.getContikiSourceFile();
   // ...
}
```

### Strongly typed PluginType Annotation

The PluginType annotation was encapsulated in an enum to give Cooja exhaustiveness
checking in switch-expressions ([#848](https://github.com/contiki-ng/cooja/pull/848)).
Plugins need to be updated by adding `.PType` in the PluginType annotation in plugins,
so change from
```Java
@PluginType(PluginType.X)
public class Y { /* ... */ }
```
into
```Java
@PluginType(PluginType.PType.X)
public class Y { /* ... */ }
```

### Update from JDOM 1 to JDOM 2

JDOM was upgraded from version 1 to version 2 ([#784](https://github.com/contiki-ng/cooja/pull/784)).
This requires some source code updates, but since Cooja uses such a small subset
of the JDOM API, the update can be done automatically with the command:

```bash
find <directory> -name \*.java -exec perl -pi -e 's#import org.jdom.#import org.jdom2.#g' {} \;
```

### Avoid starting the AWT thread in headless mode

Cooja will no longer start plugins that extend `VisPlugin` in headless mode
to avoid starting the AWT thread. Plugins that should run in both GUI mode
and headless mode need to be updated to keep the JInternalFrame internal.
Examples for PowerTracker and other plugins can be found in the PR
([#261](https://github.com/contiki-ng/cooja/pull/261)).

## Changelog

* Mobility plugin added to Cooja ([#768](https://github.com/contiki-ng/cooja/pull/768))

All [commits](https://github.com/contiki-ng/cooja/compare/630e719d01d3...master) since v4.8.
