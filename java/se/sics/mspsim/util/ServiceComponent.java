package se.sics.mspsim.util;

/* Service component that can be stopped and is not autostarted when
 * registered (unless it also implements ActiveComponent)
 */
public interface ServiceComponent {
  enum Status {STARTED, STOPPED, ERROR}

  String getName();
  Status getStatus();
  void init(String name, ComponentRegistry registry);
  void start();
  void stop();
}
