# Cooja v4.9

## Cooja User Interface Changes

### Deprecated `-nogui` parameter

Graphical/headless mode is now controlled by separate parameter (`--[no-]gui`),
so `-nogui` has been deprecated. The old behavior for `-nogui=file.csc` is now
accomplished with `--quickstart=file.csc --no-gui`.

### Double dash before long command line options

The implementation of the command line option `--[no-]gui` was required to use
a double dash, so the other command line options have been converted to also
use a double dash for consistency.

## Cooja API changes for plugins outside the main tree

### Contiki-NG-specific moved from MoteType to BaseContikiMoteType

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
