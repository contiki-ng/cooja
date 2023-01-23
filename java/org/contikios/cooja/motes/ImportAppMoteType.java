/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
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
 *
 */

package org.contikios.cooja.motes;

import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

import org.contikios.cooja.Cooja;
import org.jdom2.Element;

import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.dialogs.ImportAppMoteDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Fredrik Osterlind
 */
@ClassDescription("Import Java mote")
@AbstractionLevelDescription("Application level")
public class ImportAppMoteType extends AbstractApplicationMoteType {
  private static final Logger logger = LoggerFactory.getLogger(ImportAppMoteType.class);

  private File moteClassPath;
  private String moteClassName;
  private Constructor<? extends AbstractApplicationMote> moteConstructor;

  public ImportAppMoteType() {
    super(true);
  }

  @Override
  public Collection<Element> getConfigXML(Simulation simulation) {
    Collection<Element> config = super.getConfigXML(simulation);

    if (moteClassPath != null) {
      Element element = new Element("motepath");
      File file = simulation.getCooja().createPortablePath(moteClassPath);
      element.setText(file.getPath().replaceAll("\\\\", "/"));
      config.add(element);
    }

    if (moteClassName != null) {
      Element element = new Element("moteclass");
      element.setText(moteClassName);
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable)
  throws MoteTypeCreationException {
    for (Element element : configXML) {
      String name = element.getName();
      if (name.equals("moteclass")) {
        moteClassName = element.getText();
      } else if (name.equals("motepath")) {
        moteClassPath = simulation.getCooja().restorePortablePath(new File(element.getText()));
      }
    }
    return super.setConfigXML(simulation, configXML, Cooja.isVisualized());
  }

  @Override
  public boolean configureAndInit(Container parentContainer,
      Simulation simulation, boolean visAvailable)
  throws MoteTypeCreationException {
    if (!super.configureAndInit(parentContainer, simulation, Cooja.isVisualized())) {
      return false;
    }

    if (Cooja.isVisualized()) {
      /* Select mote class file */
      ImportAppMoteDialog dialog = new ImportAppMoteDialog(simulation.getCooja(), this);
      if (!dialog.waitForUserResponse()) {
        return false;
      }
    }
    if (moteClassName == null) {
      throw new MoteTypeCreationException("Unknown mote class file");
    }
    if (moteClassPath == null) {
      // No class path. Check if path is available in the class name.
      if (moteClassName.indexOf('/') >= 0 || moteClassName.indexOf(File.separatorChar) >= 0) {
        File moteClassFile = new File(moteClassName);
        if (moteClassFile.canRead()) {
          try (var test = createTestLoader(simulation.getCooja(), moteClassFile)) {
            // Successfully found the class
            moteClassPath = test.getTestClassPath();
            moteClassName = test.getTestClassName();
          } catch (Exception | LinkageError e) {
            // Ignore
          }
        }
      }
    }
    ClassLoader parentLoader = getParentClassLoader(simulation.getCooja());
    try (var loader = moteClassPath == null
            ? null : URLClassLoader.newInstance(new URL[] { moteClassPath.toURI().toURL() }, parentLoader)) {
      var moteClass = (loader == null ? parentLoader : loader).loadClass(moteClassName).asSubclass(AbstractApplicationMote.class);
      moteConstructor = moteClass.getConstructor(MoteType.class, Simulation.class);
    } catch (Exception | LinkageError e) {
      throw new MoteTypeCreationException("Error when loading class from: "
              + (moteClassPath != null ? moteClassPath.getAbsolutePath() : "") + " " + moteClassName, e);
    }

    if (getDescription() == null || getDescription().isEmpty()) {
      setDescription("Imported Mote Type #" + moteClassName);
    }
    return true;
  }

  @Override
  public Mote generateMote(Simulation simulation) {
    try {
      return moteConstructor.newInstance(ImportAppMoteType.this, simulation);
    } catch (Exception e) {
      throw new RuntimeException("Error when generating mote", e);
    }
  }

  public void setMoteClassPath(File moteClassPath) {
    this.moteClassPath = moteClassPath;
  }

  public File getMoteClassPath() {
    return moteClassPath;
  }

  public void setMoteClassName(String moteClassName) {
    this.moteClassName = moteClassName;
  }

  public String getMoteClassName() {
    return moteClassName;
  }

  private static ClassLoader getParentClassLoader(Cooja cooja) {
    try {
      return cooja.getProjectClassLoader();
    } catch (Exception e) {
      logger.warn("Could not get Cooja classloader: " + e);
      return ClassLoader.getSystemClassLoader();
    }
  }

  public static TestLoader createTestLoader(Cooja cooja, File classFile) throws IOException {
    classFile = classFile.getCanonicalFile();
    ArrayList<URL> list = new ArrayList<>();
    for(File parent = classFile.getParentFile();
        parent != null;
        parent = parent.getParentFile()) {
      list.add(parent.toURI().toURL());
    }
    return new TestLoader(list.toArray(new URL[0]), getParentClassLoader(cooja), classFile);
  }

  public static class TestLoader extends URLClassLoader {
    private final File classFile;
    private File classPath;
    private final Class<?> testClass;

    private TestLoader(URL[] classpath, ClassLoader parentClassLoader, File classFile)
      throws IOException
    {
      super(classpath, parentClassLoader);
      this.classFile = classFile.getCanonicalFile();

      byte[] data = Files.readAllBytes(Path.of(classFile.toURI()));
      this.testClass = defineClass(null, data, 0, data.length);
    }

    public boolean isTestSubclass(Class<?> type) {
      return type.isAssignableFrom(testClass);
    }

    public String getTestClassName() {
      return testClass.getCanonicalName();
    }

    public File getTestClassPath() {
      if (classPath == null) {
        String name = testClass.getCanonicalName();
        int index = name.indexOf('.');
        classPath = classFile.getParentFile();
        while(index >= 0) {
          classPath = classPath.getParentFile();
          index = name.indexOf('.', index + 1);
        }
      }
      return classPath;
    }
  }

}
