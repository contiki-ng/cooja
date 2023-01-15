/*
 * Copyright (c) 2010, Swedish Institute of Computer Science. All rights
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * COOJA Project.
 *
 * @author Fredrik Osterlind
 * @author Moritz Strübe
 */
public class COOJAProject {
	private static final Logger logger = LoggerFactory.getLogger(COOJAProject.class);

	public static File[] searchProjects(File folder, int depth){
		if(depth == 0){
			return null;
		}
		depth--;
		ArrayList<File> dirs = new ArrayList<>();
		
		if(!folder.isDirectory()){
			logger.warn("Project directories: " + folder.getPath() + " is not a folder" );
			return null;
		}
		File[] files = folder.listFiles();
		for(File subf : files){
			if(subf.getName().charAt(0) == '.') continue;
			if(subf.isDirectory()){
				File[] newf = searchProjects(subf, depth);
				if(newf != null){
					Collections.addAll(dirs, newf);
				}
			}
			if(subf.getName().equals(ProjectConfig.PROJECT_CONFIG_FILENAME)){
				try{
					dirs.add(folder);
				} catch(Exception e){
					logger.error("Something odd happened", e);
				}
			}
		}
		return dirs.toArray(new File[0]);
		
	}


  public final File dir;
  public final File configFile;
  public final ProjectConfig config;

  public COOJAProject(File dir) throws IOException {
    this.dir = dir;
    configFile = new File(dir.getPath(), ProjectConfig.PROJECT_CONFIG_FILENAME);
    config = new ProjectConfig(false);
    config.appendConfigFile(configFile);
  }

	public boolean directoryExists() {
		return dir.exists();
	}
	public boolean configExists() {
		return configFile.exists();
	}
	public boolean configRead() {
		return config != null;
	}
	public boolean hasError() {
		if (!directoryExists() || !configExists() || !configRead()) {
			return true;
		}
		if (getConfigJARs() != null) {
			String[] jars = getConfigJARs();
			for (String jar: jars) {
				File jarFile = Cooja.findJarFile(dir, jar);
				if (jarFile == null || !jarFile.exists()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return Description or null
	 */
	public String getDescription() {
		return config.getStringValue("DESCRIPTION");
	}

	private String[] getStringArray(String key) {
		String[] arr = config.getStringArrayValue(key);
		if (arr == null || arr.length == 0) {
			return null;
		}
		if (arr[0].equals("+")) {
			/* strip + */
			return Arrays.copyOfRange(arr, 1, arr.length);
		}
		return arr;
	}
	public String[] getConfigJARs() {
		return getStringArray("org.contikios.cooja.Cooja.JARFILES");
	}

	@Override
	public String toString() {
		if (getDescription() != null) {
			return getDescription();
		}
		return dir.toString();
	}
}
