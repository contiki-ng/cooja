package se.sics.mspsim.extutil.jfreechart;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.Timer;

import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;

import se.sics.mspsim.core.MSP430Core;
import se.sics.mspsim.util.DataSource;

public class DataSourceSampler implements ActionListener {

  private final MSP430Core cpu;
  private int interval = 100;
  private final Timer timer;
  private final ArrayList<TimeSource> sources = new ArrayList<>();

  public DataSourceSampler(MSP430Core cpu) {
    this.cpu = cpu;
    timer = new Timer(interval, this);
    timer.start();
  }

  public void stop() {
      timer.stop();
  }

  public void start() {
      timer.start();
  }

  public TimeSource addDataSource(DataSource source, TimeSeries ts) {
    TimeSource times = new TimeSource(cpu, source, ts);
    sources.add(times);
    return times;
  }

  public void removeDataSource(TimeSource source) {
    sources.remove(source);
  }

  public void setInterval(int intMsek) {
    interval = intMsek;
    timer.setDelay(interval);
  }

  private void sampleAll() {
    if (!sources.isEmpty()) {
      TimeSource[] srcs = sources.toArray(new TimeSource[0]);
      for (TimeSource src : srcs) {
        if (src != null)
          src.update();
      }
    }

  }

  @Override
  public void actionPerformed(ActionEvent arg0) {
    sampleAll();
  }

  private static class TimeSource {

    private final MSP430Core cpu;
    private final DataSource dataSource;
    private final TimeSeries timeSeries;
    private long lastUpdate;

    TimeSource(MSP430Core cpu, DataSource ds, TimeSeries ts) {
      this.cpu = cpu;
      dataSource = ds;
      timeSeries = ts;
    }

    public void update() {
      long time = cpu.cycles / 2;
      if (time > lastUpdate) {
        lastUpdate = time;
        timeSeries.add(new Millisecond(new Date(time)), dataSource.getValue());
      }
    }
  }
}
