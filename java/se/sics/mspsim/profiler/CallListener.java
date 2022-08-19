package se.sics.mspsim.profiler;
import se.sics.mspsim.core.Profiler;

public interface CallListener {

  void functionCall(Profiler source,  CallEntry entry);

  void functionReturn(Profiler source, CallEntry entry);

}