package se.sics.mspsim.profiler;
import java.util.HashMap;
import se.sics.mspsim.util.MapEntry;

public class CallEntry {

    static class CallCounter implements Comparable<CallCounter> {
        public int count;

        @Override
        public int compareTo(CallCounter o) {
            return Integer.compare(count, o.count);
        }
    }


    int fromPC;
    MapEntry function;
    long cycles;
    long exclusiveCycles;
    int calls;
    int hide;
    int stackStart;
    int currentStackMax;

    final HashMap<MapEntry,CallCounter> callers;

    public CallEntry() {
      callers = new HashMap<>();
    }

    public MapEntry getFunction() {
        return function;
    }
  }
