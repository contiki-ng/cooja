/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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
 */

package org.contikios.cooja;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A project configuration may hold the configuration for one or several project
 * directories as well as a general simulator configuration.
 * <p>
 * The configuration for a project directory may for example consist of which
 * plugins, interfaces and processes that the specific project directory supplies.
 * Each project directory configuration is read from the property file cooja.config, a
 * file which is required in each project directory.
 * <p>
 * Values can be fetched as String, Boolean, Integer, Double or String array.
 * <p>
 * Several configurations can be merged, together forming a final overall
 * configuration. The merge order of configurations matter - later
 * values will overwrite earlier. For example merging two configurations with
 * the key 'SOMEKEY' in the following order:
 * <p>
 * SOMEKEY = a b c
 * <p>
 * SOMEKEY = d e
 * <p>
 * will result in the final value "d e".
 * <p>
 * If a specific value should be extended instead of overwritten, the value must
 * start with a single space-surrounded '+'. For example, merging two
 * configurations with the key as above in the following order:
 * <p>
 * SOMEKEY = a b c
 * <p>
 * SOMEKEY = + d e
 * <p>
 * will result in the final value "a b c d e".
 * <p>
 * The simulator will hold a merged project configuration, depending on which
 * project directories are used. Additionally. each mote type may also have a
 * configuration of its own, that differs from the general simulator
 * configuration.
 * <p>
 * Often, but not necessarily, keys are named depending on which class is
 * associated with the information. For example, let's say a battery interface
 * wants to store its initial capacity (a double) using this approach. Data
 * stored in the external configuration file can look like the following:
 * org.contikios.cooja.interfaces.Battery.initial_capacity 54.123321
 * <p>
 * This value is then be read by: myMoteTypeConfig.getDoubleValue(Battery.class,
 * "initial_capacity");
 *
 * @author Fredrik Osterlind
 */
public class ProjectConfig {
  private static final Logger logger = LoggerFactory.getLogger(ProjectConfig.class);

  /**
   * User extension configuration filename.
   */
  public static final String PROJECT_CONFIG_FILENAME = "cooja.config";

  private final Properties myConfig;
  private final ArrayList<File> myProjectDirHistory = new ArrayList<>();

  /**
   * Creates new project configuration.
   *
   * @param useDefault
   *          If true the default configuration will be loaded
   */
  public ProjectConfig(boolean useDefault) {
    myConfig = new Properties();
    if (useDefault) {
      var settings = new Properties();
      settings.put("org.contikios.cooja.contikimote.interfaces.ContikiRadio.RADIO_TRANSMISSION_RATE_kbps", "250");
      appendConfig(myConfig, settings);
    }
  }

  /** Copy constructor. */
  public ProjectConfig(ProjectConfig other) {
    myConfig = new Properties(other.myConfig);
    myProjectDirHistory.addAll(other.myProjectDirHistory);
  }

  /**
   * Appends the given project directory's config file. This method also saves a
   * local history of which project directories has been loaded.
   *
   * @param projectDir
   *          Project directory
   * @return True if loaded OK
   * @throws FileNotFoundException
   *           If file was not found
   * @throws IOException
   *           Stream read error
   */
  public boolean appendProjectDir(File projectDir)
      throws FileNotFoundException, IOException {
    if (projectDir == null) {
      throw new FileNotFoundException("No project directory specified");
    }
    if (!projectDir.exists()) {
      throw new FileNotFoundException("Project directory does not exist: " + projectDir.getAbsolutePath());
    }
    
    File projectConfig = new File(projectDir.getPath(), PROJECT_CONFIG_FILENAME);
    if (!projectConfig.exists()) {
      throw new FileNotFoundException("Project config does not exist: " + projectConfig.getAbsolutePath());
    }
    myProjectDirHistory.add(projectDir);
    return appendConfigFile(projectConfig);
  }


  /**
   * Returns the project directory earlier appended to this configuration that
   * defined the given key. If the key is of an array format and the given array
   * element is non-null, then the project directory that added this element will be
   * returned instead. If no such project directory can be found null is returned
   * instead.
   *
   * @param callingClass
   *          Class which value belong to
   * @param key
   *          Key
   * @param arrayElement
   *          Value or array element
   * @return Project directory defining arguments or null
   */
  public File getUserProjectDefining(Class<?> callingClass, String key, String arrayElement) {
    // Check that key really exists in current config
    if (getStringValue(callingClass, key, null) == null) {
      return null;
    }

    // Check that element really exists, if any
    if (arrayElement != null) {
      String[] array = getStringArrayValue(callingClass, key);
      boolean foundValue = false;
      for (String element : array) {
        if (element.equals(arrayElement)) {
          foundValue = true;
          break;
        }
      }
      if (!foundValue) {
        return null;
      }
    }

    // Search in all project directory in reversed order
    try {
      ProjectConfig remadeConfig = new ProjectConfig(false);

      for (int i=myProjectDirHistory.size()-1; i >= 0; i--) {
        remadeConfig.appendProjectDir(myProjectDirHistory.get(i));

        if (arrayElement != null) {
          // Look for array
          String[] array = remadeConfig.getStringArrayValue(callingClass, key);
          for (String element : array) {
            if (element.equals(arrayElement)) {
              return myProjectDirHistory.get(i);
            }
          }
        } else {
          // Look for key
          if (remadeConfig.getStringValue(callingClass, key, null) != null) {
            return myProjectDirHistory.get(i);
          }
        }
      }

    } catch (Exception e) {
      logger.error("Exception when searching in project directory history: " + e);
      return null;
    }

    return null;
  }

