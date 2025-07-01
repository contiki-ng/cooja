package se.sics.mspsim.ui;

import java.awt.Component;
import javax.swing.JFrame;

public class JFrameWindowManager implements WindowManager {

    @Override
    public ManagedWindow createWindow(final String name) {
        return new ManagedWindow() {
            private final JFrame window = new JFrame(name);
            private boolean restored;

            @Override
            public void setSize(int width, int height) {
                window.setSize(width, height);
            }

            @Override
            public void setBounds(int x, int y, int width, int height) {
                window.setBounds(x, y, width, height);
            }

            @Override
            public void pack() {
                window.pack();
            }

            @Override
            public void add(Component component) {
                window.getContentPane().add(component);
                if (!restored) {
                    restored = true;
                    WindowUtils.restoreWindowBounds(name, window);
                }
                window.revalidate();
            }

            @Override
            public void removeAll() {
                window.getContentPane().removeAll();
            }

            @Override
            public boolean isVisible() {
                return window.isVisible();
            }

            @Override
            public void setVisible(boolean b) {
                if (b != window.isVisible()) {
                    if (b) {
                        WindowUtils.addSaveOnShutdown(name, window);
                    } else {
                        WindowUtils.saveWindowBounds(name, window);
                        WindowUtils.removeSaveOnShutdown(window);
                    }
                }
                window.setVisible(b);
            }

            @Override
            public String getTitle() {
                return window.getTitle();
            }

            @Override
            public void setTitle(String name1) {
                window.setTitle(name1);
            }
        };
    }

}
