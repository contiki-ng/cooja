package se.sics.mspsim.util;

/* Active components are always started when added to registry */
public interface ActiveComponent {
  void init(String name, ComponentRegistry registry);
  void start();
}