  /**
   * Loads the given property file and appends it to the current configuration.
   * If a property already exists it will be overwritten, unless the new value
   * begins with a '+' in which case the old value will be extended.
   * <p>
   * WARNING! The project directory history will not be saved if this method is
   * called, instead the appendUserPlatform method should be used.
   *
   * @param propertyFile
   *          Property file to read
   * @return True if file was read ok, false otherwise
   * @throws FileNotFoundException
   *           If file was not found
   * @throws IOException
   *           Stream read error
   */
  public boolean appendConfigFile(File propertyFile)
      throws FileNotFoundException, IOException {
    if (!propertyFile.exists()) {
      logger.warn("Trying to import non-existent project configuration: " + propertyFile);
      return true;
    }

    Properties newProps = new Properties();
    try (var in = new FileInputStream(propertyFile)) {
      newProps.load(in);
    }
    return appendConfig(myConfig, newProps);
  }

  private static boolean appendConfig(Properties currentValues, Properties newProps) {
    for (var entry : newProps.entrySet()) {
      if (!(entry.getKey() instanceof String key && entry.getValue() instanceof String property)) continue;
      if (property.startsWith("+ ")) {
        if (currentValues.getProperty(key) != null) {
          currentValues.setProperty(key, currentValues.getProperty(key) + " "
              + property.substring(1).trim());
        } else {
          currentValues.setProperty(key, property.substring(1).trim());
        }
      } else {
        currentValues.setProperty(key, property);
      }
    }

    return true;
  }

  public boolean appendConfig(ProjectConfig config) {
    for (var entry : config.myConfig.entrySet()) {
      if (!(entry.getKey() instanceof String key && entry.getValue() instanceof String property)) continue;
      if (property.startsWith("+ ")) {
        if (myConfig.getProperty(key) != null) {
        	myConfig.setProperty(key, myConfig.getProperty(key) + " "
              + property.substring(1).trim());
        } else {
        	myConfig.setProperty(key, property.substring(1).trim());
        }
      } else {
      	myConfig.setProperty(key, property);
      }
  	}
  	return true;
  }

  /**
   * Returns the entry set of the configuration.
   * @return Entry set of the configuration
   */
  public Set<Map.Entry<Object, Object>> getEntrySet() {
    return myConfig.entrySet();
  }

  /**
   * Get string value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @param defaultValue
   *          Default value to return if id is not found
   * @return Value or defaultValue if id wasn't found
   */
  public String getStringValue(Class<?> callingClass, String id, String defaultValue) {
    return myConfig.getProperty(callingClass.getName() + "." + id, defaultValue);
  }

  /**
   * Returns value of given name.
   *
   * @param name
   *          Name
   * @return Value as string
   */
  public String getStringValue(String name) {
    return myConfig.getProperty(name);
  }

  /**
   * Get string value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @return Value or null if id wasn't found
   */
  public String getStringValue(Class<?> callingClass, String id) {
    return getStringValue(callingClass, id, null);
  }

  /**
   * Get string array value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @return Value or null if id wasn't found
   */
  public String[] getStringArrayValue(Class<?> callingClass, String id) {
    String stringVal = getStringValue(callingClass, id, null);
    if (stringVal == null) {
      return new String[0];
    }

    return getStringValue(callingClass, id, "").split(" ");
  }

  /**
   * Get string value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @return Value or null if id wasn't found
   */
  public String getValue(Class<?> callingClass, String id) {
    return getStringValue(callingClass, id);
  }

  /**
   * Get string array value with given id.
   *
   * @param id
   *          Id of value to return
   * @return Value or null if id wasn't found
   */
  public String[] getStringArrayValue(String id) {
    String stringVal = getStringValue(id);
    if (stringVal == null) {
      return new String[0];
    }

    return getStringValue(id).split(" ");
  }

  /**
   * Get integer value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @param defaultValue
   *          Default value to return if id is not found
   * @return Value or defaultValue if id wasn't found
   */
  public int getIntegerValue(Class<?> callingClass, String id, int defaultValue) {
    String str = getStringValue(callingClass, id);
    if (str == null) {
      return defaultValue;
    }

    return Integer.parseInt(str);
  }

  /**
   * Get integer value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @return Value or 0 if id wasn't found
   */
  public int getIntegerValue(Class<?> callingClass, String id) {
    return getIntegerValue(callingClass, id, 0);
  }

  /**
   * Get double value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @param defaultValue
   *          Default value to return if id is not found
   * @return Value or defaultValue if id wasn't found
   */
  public double getDoubleValue(Class<?> callingClass, String id, double defaultValue) {
    String str = getStringValue(callingClass, id);
    if (str == null) {
      return defaultValue;
    }

    return Double.parseDouble(str);
  }

  /**
   * Get double value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @return Value or 0.0 if id wasn't found
   */
  public double getDoubleValue(Class<?> callingClass, String id) {
    return getDoubleValue(callingClass, id, 0.0);
  }

  /**
   * Get boolean value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @param defaultValue
   *          Default value to return if id is not found
   * @return Value or defaultValue if id wasn't found
   */
  public boolean getBooleanValue(Class<?> callingClass, String id, boolean defaultValue) {
    String str = getStringValue(callingClass, id);
    if (str == null) {
      return defaultValue;
    }

    return Boolean.parseBoolean(str);
  }

  /**
   * Get boolean value with given id.
   *
   * @param callingClass
   *          Class which value belongs to
   * @param id
   *          Id of value to return
   * @return Value or false if id wasn't found
   */
  public boolean getBooleanValue(Class<?> callingClass, String id) {
    return getBooleanValue(callingClass, id, false);
  }
}
