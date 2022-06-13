/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;
import org.contikios.cooja.MoteType.MoteTypeCreationException;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.dialogs.MessageList;


/**
 * The purpose of corecomm's is communicating with a compiled Contiki system
 * using Java Native Interface (JNI). Each implemented class (named
 * Lib[number]), loads a shared library which belongs to one mote type. The
 * reason for this somewhat strange design is that once loaded, a native library
 * cannot be unloaded in Java (in the current versions available). Therefore if
 * we wish to load several libraries, the names and associated native functions
 * must have unique names. And those names are defined via the calling class in
 * JNI. For example, the corresponding function for a native tick method in
 * class Lib1 will be named Java_org_contikios_cooja_corecomm_Lib1_tick. When creating
 * a new mote type, the main Contiki source file is generated with function
 * names compatible with the next available corecomm class. This also implies
 * that even if a mote type is deleted, a new one cannot be created using the
 * same corecomm class without restarting the JVM and thus the entire
 * simulation.
 *
 * Each implemented CoreComm class needs read access to the following core
 * variables:
 * <ul>
 * <li>referenceVar
 * </ul>
 * and the following native functions:
 * <ul>
 * <li>tick()
 * <li>init()
 * <li>getReferenceAbsAddr()
 * <li>getMemory(int start, int length, byte[] mem)
 * <li>setMemory(int start, int length, byte[] mem)
 *
 * @author Fredrik Osterlind
 */
public abstract class CoreComm {

  // Static pointers to current libraries
  private final static Vector<CoreComm> coreComms = new Vector<>();

  private final static Vector<File> coreCommFiles = new Vector<>();

  private static int fileCounter = 1;

  /**
   * Has any library been loaded? Since libraries can't be unloaded the entire
   * simulator may have to be restarted.
   *
   * @return True if any library has been loaded this session
   */
  public static boolean hasLibraryBeenLoaded() {
    return coreComms.size() > 0;
  }

