public class IntervalStats {
    private final int[][] intervalFinishedTasks;
    private final int[][] intervalAdmittedTasks;
    private final int[][] intervalViolations;
    private final double[][] accumulatedResponseTime;

    public IntervalStats(int[][] intervalFinishedTasks, int[][] intervalAdmittedTasks, int[][] intervalViolations,
                         double[][] accumulatedResponseTime) {
        this.intervalFinishedTasks = intervalFinishedTasks;
        this.intervalAdmittedTasks = intervalAdmittedTasks;
        this.intervalViolations = intervalViolations;
        this.accumulatedResponseTime = accumulatedResponseTime;
    }

    public int[][] getIntervalFinishedTasks() {
        return intervalFinishedTasks;
    }

    public int[][] getIntervalAdmittedTasks() {
        return intervalAdmittedTasks;
    }

    public int[][] getIntervalViolations() {
        return intervalViolations;
    }

    public double[][] getAccumulatedResponseTime() {
        return accumulatedResponseTime;
    }
}
