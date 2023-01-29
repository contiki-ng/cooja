/*
 * Copyright (c) 2023, RISE Research Institutes of Sweden AB.
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
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.dialogs;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import org.contikios.cooja.Cooja;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JFileChooser for selecting resource files within a JAR file.
 */
public class JResourceChooser {
  private static final Logger logger = LoggerFactory.getLogger(JResourceChooser.class);

  /**
   * Allocate a new file chooser for resources.
   * @param owner Resource owning class.
   * @param realRoot Relative path within the JAR for the directory
   * @return Name of the file selected, or null.
   */
  public static String newResourceChooser(Class<?> owner, String realRoot) {
    FileSystem jarFileSystem = null;
    try {
      var xuri = owner.getResource(realRoot).toURI();
      if("jar".equals(xuri.getScheme())){
        for (var provider: FileSystemProvider.installedProviders()) {
          if (provider.getScheme().equalsIgnoreCase("jar")) {
            try {
              provider.getFileSystem(xuri);
            } catch (FileSystemNotFoundException e) {
              jarFileSystem = provider.newFileSystem(xuri, Collections.emptyMap());
            }
          }
        }
      }
    } catch (Exception e) {
      logger.error("Could not create resource root", e);
      return null;
    }

    // Remove the create directory button since that will fail inside jar.
    UIManager.put("FileChooser.readOnly", Boolean.TRUE);
    String file = null;
    var root = new File("/");
    try (var r = jarFileSystem) {
      var chooser = new JFileChooser(root, new DirectoryRestrictedFileSystemView(owner, root, realRoot));
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setFileFilter(new FileNameExtensionFilter("JavaScript", "js"));
      if (chooser.showOpenDialog(Cooja.getTopParentContainer()) == JFileChooser.APPROVE_OPTION) {
        file = chooser.getSelectedFile().getName();
      }
    } catch (IOException e) {
      logger.error("Failed to close JAR file system", e);
    } finally {
      UIManager.put("FileChooser.readOnly", Boolean.FALSE);
    }
    return file;
  }

  private static class DirectoryRestrictedFileSystemView extends FileSystemView {
    private final File root;
    private final String jarRoot;
    private final Class<?> owner;

    DirectoryRestrictedFileSystemView(Class<?> owner, File rootDirectory, String realRoot) {
      this.owner = owner;
      root = rootDirectory;
      jarRoot = realRoot;
    }

    @Override
    public File getHomeDirectory() {
      return root;
    }

    @Override
    public File createNewFolder(File containingDir) throws IOException {
      throw new IOException("Unable to create directory");
    }

    @Override
    public File[] getRoots() {
      return new File[]{root};
    }

    @Override
    public File[] getFiles(File dir, boolean useFileHiding) {
      try {
        // Prepend the jar root path to every file listing.
        var f = owner.getResource(jarRoot + dir).toURI();
        try (var paths = Files.list(Path.of(f))) {
          return paths.map(p -> new File(p.getFileName().toString())).toList().toArray(new File[0]);
        }
      } catch (URISyntaxException | IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