  /**
   * Has given library file already been loaded during this session? A loaded
   * library can be removed, but not unloaded during one session. And a new
   * library file, named the same as an earlier loaded and removed file, can't
   * be loaded either.
   *
   * @param libraryFile
   *          Library file
   * @return True if a library has already been loaded from the given file's
   *         filename
   */
  public static boolean hasLibraryFileBeenLoaded(File libraryFile) {
    for (File loadedFile : coreCommFiles) {
      if (loadedFile.getName().equals(libraryFile.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the class name of next free core communicator class. If null is
   * returned, no classes are available.
   *
   * @return Class name
   */
  public static String getAvailableClassName() {
    return "Lib" + fileCounter;
  }

  /**
   * Generates new source file by reading default source template and replacing
   * the class name field.
   *
   * @param tempDir Directory for temporary files
   * @param className
   *          Java class name (without extension)
   * @throws MoteTypeCreationException
   *           If error occurs
   */
  public static void generateLibSourceFile(Path tempDir, String className)
      throws MoteTypeCreationException {
    BufferedWriter sourceFileWriter = null;
    BufferedReader templateFileReader = null;
    String destFilename = null;

    try {
      Reader reader;
      String mainTemplate = Cooja
          .getExternalToolsSetting("CORECOMM_TEMPLATE_FILENAME");

      if (new File(mainTemplate).exists()) {
        reader = Files.newBufferedReader(Paths.get(mainTemplate), UTF_8);
      } else {
        InputStream input = CoreComm.class
            .getResourceAsStream('/' + mainTemplate);
        if (input == null) {
          throw new FileNotFoundException(mainTemplate + " not found");
        }
        reader = new InputStreamReader(input, UTF_8);
      }

      templateFileReader = new BufferedReader(reader);
      destFilename = className + ".java";

      File dir = tempDir.toFile();
      StringBuilder path = new StringBuilder(tempDir.toString());
      // Gradually do mkdir() since mkdirs() makes deleteOnExit() leave the
      // directory when Cooja quits.
      for (String p : new String[] {"/org", "/contikios", "/cooja", "/corecomm"}) {
        path.append(p);
        dir = new File(path.toString());
        dir.mkdir();
        dir.deleteOnExit();
      }

      Path dst = Path.of(dir + "/" + destFilename);
      sourceFileWriter = Files.newBufferedWriter(dst, UTF_8);
      dst.toFile().deleteOnExit();

      // Replace special fields in template
      String line;
      while ((line = templateFileReader.readLine()) != null) {
        line = line.replace("[CLASSNAME]", className);
        sourceFileWriter.write(line + "\n");
      }

      sourceFileWriter.close();
      templateFileReader.close();
    } catch (Exception e) {
      try {
        if (sourceFileWriter != null) {
          sourceFileWriter.close();
        }
        if (templateFileReader != null) {
          templateFileReader.close();
        }
      } catch (Exception e2) {
      }

      throw new MoteTypeCreationException(
          "Could not generate corecomm source file: " + className + ".java", e);
    }

    if (Files.exists(Path.of(tempDir + "/org/contikios/cooja/corecomm/" + destFilename))) {
      return;
    }

    throw new MoteTypeCreationException(
        "Could not generate corecomm source file: " + className + ".java");
  }

  /**
   * Compiles Java class.
   *
   * @param tempDir Directory for temporary files
   * @param className
   *          Java class name (without extension)
   * @throws MoteTypeCreationException
   *           If Java class compilation error occurs
   */
  public static void compileSourceFile(Path tempDir, String className)
      throws MoteTypeCreationException {
      /* Try to create a message list with support for GUI - will give not UI if headless */
    MessageList compilationOutput = MessageContainer.createMessageList(true);
    OutputStream compilationStandardStream = compilationOutput
        .getInputStream(MessageList.NORMAL);
    OutputStream compilationErrorStream = compilationOutput
        .getInputStream(MessageList.ERROR);

    try {
      int b;
      String[] cmd = new String[] {
          Cooja.getExternalToolsSetting("PATH_JAVAC"),
          "-cp",
          "." + File.pathSeparator +
          Cooja.getExternalToolsSetting("PATH_CONTIKI")
          + "/tools/cooja/dist/cooja.jar",
          tempDir + "/org/contikios/cooja/corecomm/" + className + ".java" };

      ProcessBuilder pb = new ProcessBuilder(cmd);
      Process p = pb.start();
      InputStream outputStream = p.getInputStream();
      InputStream errorStream = p.getErrorStream();
      while ((b = outputStream.read()) >= 0) {
        compilationStandardStream.write(b);
      }
      while ((b = errorStream.read()) >= 0) {
        compilationErrorStream.write(b);
      }
      if (p.waitFor() == 0) {
        File classFile = new File(tempDir + "/org/contikios/cooja/corecomm/" + className + ".class");
        classFile.deleteOnExit();
        return;
      }
    } catch (IOException | InterruptedException e) {
      var exception = new MoteTypeCreationException(
          "Could not compile corecomm source file: " + className + ".java", e);
      exception.setCompilationOutput(compilationOutput);
      throw exception;
    }

    MoteTypeCreationException exception = new MoteTypeCreationException(
        "Could not compile corecomm source file: " + className + ".java");
    exception.setCompilationOutput(compilationOutput);
    throw exception;
  }

  /**
   * Loads given Java class file from disk.
   *
   * @param tempDir Directory for temporary files
   * @param className Java class name
   * @return Loaded class
   * @throws MoteTypeCreationException If error occurs
   */
  public static Class<?> loadClassFile(Path tempDir, String className)
      throws MoteTypeCreationException {
    Class<?> loadedClass = null;
    try {
      ClassLoader urlClassLoader = new URLClassLoader(
          new URL[] { tempDir.toUri().toURL() },
          CoreComm.class.getClassLoader());
      loadedClass = urlClassLoader.loadClass("org.contikios.cooja.corecomm."
          + className);

    } catch (MalformedURLException | ClassNotFoundException e) {
      throw new MoteTypeCreationException(
          "Could not load corecomm class file: " + className + ".class", e);
    }
    if (loadedClass == null) {
      throw new MoteTypeCreationException(
          "Could not load corecomm class file: " + className + ".class");
    }

    return loadedClass;
  }

  /**
   * Create and return an instance of the core communicator identified by
   * className. This core communicator will load the native library libFile.
   *
   * @param tempDir Directory for temporary files
   * @param className
   *          Class name of core communicator
   * @param libFile
   *          Native library file
   * @return Core Communicator
   */
  public static CoreComm createCoreComm(Path tempDir, String className, File libFile)
      throws MoteTypeCreationException {
    generateLibSourceFile(tempDir, className);

    compileSourceFile(tempDir, className);

    Class newCoreCommClass = loadClassFile(tempDir, className);

    try {
      Constructor constr = newCoreCommClass
          .getConstructor(new Class[] { File.class });
      CoreComm newCoreComm = (CoreComm) constr
          .newInstance(new Object[] { libFile });

      coreComms.add(newCoreComm);
      coreCommFiles.add(libFile);
      fileCounter++;

      return newCoreComm;
    } catch (Exception e) {
      throw new MoteTypeCreationException(
          "Error when creating corecomm instance: " + className, e);
    }
  }

  /**
   * Ticks a mote once. This should not be used directly, but instead via
   * {@link ContikiMoteType#tick()}.
   */
  public abstract void tick();

  /**
   * Initializes a mote by running a startup script in the core. (Should only be
   * run once, at the same time as the library is loaded)
   */
  protected abstract void init();

  /**
   * Sets the relative memory address of the reference variable.
   * Is used by Contiki to map between absolute and relative memory addresses.
   *
   * @param addr Relative address
   */
  public abstract void setReferenceAddress(long addr);

  /**
   * Fills an byte array with memory segment identified by start and length.
   *
   * @param relAddr Relative memory start address
   * @param length Length of segment
   * @param mem Array to fill with memory segment
   */
  public abstract void getMemory(long relAddr, int length, byte[] mem);

  /**
   * Overwrites a memory segment identified by start and length.
   *
   * @param relAddr Relative memory start address
   * @param length Length of segment
   * @param mem New memory segment data
   */
  public abstract void setMemory(long relAddr, int length, byte[] mem);

}
