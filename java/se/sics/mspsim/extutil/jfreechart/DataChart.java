package se.sics.mspsim.extutil.jfreechart;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JPanel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.ui.ManagedWindow;
import se.sics.mspsim.ui.WindowManager;
import se.sics.mspsim.util.ComponentRegistry;
import se.sics.mspsim.util.DataSource;
import se.sics.mspsim.util.ServiceComponent;
import se.sics.mspsim.util.StackMonitor;

public class DataChart extends JPanel implements ServiceComponent {
  private enum Mode {NONE, STACK, DUTY}
  private Mode mode = Mode.NONE;

  private final TimeSeriesCollection dataset;
  private ComponentRegistry registry;
  private ManagedWindow jw;
  private MSP430 cpu;
  private DataSourceSampler dss;
  private Status status = Status.STOPPED;
  private String name;

  public DataChart(String title, String yaxis) {
    DateAxis domain = new DateAxis("Time");
    NumberAxis range = new NumberAxis(yaxis);
    XYPlot xyplot = new XYPlot();
    xyplot.setDomainAxis(domain);
    xyplot.setRangeAxis(range);
 // xyplot.setBackgroundPaint(Color.black);
    dataset = new TimeSeriesCollection();
    xyplot.setDataset(dataset);

    DefaultXYItemRenderer renderer = new DefaultXYItemRenderer();
    renderer.setSeriesPaint(0, Color.red);
    renderer.setSeriesPaint(1, Color.green);
    renderer.setSeriesPaint(2, Color.blue);
    renderer.setSeriesPaint(3, Color.black);
    renderer.setSeriesShapesVisible(0, false);
    renderer.setSeriesShapesVisible(1, false);
    renderer.setSeriesShapesVisible(2, false);
    renderer.setSeriesShapesVisible(3, false);
    xyplot.setRenderer(renderer);

    domain.setAutoRange(true);
    domain.setLowerMargin(0.0);
    domain.setUpperMargin(0.0);

    domain.setTickLabelsVisible(true);
    range.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
    JFreeChart chart = new JFreeChart(title,
        JFreeChart.DEFAULT_TITLE_FONT, xyplot, true);
    ChartPanel chartPanel = new ChartPanel(chart);
    setLayout(new BorderLayout());
    setPreferredSize(new Dimension(400, 200));
    add(chartPanel, BorderLayout.CENTER);
  }

  public void addTimeSeries(TimeSeries ts) {
    dataset.addSeries(ts);
  }

  private ManagedWindow openFrame(String name) {
    WindowManager wm = (WindowManager) registry.getComponent("windowManager");
      ManagedWindow jw = wm.createWindow(name);
    jw.add(this);
    return jw;
  }



  public void setupStackFrame(MSP430 cpu) {
    mode = Mode.STACK;
    this.cpu = cpu;
  }

  private void openStackFrame() {
      if (jw == null) {
          jw = openFrame("Stack Monitor");
          StackMonitor sm = new StackMonitor(cpu);
          DataSourceSampler dss = new DataSourceSampler(cpu);
          TimeSeries ts = new TimeSeries("Max Stack");
          ts.setMaximumItemCount(200);
          addTimeSeries(ts);
          dss.addDataSource(sm.getMaxSource(), ts);
          ts = new TimeSeries("Stack");
          ts.setMaximumItemCount(200);
          addTimeSeries(ts);
          dss.addDataSource(sm.getSource(), ts);
      }
  }

  public DataSourceSampler setupChipFrame(MSP430 cpu) {
      mode = Mode.DUTY;
      this.cpu = cpu;
      jw = openFrame("Duty-Cycle Monitor");
      dss = new DataSourceSampler(cpu);
      dss.setInterval(50);
      return dss;
  }

  public void addDataSource(DataSourceSampler dss, String name, DataSource src) {
    TimeSeries ts = new TimeSeries(name);
    ts.setMaximumItemCount(200);
    addTimeSeries(ts);
    dss.addDataSource(src, ts);
  }

  @Override
  public Status getStatus() {
      return status;
  }

  @Override
  public void init(String name, ComponentRegistry registry) {
      this.registry = registry;
      this.name = name;
  }

  @Override
  public void start() {
      if (mode == Mode.STACK) {
          openStackFrame();
      } else {
          dss.start();
      }
      jw.setVisible(true);
      status = Status.STARTED;
  }

  @Override
  public void stop() {
      jw.setVisible(false);
      if (mode != Mode.STACK) {
          dss.stop();
      }
      status = Status.STOPPED;
  }

  @Override
  public String getName() {
      return name;
  }
}
