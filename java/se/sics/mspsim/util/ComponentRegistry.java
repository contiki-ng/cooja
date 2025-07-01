/*
 * Copyright (c) 2008-2012, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * This file is part of MSPSim.
 *
 * -----------------------------------------------------------------
 *
 * Author  : Joakim Eriksson
 * Created : Mon Feb 11 2008
 */
package se.sics.mspsim.util;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class ComponentRegistry {

    private final ArrayList<ComponentEntry> components = new ArrayList<>();
    private boolean running;

    public ComponentRegistry(ComponentEntry... entries) {
        for (var e : entries) {
            registerComponent(e.name, e.component);
        }
    }

    private synchronized ComponentEntry[] getAllEntries() {
        return components.toArray(new ComponentEntry[0]);
    }

    public void registerComponent(String name, Object component) {
        if (name == null || component == null) {
            throw new NullPointerException();
        }
        synchronized (this) {
            components.add(new ComponentEntry(name, component));
        }
        if (component instanceof ActiveComponent ac) {
            ac.init(name, this);
            if (running) {
                ac.start();
            }
        } else if (component instanceof ServiceComponent sc) {
            sc.init(name, this);
        }
    }

    public synchronized Object getComponent(String name) {
        for (ComponentEntry entry : components) {
            if (name.equals(entry.name)) {
                return entry.component;
            }
        }
        return null;
    }

    public synchronized boolean removeComponent(String name) {
      ComponentEntry rem = null;
        for (ComponentEntry entry : components) {
            if (name.equals(entry.name)) {
              rem = entry;
              break;
            }
        }
        if (rem == null) {
          return false;
        }
        components.remove(rem);
        return true;
    }

    public synchronized Object[] getAllComponents(String name) {
        ArrayList<Object> list = new ArrayList<>();
        for (ComponentEntry entry : components) {
            if (name.equals(entry.name)) {
                list.add(entry.component);
            }
        }
        return list.toArray();
    }

    public synchronized <T> T getComponent(Class<T> type, String name) {
        for (ComponentEntry entry : components) {
            if (type.isInstance(entry.component) && name.equals(entry.name)) {
                return type.cast(entry.component);
            }
        }
        return null;
    }

    public synchronized <T> List<T> getAllComponents(Class<T> type, String name) {
        ArrayList<T> list = new ArrayList<>();
        for (ComponentEntry entry : components) {
            if (type.isInstance(entry.component) && name.equals(entry.name)) {
                list.add(type.cast(entry.component));
            }
        }
        return list;
    }

    public synchronized <T> T getComponent(Class<T> type) {
        for (ComponentEntry entry : components) {
            if (type.isInstance(entry.component)) {
                return type.cast(entry.component);
            }
        }
        return null;
    }

    public synchronized <T> List<T> getAllComponents(Class<T> type) {
        ArrayList<T> list = new ArrayList<>();
        for (ComponentEntry entry : components) {
            if (type.isInstance(entry.component)) {
                list.add(type.cast(entry.component));
            }
        }
        return list;
    }

    public void start() {
        ComponentEntry[] plugs;
        synchronized (this) {
            running = true;
            plugs = getAllEntries();
        }

        for (ComponentEntry entry : plugs) {
            if (entry.component instanceof ActiveComponent ac) {
                ac.start();
            }
        }
    }

    public void printRegistry(PrintStream out) {
        ComponentEntry[] plugs = getAllEntries();
        out.printf("%-22s %s\n", "Component Name", "Component Class");
        out.println("----------------------------------------------");
        for (ComponentEntry entry : plugs) {
            out.printf("%-22s %s\n", entry.name, entry.component.getClass().getName());
        }
    }

    public record ComponentEntry(String name, Object component) {}
}
