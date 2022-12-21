# Cooja v4.9

## Cooja User Interface Changes

### Deprecated `-nogui` and `-quickstart` parameters

Graphical/headless mode is now controlled by separate parameter (`--[no-]gui`),
so `-nogui` and `-quickstart` have been deprecated. The old behavior for
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
