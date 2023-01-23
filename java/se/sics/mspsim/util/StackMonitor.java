package se.sics.mspsim.util;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.RegisterMonitor;
import se.sics.mspsim.core.Memory.AccessMode;
import se.sics.mspsim.profiler.SimpleProfiler;

public class StackMonitor {

  private int heapStartAddress;
  private int stackStartAddress;

  private int stackMin;
  private int stackMax;
  private int stack;
  private int profStackMax;

  private final DataSource maxDataSource = new DataSource() {
    @Override
    public int getValue() {
      int tmp = stackMax;
      stackMax = stack;
      return tmp;
    }
    @Override
    public double getDoubleValue() {
      return getValue();
    }
  };

  private final DataSource minDataSource = new DataSource() {
    @Override
    public int getValue() {
      int tmp = stackMin;
      stackMin = stack;
      return tmp;
    }
    @Override
    public double getDoubleValue() {
      return getValue();
    }
  };

  private final DataSource dataSource = new DataSource() {
    @Override
    public int getValue() {
      return stack;
    }
    @Override
    public double getDoubleValue() {
      return getValue();
    }
  };

  public StackMonitor(MSP430 cpu) {
    SimpleProfiler profiler = cpu.getRegistry().getComponent(SimpleProfiler.class);
    if (profiler != null) {
        profiler.setStackMonitor(this);
        System.out.println("Found simple profiler!!!: " + profiler);
    } else {
        System.out.println("Could not find any suitable profiler");
    }
    if (cpu.getDisAsm() != null) {
      MapTable mapTable = cpu.getDisAsm().getMap();
      if (mapTable != null) {
        this.heapStartAddress = mapTable.heapStartAddress;
        this.stackStartAddress = mapTable.stackStartAddress;
      }
    }
    cpu.addRegisterWriteMonitor(MSP430.SP, new RegisterMonitor.Adapter() {

        @Override
        public void notifyWriteBefore(int reg, int data, AccessMode mode) {
            // TODO add support for 20 bit addresses
            stack = ((stackStartAddress - data) + 0xffff) & 0xffff;
            if (stack > stackMax) {
              stackMax = stack;
            }
            if (stack < stackMin) {
              stackMin = stack;
            }
            if (stack > profStackMax) {
                profStackMax = stack;
            }
        }

    });
  }

  public int getStackStart() {
    return stackStartAddress;
  }

  public int getHeapStart() {
    return heapStartAddress;
  }


  public DataSource getMaxSource() {
    return maxDataSource;
  }

  public DataSource getMinSource() {
    return minDataSource;
  }

  public DataSource getSource() {
    return dataSource;
  }

  /* specialized profiler support for the stack */
  /* set current profiler Stack max to this value */
  public void setProfStackMax(int max) {
      profStackMax = max;
  }

  /* get profiler stack max */
  public int getProfStackMax() {
      return profStackMax;
  }

  public int getStack() {
      return stack;
  }

}
