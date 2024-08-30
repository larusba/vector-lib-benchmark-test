package javaannbench.display;

public class ProgressBar implements AutoCloseable {

  private final me.tongfei.progressbar.ProgressBar wrapped;

  private ProgressBar(me.tongfei.progressbar.ProgressBar wrapped) {
    this.wrapped = wrapped;
  }

  public static ProgressBar create(String description, int size) {
    var wrapped =
        me.tongfei.progressbar.ProgressBar.builder()
            .setTaskName(description)
            .setUpdateIntervalMillis(250)
            .setInitialMax(size)
            .build();
    return new ProgressBar(wrapped);
  }

  public void inc() {
    this.wrapped.step();
  }

  @Override
  public void close() {
    this.wrapped.close();
  }
}
